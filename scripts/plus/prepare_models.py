"""
Prepares the Plus edition's models: the text finder and reader, and the face finder (YuNet)
and face matcher (SFace, already 8-bit). The text reader's weights are shrunk to 8-bit numbers
where that's safe (below); the rest are used as published. All are converted to ONNX Runtime's
own format, which also writes the list of operators they use. That list is what the cut-down
runtime is built with.

Shrinking can cost accuracy, so the shrunk reader is checked against the original on a few
lines of test text drawn here: it must read every line the same. Shrinking every layer is
tried first (smallest); if that reads anything differently, only the matrix layers are shrunk;
if even that differs, the original is kept.

Usage: python3 prepare_models.py <folder with det.onnx, rec.onnx, rec_dict.txt, yunet.onnx, sface.onnx> <output folder>
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

shutil.copy(src / "det.onnx", work / "det.onnx")
shutil.copy(chosen, work / "rec.onnx")
shutil.copy(src / "yunet.onnx", work / "yunet.onnx")
shutil.copy(src / "sface.onnx", work / "sface.onnx")
subprocess.run(
    [
        sys.executable, "-m", "onnxruntime.tools.convert_onnx_models_to_ort", str(work),
        # Runtime style: the models keep the information the XNNPACK engine needs to rearrange
        # them for itself when they're loaded (it only exists in the "extended" runtime build).
        "--enable_type_reduction", "--optimization_style", "Runtime",
    ],
    check=True,
)
for f in sorted(work.iterdir()):
    print(f"{f.name}: {f.stat().st_size} bytes")

# Print each model's inputs and outputs, so a changed model shows up in the build log.
for name in ["det.onnx", "rec.onnx", "yunet.onnx", "sface.onnx"]:
    session = ort.InferenceSession(str(work / name), providers=["CPUExecutionProvider"])
    print(name, "in:", [(i.name, i.shape) for i in session.get_inputs()], "out:", [(o.name, o.shape) for o in session.get_outputs()])
