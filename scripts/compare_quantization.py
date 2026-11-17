"""
Compares ways of shrinking Gallery Plus's models to 8-bit numbers, to decide whether a
different way is worth switching to. Nothing here changes what the app ships.

For each model it tries:
  - original: the model as published (32-bit numbers; SFace is left out, it's published 8-bit)
  - dynamic: what the build does today for the reader and TinyCLIP. Only the weights are stored
    8-bit; the numbers flowing between layers are rounded to 8-bit afresh on every run.
  - static: weights and the numbers between layers are both 8-bit, with the rounding worked out
    once beforehand from sample photos ("calibration"). Phone processors have fast instructions
    for this, so it's usually quicker. Tried on every layer, and on only the heavy layers
    (convolutions and matrix multiplications), which is gentler on accuracy.

and measures, on this machine (an Arm processor like a phone's): file size, time per photo
with 1 and 4 cores, and accuracy against the original:
  - text finder: overlap of the "text here" areas it marks
  - text reader: lines read exactly as the original reads them
  - face finder: overlap of where it finds faces
  - TinyCLIP: similarity of fingerprints, and whether each description still finds its photo

The sample photos are OpenCV's own sample images, fetched by the workflow; half of them are
used for calibration and the other half for measuring, so a model isn't tested on what it was
tuned with.

Usage: python3 compare_quantization.py <folder with the downloads in models.txt> <photo folder> <work folder>
"""
import json
import os
import pathlib
import statistics
import sys
import time

import numpy as np
import onnx
import onnx.utils
import onnxruntime as ort
import tokenizers
from onnxruntime.quantization import (
    CalibrationDataReader, CalibrationMethod, QuantFormat, QuantType, quantize_dynamic, quantize_static,
)
from onnxruntime.quantization.shape_inference import quant_pre_process
from PIL import Image, ImageDraw, ImageFont

src = pathlib.Path(sys.argv[1])
photo_dir = pathlib.Path(sys.argv[2])
work = pathlib.Path(sys.argv[3])
work.mkdir(parents=True, exist_ok=True)

photos = sorted(p for p in photo_dir.iterdir() if p.suffix.lower() in (".jpg", ".png"))
calibration_photos = photos[0::2]
test_photos = photos[1::2]
print(f"{len(photos)} photos: {len(calibration_photos)} for calibration, {len(test_photos)} for testing")


# --- Preparing inputs the way the app does -------------------------------------------------

def load(path):
    return Image.open(path).convert("RGB")


def det_input(image, side=960):
    """The text finder's input: longest side [side], both sides multiples of 32, blue first."""
    scale = side / max(image.size)
    w = max(32, round(image.width * scale / 32) * 32)
    h = max(32, round(image.height * scale / 32) * 32)
    pixels = np.asarray(image.resize((w, h), Image.BILINEAR), dtype=np.float32)[:, :, ::-1] / 255.0
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
    return ((pixels - mean) / std).transpose(2, 0, 1)[np.newaxis].astype(np.float32)


characters = [c for c in (src / "rec_dict.txt").read_text(encoding="utf-8").split("\n") if c] + [" "]


def line_input(text, size=32):
    """A line of text drawn 48 pixels high, blue first, scaled to -1..1, as the reader takes it."""
    font = ImageFont.load_default(size=size)
    width = int(font.getlength(text)) + 16
    image = Image.new("RGB", (width, 48), "white")
    ImageDraw.Draw(image).text((8, (48 - size) // 2 - 2), text, fill="black", font=font)
    pixels = np.asarray(image, dtype=np.float32)[:, :, ::-1] / 255.0
    return ((pixels - 0.5) / 0.5).transpose(2, 0, 1)[np.newaxis].astype(np.float32)


CALIBRATION_LINES = [
    "Welcome to the museum", "Exit", "Total 18.99", "Monday to Friday", "Parking only",
    "Grand Opening Sale", "Happy Birthday Emma", "Platform 4", "Fresh bread daily", "No entry",
    "Room 214", "www.example.com", "Call 555-0142", "Best before 12/2027", "Merry Christmas",
    "Ingredients: flour, sugar", "Gate B12", "Speed limit 35", "Open 24 hours", "Thank you!",
]
TEST_LINES = [
    "Boarding pass 2026", "Open daily 9 to 5", "Café Größe niño", "TOTAL $42.17", "gallery@example.org",
    "Main Street", "Library hours", "Do not disturb", "Sale ends Sunday", "Flight AA 1234",
    "Coffee & Tea", "Wi-Fi password: sunshine", "Emergency exit only", "Lot 7", "Season's greetings",
]
LINE_SIZES = [24, 30, 36]


def yunet_input(image):
    """YuNet's input: the photo shrunk to fit 640 × 640, the rest black, blue first, 0–255."""
    scale = 640 / max(image.size)
    resized = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.BILINEAR)
    canvas = Image.new("RGB", (640, 640), "black")
    canvas.paste(resized, (0, 0))
    return np.asarray(canvas, dtype=np.float32)[:, :, ::-1].transpose(2, 0, 1)[np.newaxis].copy()


def clip_image_input(image):
    """CLIP's input: shortest side 224, centre square, red first, CLIP's mean and spread."""
    scale = 224 / min(image.size)
    resized = image.resize((max(224, round(image.width * scale)), max(224, round(image.height * scale))), Image.BICUBIC)
    left = (resized.width - 224) // 2
    top = (resized.height - 224) // 2
    pixels = np.asarray(resized.crop((left, top, left + 224, top + 224)), dtype=np.float32) / 255.0
    mean = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
    std = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)
    return ((pixels - mean) / std).transpose(2, 0, 1)[np.newaxis].astype(np.float32)


clip_tokenizer = tokenizers.Tokenizer.from_file(str(src / "clip_tokenizer.json"))


def text_feeds(names, phrase):
    ids = np.array([clip_tokenizer.encode(phrase).ids], dtype=np.int64)
    feeds = {"input_ids": ids}
    if "attention_mask" in names:
        feeds["attention_mask"] = np.ones_like(ids)
    return feeds


# What each of OpenCV's sample photos shows, for checking descriptions still find their photo.
CAPTIONS = {
    "lena": "a woman wearing a hat", "messi5": "a football player on a pitch", "baboon": "a baboon",
    "fruits": "a bowl of fruit", "building": "a tall building", "home": "a living room",
    "starry_night": "a painting of a starry night", "butterfly": "a butterfly", "apple": "an apple",
    "orange": "an orange", "aero1": "an aerial photo", "aero3": "an aerial photo", "HappyFish": "a fish",
    "LinuxLogo": "a penguin logo", "WindowsLogo": "a windows logo", "imageTextN": "printed text",
    "imageTextR": "printed text", "sudoku": "a sudoku puzzle", "chicky_512": "a cartoon chick",
    "licenseplate_motion": "a car license plate", "smarties": "colorful candies", "text_motion": "blurry text",
    "board": "a chessboard", "notes": "handwritten notes", "graf1": "graffiti on a wall",
    "box_in_scene": "a box on a table", "basketball1": "a basketball game", "left01": "a chessboard",
    "right01": "a chessboard", "pic1": "shapes", "pic3": "shapes", "pic5": "shapes",
}
CLIP_CALIBRATION_PHRASES = [
    "a black dog", "old log cabin", "red car", "a birthday cake with candles", "sunset over the sea",
    "a cat on a sofa", "snowy mountains", "a plate of pasta", "children playing in a park", "a city at night",
    "a bride and groom", "a receipt", "a screenshot of a phone", "flowers in a garden", "a beach with palm trees",
    "a selfie", "a bicycle", "a whiteboard with notes", "a dog on the beach", "fireworks",
]


# --- Calibration data ----------------------------------------------------------------------

class Feeds(CalibrationDataReader):
    def __init__(self, feeds):
        self.feeds = iter(feeds)

    def get_next(self):
        return next(self.feeds, None)


def calibration_feeds(model, name):
    inputs = [i.name for i in ort.InferenceSession(str(model), providers=["CPUExecutionProvider"]).get_inputs()]
    if name == "det":
        return [{inputs[0]: det_input(load(p), side)} for p in calibration_photos for side in (480, 960)]
    if name == "rec":
        return [{inputs[0]: line_input(t, s)} for t in CALIBRATION_LINES for s in LINE_SIZES]
    if name == "yunet":
        return [{inputs[0]: yunet_input(load(p))} for p in calibration_photos]
    if name == "clip_image":
        return [{inputs[0]: clip_image_input(load(p))} for p in calibration_photos]
    if name == "clip_text":
        return [text_feeds(inputs, t) for t in CLIP_CALIBRATION_PHRASES]
    raise ValueError(name)


# --- Making the variants -------------------------------------------------------------------

HEAVY = ["Conv", "MatMul", "Gemm"]


def variants(name, original):
    made = {"original": original}
    try:
        out = work / f"{name}-dynamic.onnx"
        quantize_dynamic(str(original), str(out), weight_type=QuantType.QInt8, per_channel=True)
        made["dynamic"] = out
    except Exception as error:
        print(f"{name} dynamic: couldn't make it: {error}")
    prepared = work / f"{name}-prepared.onnx"
    try:
        quant_pre_process(str(original), str(prepared), skip_symbolic_shape=name.startswith("clip"))
    except Exception as error:
        print(f"{name}: preprocessing failed ({error}); quantizing the original as is")
        prepared = original
    for label, op_types in [("static, every layer", None), ("static, heavy layers", HEAVY + (["Gather"] if name == "clip_text" else []))]:
        out = work / f"{name}-{label.replace(', ', '-').replace(' ', '_')}.onnx"
        try:
            quantize_static(
                str(prepared), str(out), Feeds(calibration_feeds(original, name)),
                quant_format=QuantFormat.QDQ, per_channel=True,
                weight_type=QuantType.QInt8, activation_type=QuantType.QUInt8,
                calibrate_method=CalibrationMethod.MinMax,
                **({"op_types_to_quantize": op_types} if op_types else {}),
            )
            made[label] = out
        except Exception as error:
            print(f"{name} {label}: couldn't make it: {error}")
    return made


# --- Measuring -----------------------------------------------------------------------------

def session(model, threads=1):
    options = ort.SessionOptions()
    options.intra_op_num_threads = threads
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    return ort.InferenceSession(str(model), options, providers=["CPUExecutionProvider"])


def timing(model, feeds, threads):
    s = session(model, threads)
    for _ in range(3):
        s.run(None, feeds)
    times = []
    for _ in range(15):
        start = time.perf_counter()
        s.run(None, feeds)
        times.append((time.perf_counter() - start) * 1000)
    return statistics.median(times)


def overlap(a, b):
    union = np.logical_or(a, b).sum()
    return 1.0 if union == 0 else float(np.logical_and(a, b).sum() / union)


def det_accuracy(reference, model):
    r, m = session(reference), session(model)
    name = r.get_inputs()[0].name
    scores = []
    for p in test_photos:
        x = det_input(load(p))
        scores.append(overlap(r.run(None, {name: x})[0] > 0.3, m.run(None, {name: x})[0] > 0.3))
    return f"text areas overlap {statistics.mean(scores):.3f} (worst {min(scores):.3f})"


def read_line(s, x):
    scores = s.run(None, {s.get_inputs()[0].name: x})[0][0]
    text, previous = [], -1
    for slot in scores.argmax(axis=1):
        if slot != 0 and slot != previous and slot - 1 < len(characters):
            text.append(characters[slot - 1])
        previous = slot
    return "".join(text)


def rec_accuracy(reference, model):
    r, m = session(reference), session(model)
    same = total = right_ref = right = 0
    for t in TEST_LINES:
        for size in LINE_SIZES:
            x = line_input(t, size)
            a, b = read_line(r, x), read_line(m, x)
            same += a == b
            right_ref += a == t
            right += b == t
            total += 1
    return f"reads {same}/{total} lines as the original does; {right}/{total} correct (original {right_ref}/{total})"


def face_spots(s, x):
    outputs = dict(zip([o.name for o in s.get_outputs()], s.run(None, {s.get_inputs()[0].name: x})))
    spots = []
    for stride in (8, 16, 32):
        score = np.sqrt(np.clip(outputs[f"cls_{stride}"] * outputs[f"obj_{stride}"], 0, 1))
        spots.append(score.reshape(-1) > 0.6)
    return np.concatenate(spots)


def yunet_accuracy(reference, model):
    r, m = session(reference), session(model)
    scores, faces = [], 0
    for p in photos:  # faces are rare in the samples, so every photo is used
        x = yunet_input(load(p))
        a, b = face_spots(r, x), face_spots(m, x)
        faces += int(a.any())
        if a.any() or b.any():
            scores.append(overlap(a, b))
    if not scores:
        return "no faces found in the samples"
    return f"face spots overlap {statistics.mean(scores):.3f} (worst {min(scores):.3f}) over {len(scores)} photos with faces"


def normalised(v):
    v = v.reshape(-1)
    return v / np.linalg.norm(v)


def image_prints(model):
    s = session(model)
    name = s.get_inputs()[0].name
    return {p.stem: normalised(s.run(None, {name: clip_image_input(load(p))})[0]) for p in test_photos}


def text_prints(model, phrases):
    s = session(model)
    names = [i.name for i in s.get_inputs()]
    return {t: normalised(s.run(None, text_feeds(names, t))[0]) for t in phrases}


def found(image_fps, text_fps):
    """How many test photos come top for their own caption."""
    hits = total = 0
    for stem, fp in image_fps.items():
        caption = CAPTIONS.get(stem)
        if caption is None:
            continue
        best = max(image_fps, key=lambda s: float(np.dot(image_fps[s], text_fps[caption])))
        hits += CAPTIONS.get(best) == caption
        total += 1
    return hits, total


test_captions = sorted({CAPTIONS[p.stem] for p in test_photos if p.stem in CAPTIONS})


def clip_image_accuracy(reference, model, reference_text):
    a, b = image_prints(reference), image_prints(model)
    texts = text_prints(reference_text, test_captions)
    worst = min(float(np.dot(a[k], b[k])) for k in a)
    h0, n = found(a, texts)
    h1, _ = found(b, texts)
    return f"fingerprint similarity worst {worst:.4f}; captions finding their photo {h1}/{n} (original {h0}/{n})"


def clip_text_accuracy(reference, model, reference_image):
    a, b = text_prints(reference, test_captions + CLIP_CALIBRATION_PHRASES), text_prints(model, test_captions + CLIP_CALIBRATION_PHRASES)
    worst = min(float(np.dot(a[k], b[k])) for k in test_captions)
    images = image_prints(reference_image)
    h0, n = found(images, a)
    h1, _ = found(images, b)
    return f"fingerprint similarity worst {worst:.4f}; captions finding their photo {h1}/{n} (original {h0}/{n})"


# --- Running it ----------------------------------------------------------------------------

combined = onnx.load(str(src / "clip.onnx"))
outputs = [o.name for o in combined.graph.output]
inputs = [i.name for i in combined.graph.input]
extractor = onnx.utils.Extractor(combined)
onnx.save(extractor.extract_model([i for i in inputs if "pixel" in i], [next(o for o in outputs if "image_embeds" in o)]), str(work / "clip_image.onnx"))
onnx.save(extractor.extract_model([i for i in inputs if "pixel" not in i], [next(o for o in outputs if "text_embeds" in o)]), str(work / "clip_text.onnx"))
del combined, extractor

sample = load(test_photos[0])
MODELS = [
    ("det", "Text finder", src / "det.onnx", lambda s: {s.get_inputs()[0].name: det_input(sample)}),
    ("rec", "Text reader", src / "rec.onnx", lambda s: {s.get_inputs()[0].name: line_input("Open daily 9 to 5")}),
    ("yunet", "Face finder", src / "yunet.onnx", lambda s: {s.get_inputs()[0].name: yunet_input(sample)}),
    ("clip_image", "TinyCLIP image half", work / "clip_image.onnx", lambda s: {s.get_inputs()[0].name: clip_image_input(sample)}),
    ("clip_text", "TinyCLIP text half", work / "clip_text.onnx", lambda s: text_feeds([i.name for i in s.get_inputs()], "a black dog on the beach")),
]

cores = os.cpu_count() or 1
rows = []
for key, title, original, feeds_for in MODELS:
    for label, model in variants(key, original).items():
        try:
            feeds = feeds_for(session(model))
            one = timing(model, feeds, 1)
            many = timing(model, feeds, min(4, cores))
            if key == "det":
                accuracy = det_accuracy(original, model)
            elif key == "rec":
                accuracy = rec_accuracy(original, model)
            elif key == "yunet":
                accuracy = yunet_accuracy(original, model)
            elif key == "clip_image":
                accuracy = clip_image_accuracy(original, model, work / "clip_text.onnx")
            else:
                accuracy = clip_text_accuracy(original, model, work / "clip_image.onnx")
            row = (title, label, f"{model.stat().st_size / 1e6:.1f} MB", f"{one:.1f} ms", f"{many:.1f} ms", accuracy)
        except Exception as error:
            row = (title, label, f"{model.stat().st_size / 1e6:.1f} MB", "-", "-", f"can't run: {str(error).splitlines()[0][:120]}")
        print(" | ".join(row), flush=True)
        rows.append(row)

table = ["| Model | Way | Size | 1 core | 4 cores | Accuracy against the original |", "|---|---|---|---|---|---|"]
table += ["| " + " | ".join(r) + " |" for r in rows]
report = "\n".join(table)
print(report)
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as f:
        f.write(f"## Shrinking the models to 8-bit\n\n{len(photos)} sample photos, {cores} cores, onnxruntime {ort.__version__}\n\n{report}\n")
(work / "report.json").write_text(json.dumps(rows, indent=1), encoding="utf-8")
