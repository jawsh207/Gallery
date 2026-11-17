#!/usr/bin/env bash
# Builds what Gallery Plus needs for reading text in photos, into build/plus/:
#   onnxruntime-plus.aar  ONNX Runtime cut down to the operators our models use, arm64 only
#   assets/ocr/           the text models in ONNX Runtime's format, and the reading model's characters
#   assets/faces/         the face finder and face matcher, in the same format
#   assets/clip/          TinyCLIP's image and text halves, and its tokenizer's pieces and merges
#   test/                 reference tokenizations the unit tests check the app's tokenizer against
# Needs Python 3, the Android SDK and NDK (ANDROID_HOME, ANDROID_NDK_HOME), Java and Ninja.
# CI runs it and caches the result; run it locally once before building the Plus edition.
set -euo pipefail
ORT_VERSION=1.20.1
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE="$ROOT/scripts/plus"
OUT="$ROOT/build/plus"
WORK="$OUT/work"
rm -rf "$OUT"; mkdir -p "$WORK/download" "$OUT/assets/ocr" "$OUT/assets/faces" "$OUT/assets/clip" "$OUT/test"

python3 -m pip install --quiet "onnxruntime==$ORT_VERSION" "onnx==1.17.0" "pillow>=10.1" numpy tokenizers

# Download each model and check it against its pinned SHA-256.
grep -v '^#' "$HERE/models.txt" | while read -r name url sha; do
  [ -z "$name" ] && continue
  curl -fsSL --retry 3 -o "$WORK/download/$name" "$url"
  actual=$(sha256sum "$WORK/download/$name" | cut -d' ' -f1)
  if [ "$sha" = "unpinned" ]; then
    echo "::notice title=Unpinned model::$name sha256 $actual"
  elif [ "$sha" != "$actual" ]; then
    echo "::error::$name has sha256 $actual, expected $sha"; exit 1
  fi
done

python3 "$HERE/prepare_models.py" "$WORK/download" "$WORK/models"
cp "$WORK/models/onnx/det.ort" "$WORK/models/onnx/rec.ort" "$OUT/assets/ocr/"
cp "$WORK/download/rec_dict.txt" "$OUT/assets/ocr/rec_dict.txt"
cp "$WORK/models/onnx/yunet.ort" "$WORK/models/onnx/sface.ort" "$OUT/assets/faces/"
cp "$WORK/models/onnx/clip_image.ort" "$WORK/models/onnx/clip_text.ort" "$OUT/assets/clip/"
cp "$WORK/models/clip_vocab.txt" "$WORK/models/clip_merges.txt" "$OUT/assets/clip/"
# Reference tokenizations for the unit tests (not shipped in the app).
cp "$WORK/models/clip_tokens_expected.txt" "$OUT/test/"
CONFIG=$(ls "$WORK/models/onnx/"*.config | head -1)
echo "Operators used:"; cat "$CONFIG"

git clone --quiet --depth 1 --branch "v$ORT_VERSION" https://github.com/microsoft/onnxruntime "$WORK/onnxruntime"
# GitLab re-generated the archive of the Eigen commit ONNX Runtime 1.20 uses, so its SHA-1
# changed though the source is the same commit; pin the new one.
sed -i 's/be8be39fdbc6e60e94fa7870b280707069b5b81a/32b145f525a8308d7ab1c09388b2e288312d8eba/' "$WORK/onnxruntime/cmake/deps.txt"
python3 "$WORK/onnxruntime/tools/ci_build/github/android/build_aar_package.py" \
  --build_dir "$WORK/ort-build" --config MinSizeRel \
  --android_sdk_path "$ANDROID_HOME" --android_ndk_path "$ANDROID_NDK_HOME" \
  --include_ops_by_config "$CONFIG" \
  "$HERE/ort_settings.json"
AAR=$(find "$WORK/ort-build" -name 'onnxruntime-android-*.aar' | head -1)
cp "$AAR" "$OUT/onnxruntime-plus.aar"
ls -l "$OUT/onnxruntime-plus.aar" "$OUT/assets/ocr" "$OUT/assets/faces" "$OUT/assets/clip"
