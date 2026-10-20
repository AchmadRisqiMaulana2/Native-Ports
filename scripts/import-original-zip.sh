#!/usr/bin/env bash
set -euo pipefail

# One-command pipeline when you drop Feeding_Frenzy_Win_Preinstalled_EN.zip (or main.pak) into the repo:
#   ./scripts/import-original-zip.sh path/to/Feeding_Frenzy_Win_Preinstalled_EN.zip
#
# 1. Inspects main.pak + FeedingFrenzy.exe PE32 structure
# 2. Extracts all unpacked assets + RGBA-composed PNGs into unpacked_dump/
# 3. Bundles original main.pak directly into ports/feeding-frenzy/app/src/main/assets/main.pak
# 4. Rebuilds & signs FeedingFrenzy-Android9Plus-Oprek-debug.apk with full original PopCap assets!

if [ $# -lt 1 ]; then
  echo "Usage: $0 <path/to/Feeding_Frenzy_Win_Preinstalled_EN.zip | main.pak>"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_PATH="$1"
VENV_PYTHON="/tmp/jdk-venv/bin/python"
[ -x "$VENV_PYTHON" ] || VENV_PYTHON="python3"

echo "=== 1. Inspecting Original PopCap Archive & PE32 Binary ==="
"$VENV_PYTHON" "$ROOT_DIR/tools/pak_tool.py" inspect "$INPUT_PATH"

echo "=== 2. Extracting & Composing Transparent RGBA Sprites -> unpacked_dump/ ==="
"$VENV_PYTHON" "$ROOT_DIR/tools/pak_tool.py" unpack "$INPUT_PATH" -o "$ROOT_DIR/unpacked_dump" --compose-png

echo "=== 3. Bundling main.pak into Android APK assets/ ==="
"$VENV_PYTHON" - "$INPUT_PATH" "$ROOT_DIR/ports/feeding-frenzy/app/src/main/assets/main.pak" << 'PY'
import sys
from pathlib import Path
sys.path.insert(0, str(Path("tools").resolve()))
from pak_tool import load_pak_bytes

pak_bytes, _, label = load_pak_bytes(sys.argv[1])
out_file = Path(sys.argv[2])
out_file.write_bytes(pak_bytes)
print(f"Copied {label} -> {out_file} ({len(pak_bytes)} bytes)")
PY

echo "=== 4. Rebuilding Signed Android 9+ Native Port APK ==="
bash "$ROOT_DIR/scripts/build-apk.sh"
