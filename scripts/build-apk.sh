#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash "$ROOT_DIR/scripts/setup-toolchain.sh"

TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-/tmp/android-toolchain}"
VENV_DIR="${VENV_DIR:-/tmp/jdk-venv}"
export JAVA_HOME="$("$VENV_DIR/bin/python" -c "import jdk4py; print(str(jdk4py.JAVA_HOME))")"
JAVA_BIN="$JAVA_HOME/bin/java"
AAPT_BIN="$TOOLCHAIN_DIR/aaptjs/bin/linux/aapt"

APP_DIR="$ROOT_DIR/ports/feeding-frenzy/app"
BUILD_DIR="$ROOT_DIR/ports/feeding-frenzy/app/build"
OUT_APK="$ROOT_DIR/FeedingFrenzy-Android9Plus-Oprek-debug.apk"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex"

# Ensure multi-density icons exist
"$VENV_DIR/bin/python" "$ROOT_DIR/tools/generate_icons.py" >/dev/null

echo "[1/6] Packaging Android resources & generating R.java (minSdk=28 Android 9.0, targetSdk=34)..."
"$AAPT_BIN" package -f -m \
  -J "$BUILD_DIR/gen" \
  -M "$APP_DIR/src/main/AndroidManifest.xml" \
  -S "$APP_DIR/src/main/res" \
  -A "$APP_DIR/src/main/assets" \
  -I "$TOOLCHAIN_DIR/android-28.jar" \
  -F "$BUILD_DIR/resources.ap_"

echo "[2/6] Compiling Java sources + generated R.java..."
find "$APP_DIR/src/main/java" "$BUILD_DIR/gen" -name "*.java" > "$BUILD_DIR/java_sources.txt"
"$JAVA_BIN" -cp "$TOOLCHAIN_DIR/ecj.jar" org.eclipse.jdt.internal.compiler.batch.Main \
  -1.7 -nowarn \
  -bootclasspath "$TOOLCHAIN_DIR/android.jar" \
  -d "$BUILD_DIR/classes" \
  @"$BUILD_DIR/java_sources.txt"

echo "[3/6] Compiling Kotlin sources with bidirectional Java interop..."
find "$APP_DIR/src/main/java" "$BUILD_DIR/gen" "$APP_DIR/src/main/kotlin" \( -name "*.java" -o -name "*.kt" \) > "$BUILD_DIR/all_sources.txt"
"$TOOLCHAIN_DIR/kotlin-compiler/bin/kotlinc" \
  -no-jdk \
  -jvm-target 1.8 \
  -nowarn \
  -classpath "$TOOLCHAIN_DIR/android.jar:$BUILD_DIR/classes" \
  -d "$BUILD_DIR/classes" \
  @"$BUILD_DIR/all_sources.txt"

echo "[4/6] Dexing Java + Kotlin bytecode + kotlin-stdlib with Google D8 (min-api 28)..."
jar_in="$BUILD_DIR/app_classes.jar"
(cd "$BUILD_DIR/classes" && "$VENV_DIR/bin/python" -m zipfile -c "$jar_in" .)
"$JAVA_BIN" -cp "$TOOLCHAIN_DIR/d8.jar" com.android.tools.r8.D8 \
  --min-api 28 \
  --release \
  --lib "$TOOLCHAIN_DIR/android.jar" \
  --output "$BUILD_DIR/dex" \
  "$jar_in" \
  "$TOOLCHAIN_DIR/kotlin-compiler/lib/kotlin-stdlib.jar"

echo "[5/6] Packing uncompressed resources.arsc & 4-byte aligning APK..."
"$VENV_DIR/bin/python" "$ROOT_DIR/scripts/apk_packager.py" \
  "$BUILD_DIR/resources.ap_" \
  "$BUILD_DIR/unsigned_aligned.apk" \
  "$BUILD_DIR/dex"/classes*.dex

echo "[6/6] Signing APK (Scheme v1 + v2 + v3 for Android 9–16) & verifying..."
"$JAVA_BIN" -jar "$TOOLCHAIN_DIR/apksigner.jar" sign \
  --ks "$TOOLCHAIN_DIR/nativeports-debug.p12" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --min-sdk-version 28 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$OUT_APK" \
  "$BUILD_DIR/unsigned_aligned.apk"

"$JAVA_BIN" -jar "$TOOLCHAIN_DIR/apksigner.jar" verify --verbose --min-sdk-version 28 "$OUT_APK"
"$AAPT_BIN" dump badging "$OUT_APK" | head -n 6
ls -lh "$OUT_APK"
echo "✅ Built verified installable APK -> $OUT_APK"
