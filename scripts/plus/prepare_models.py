"""
Prepares the Plus edition's text-recognition models: shrinks the reading model's matrix
weights to 8-bit numbers (the text-finding model is small and kept as it is), then converts
both to ONNX Runtime's own format, which also writes the list of operators they use. That
list is what the cut-down runtime is built with.

Usage: python3 prepare_models.py <folder with det.onnx and rec.onnx> <output folder>
"""
import pathlib
import shutil
import subprocess
import sys

from onnxruntime.quantization import QuantType, quantize_dynamic

src = pathlib.Path(sys.argv[1])
out = pathlib.Path(sys.argv[2])
work = out / "onnx"
work.mkdir(parents=True, exist_ok=True)

shutil.copy(src / "det.onnx", work / "det.onnx")
# Only matrix multiplications are quantized: the convolutions stay in full precision, which
# keeps accuracy close to the original while the transformer layers (most of the weights) shrink.
quantize_dynamic(
    str(src / "rec.onnx"),
    str(work / "rec.onnx"),
    weight_type=QuantType.QInt8,
    op_types_to_quantize=["MatMul", "Gemm"],
)

subprocess.run(
    [
        sys.executable, "-m", "onnxruntime.tools.convert_onnx_models_to_ort", str(work),
        "--enable_type_reduction", "--optimization_style", "Fixed",
    ],
    check=True,
)
for f in sorted(work.iterdir()):
    print(f"{f.name}: {f.stat().st_size} bytes")
