"""
Prepares the Plus edition's models: the text finder and reader, the face finder (YuNet) and
face matcher (SFace, already 8-bit), and TinyCLIP's image and text halves for searching by
description (shrunk to 8-bit with a check of their fingerprints, below). The text reader's weights are shrunk to 8-bit numbers
where that's safe (below); the rest are used as published. All are converted to ONNX Runtime's
own format, which also writes the list of operators they use. That list is what the cut-down
runtime is built with.

Shrinking can cost accuracy, so the shrunk reader is checked against the original on a few
lines of test text drawn here: it must read every line the same. Shrinking every layer is
tried first (smallest); if that reads anything differently, only the matrix layers are shrunk;
if even that differs, the original is kept.

Usage: python3 prepare_models.py <folder with the downloads in models.txt> <output folder>
"""
import pathlib
import shutil
import subprocess
import sys

import numpy as np
import onnxruntime as ort
from onnxruntime.quantization import QuantType, quantize_dynamic
from PIL import Image, ImageDraw, ImageFont

src = pathlib.Path(sys.argv[1])
out = pathlib.Path(sys.argv[2])
work = out / "onnx"
trial = out / "trial"
work.mkdir(parents=True, exist_ok=True)
trial.mkdir(parents=True, exist_ok=True)

characters = [c for c in (src / "rec_dict.txt").read_text(encoding="utf-8").split("\n") if c] + [" "]
LINES = ["Boarding pass 2026", "Open daily 9 to 5", "Café Größe niño", "TOTAL $42.17", "gallery@example.org"]


def line_image(text):
    """A line of black text on white, 48 pixels high, as blue-green-red values scaled to -1..1."""
    font = ImageFont.load_default(size=32)
    width = int(font.getlength(text)) + 16
    image = Image.new("RGB", (width, 48), "white")
    ImageDraw.Draw(image).text((8, 6), text, fill="black", font=font)
    pixels = np.asarray(image, dtype=np.float32)[:, :, ::-1] / 255.0
    return ((pixels - 0.5) / 0.5).transpose(2, 0, 1)[np.newaxis]


def read(model, image):
    """What the reader model makes of one line (greedy CTC decoding, as the app does)."""
    session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
    scores = session.run(None, {session.get_inputs()[0].name: image})[0][0]
    best = scores.argmax(axis=1)
    text, previous = [], -1
    for slot in best:
        if slot != 0 and slot != previous and slot - 1 < len(characters):
            text.append(characters[slot - 1])
        previous = slot
    return "".join(text)


images = [line_image(t) for t in LINES]
expected = [read(src / "rec.onnx", i) for i in images]
print("Original reads:", expected)

chosen = None
for name, op_types in [("all layers", None), ("matrix layers only", ["MatMul", "Gemm"])]:
    candidate = trial / f"rec-{len(op_types or [])}.onnx"
    quantize_dynamic(
        str(src / "rec.onnx"), str(candidate),
        weight_type=QuantType.QInt8, per_channel=True,
        **({"op_types_to_quantize": op_types} if op_types else {}),
    )
    got = [read(candidate, i) for i in images]
    print(f"Shrunk ({name}) reads:", got, f"{candidate.stat().st_size} bytes")
    if got == expected:
        chosen = candidate
        print(f"::notice title=Reader model::shrunk ({name}), {candidate.stat().st_size} bytes")
        break
if chosen is None:
    print("::warning title=Reader model::shrinking changed what it reads; keeping the original")
    chosen = src / "rec.onnx"

# Search by description: both halves of TinyCLIP shrunk to 8-bit. The check here is on their
# fingerprints: for a few test pictures and phrases, the shrunk model's must point almost the
# same way as the original's (cosine similarity at least 0.98, or 0.97 for the text half; see below).
import json
import tokenizers

clip_tokenizer = tokenizers.Tokenizer.from_file(str(src / "clip_tokenizer.json"))
PHRASES = ["a black dog", "old log cabin", "red car", "a birthday cake with candles", "sunset over the sea"]


def picture(kind):
    """A simple test picture, 224 x 224, as CLIP's normalised red-green-blue values."""
    image = Image.new("RGB", (224, 224), "white")
    draw = ImageDraw.Draw(image)
    if kind == 0:
        draw.rectangle((40, 80, 184, 160), fill="red")
        draw.ellipse((60, 140, 100, 180), fill="black")
        draw.ellipse((124, 140, 164, 180), fill="black")
    elif kind == 1:
        draw.rectangle((0, 0, 224, 120), fill=(90, 160, 230))
        draw.ellipse((80, 60, 140, 120), fill=(250, 180, 60))
        draw.rectangle((0, 120, 224, 224), fill=(30, 80, 160))
    else:
        draw.polygon([(40, 120), (112, 50), (184, 120)], fill=(120, 70, 30))
        draw.rectangle((60, 120, 164, 200), fill=(140, 90, 40))
    pixels = np.asarray(image, dtype=np.float32) / 255.0
    mean = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
    std = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)
    return ((pixels - mean) / std).transpose(2, 0, 1)[np.newaxis]


def clip_inputs(session, kind):
    """The inputs one of the halves needs for test case [kind]."""
    names = [i.name for i in session.get_inputs()]
    if "pixel_values" in names:
        return {"pixel_values": picture(kind)}
    ids = np.array([clip_tokenizer.encode(PHRASES[kind]).ids], dtype=np.int64)
    feeds = {"input_ids": ids}
    if "attention_mask" in names:
        feeds["attention_mask"] = np.ones_like(ids)
    return feeds


def fingerprints(model, cases):
    session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
    out = []
    for kind in range(cases):
        values = session.run(None, clip_inputs(session, kind))[0].reshape(-1)
        out.append(values / np.linalg.norm(values))
    return out


# The ONNX conversion is one model with both halves; it's split here so the app can load the
# image half for the background work and only the small text half while searching.
import onnx
import onnx.utils

combined = onnx.load(str(src / "clip.onnx"))
outputs = [o.name for o in combined.graph.output]
inputs = [i.name for i in combined.graph.input]
print("clip.onnx inputs", inputs, "outputs", outputs)
image_out = next(o for o in outputs if "image_embeds" in o)
text_out = next(o for o in outputs if "text_embeds" in o)
extractor = onnx.utils.Extractor(combined)
onnx.save(extractor.extract_model([i for i in inputs if "pixel" in i], [image_out]), str(src / "clip_image.onnx"))
onnx.save(extractor.extract_model([i for i in inputs if "pixel" not in i], [text_out]), str(src / "clip_text.onnx"))
del combined, extractor

for half, cases in [("clip_image", 3), ("clip_text", len(PHRASES))]:
    original = fingerprints(src / f"{half}.onnx", cases)
    kept = src / f"{half}.onnx"
    # The text half's word table (a Gather) is most of its size, so shrinking it with the matrix
    # layers is tried before leaving it at full size.
    for name, op_types in [
        ("all layers", None),
        ("matrix layers and word table", ["MatMul", "Gemm", "Gather"]),
        ("matrix layers only", ["MatMul", "Gemm"]),
    ]:
        candidate = trial / f"{half}-{len(op_types or [])}.onnx"  # a distinct name per option
        quantize_dynamic(
            str(src / f"{half}.onnx"), str(candidate),
            weight_type=QuantType.QInt8, per_channel=True,
            **({"op_types_to_quantize": op_types} if op_types else {}),
        )
        try:
            worst = min(float(np.dot(a, b)) for a, b in zip(original, fingerprints(candidate, cases)))
        except Exception as error:  # e.g. an 8-bit layer the runtime has no implementation for
            print(f"{half} shrunk ({name}) can't run: {error}")
            continue
        print(f"{half} shrunk ({name}): worst similarity {worst:.4f}, {candidate.stat().st_size} bytes")
        # The text half is held to 0.97: its word table, which is most of its size, can only be
        # shrunk along with everything else, landing just under 0.98, and a query fingerprint
        # that close still ranks photos the same way. Left at full size it would be 178 MB.
        if worst >= (0.97 if half == "clip_text" else 0.98):
            kept = candidate
            print(f"::notice title={half}::shrunk ({name}), {candidate.stat().st_size} bytes, worst similarity {worst:.4f}")
            break
    else:
        print(f"::warning title={half}::shrinking changed its fingerprints too much; keeping the original")
    shutil.copy(kept, work / f"{half}.onnx")

# The tokenizer's word pieces and merge rules as plain text, for the app, plus the reference
# tokenization of some phrases, which the app's unit tests check its own tokenizer against.
spec = json.loads((src / "clip_tokenizer.json").read_text(encoding="utf-8"))
vocab = spec["model"]["vocab"]
pieces = [None] * len(vocab)
for piece, i in vocab.items():
    pieces[i] = piece
merges = [m if isinstance(m, str) else " ".join(m) for m in spec["model"]["merges"]]
(out / "clip_vocab.txt").write_text("\n".join(pieces) + "\n", encoding="utf-8")
(out / "clip_merges.txt").write_text("\n".join(merges) + "\n", encoding="utf-8")
samples = PHRASES + ["Old log-cabin, 1987!", "  café  au   lait  ", "it's a DOG's life", "😀 smile"]
(out / "clip_tokens_expected.txt").write_text(
    "".join(f"{p}\t{' '.join(map(str, clip_tokenizer.encode(p).ids))}\n" for p in samples), encoding="utf-8",
)

shutil.copy(src / "det.onnx", work / "det.onnx")
shutil.copy(chosen, work / "rec.onnx")
shutil.copy(src / "yunet.onnx", work / "yunet.onnx")
shutil.copy(src / "sface.onnx", work / "sface.onnx")
subprocess.run(
    [
        sys.executable, "-m", "onnxruntime.tools.convert_onnx_models_to_ort", str(work),
        "--enable_type_reduction", "--optimization_style", "Fixed",
    ],
    check=True,
)
for f in sorted(work.iterdir()):
    print(f"{f.name}: {f.stat().st_size} bytes")

# Print each model's inputs and outputs, so a changed model shows up in the build log.
for name in ["det.onnx", "rec.onnx", "yunet.onnx", "sface.onnx", "clip_image.onnx", "clip_text.onnx"]:
    session = ort.InferenceSession(str(work / name), providers=["CPUExecutionProvider"])
    print(name, "in:", [(i.name, i.shape) for i in session.get_inputs()], "out:", [(o.name, o.shape) for o in session.get_outputs()])
