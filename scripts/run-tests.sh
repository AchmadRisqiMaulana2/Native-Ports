#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash "$ROOT_DIR/scripts/setup-toolchain.sh"

TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-/tmp/android-toolchain}"
VENV_DIR="${VENV_DIR:-/tmp/jdk-venv}"
export JAVA_HOME="$("$VENV_DIR/bin/python" -c "import jdk4py; print(str(jdk4py.JAVA_HOME))")"
JAVA_BIN="$JAVA_HOME/bin/java"

APP_DIR="$ROOT_DIR/ports/feeding-frenzy/app"
WORK_DIR="/tmp/ff-test-build"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/classes"

echo "[test] 1/3 Compiling Java core modules (PAK, PE32 Inspector, Audio Synth, 60Hz Engine)..."
find "$APP_DIR/src/main/java" -name "*.java" > "$WORK_DIR/java_sources.txt"
"$JAVA_BIN" -cp "$TOOLCHAIN_DIR/ecj.jar" org.eclipse.jdt.internal.compiler.batch.Main \
  -1.7 -nowarn \
  -bootclasspath "$TOOLCHAIN_DIR/android.jar" \
  -d "$WORK_DIR/classes" \
  @"$WORK_DIR/java_sources.txt"

echo "[test] 2/3 Compiling Kotlin + Java sources together with full bidirectional AST interop..."
find "$APP_DIR/src/main/java" "$APP_DIR/src/main/kotlin" "$APP_DIR/src/test/kotlin" \( -name "*.java" -o -name "*.kt" \) > "$WORK_DIR/all_sources.txt"
"$TOOLCHAIN_DIR/kotlin-compiler/bin/kotlinc" \
  -no-jdk \
  -jvm-target 1.8 \
  -nowarn \
  -classpath "$TOOLCHAIN_DIR/android.jar:$WORK_DIR/classes" \
  -d "$WORK_DIR/classes" \
  @"$WORK_DIR/all_sources.txt"

echo "[test] 3/3 Running NativePortVerificationSuite on OpenJDK 21..."
"$JAVA_BIN" -cp "$WORK_DIR/classes:$TOOLCHAIN_DIR/kotlin-compiler/lib/kotlin-stdlib.jar:$TOOLCHAIN_DIR/android.jar" \
  com.risqi.nativeports.feedingfrenzy.NativePortVerificationSuite
