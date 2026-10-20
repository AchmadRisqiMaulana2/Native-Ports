#!/usr/bin/env bash
set -euo pipefail

# Universal self-contained Android 9+ (Java + Kotlin) build toolchain bootstrapper.
# Works seamlessly on:
#   1. Restricted Arena sandboxes (via pypi.org, registry.npmjs.org, github.com)
#   2. GitHub Actions ubuntu-latest runners (reuses preinstalled JDK & Android SDK when available)
#   3. Local Linux workstations

export GIT_LFS_SKIP_SMUDGE=1
TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-/tmp/android-toolchain}"
VENV_DIR="${VENV_DIR:-/tmp/jdk-venv}"
mkdir -p "$TOOLCHAIN_DIR"

# 1. Resolve Python interpreter with Pillow
if [ -x "$VENV_DIR/bin/python" ]; then
  PY_BIN="$VENV_DIR/bin/python"
elif python3 -m venv "$VENV_DIR" >/dev/null 2>&1; then
  PY_BIN="$VENV_DIR/bin/python"
  "$PY_BIN" -m pip install -q Pillow
else
  PY_BIN="$(command -v python3)"
  "$PY_BIN" -m pip install -q --user --break-system-packages Pillow 2>/dev/null || "$PY_BIN" -m pip install -q Pillow
fi

# 2. Resolve JAVA_HOME (use preinstalled JDK if >= 11, otherwise install Temurin 21 via jdk4py)
if command -v java >/dev/null 2>&1 && command -v keytool >/dev/null 2>&1; then
  RESOLVED_JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
else
  if ! "$PY_BIN" -c "import jdk4py" >/dev/null 2>&1; then
    echo "[toolchain] Installing OpenJDK 21 runtime via jdk4py..."
    "$PY_BIN" -m pip install -q "jdk4py==21.0.8.2"
  fi
  RESOLVED_JAVA_HOME="$("$PY_BIN" -c "import jdk4py; print(str(jdk4py.JAVA_HOME))")"
fi

# 3. Resolve Android API 34 jar, D8 dexer, & APKSigner
if [ ! -f "$TOOLCHAIN_DIR/android.jar" ] || [ ! -f "$TOOLCHAIN_DIR/d8.jar" ] || [ ! -f "$TOOLCHAIN_DIR/apksigner.jar" ]; then
  echo "[toolchain] Fetching Android API 34 android.jar, D8 dexer, & APKSigner..."
  rm -rf /tmp/h2apk_fetch
  GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 --filter=blob:none --sparse https://github.com/HashShin/H2APK.git /tmp/h2apk_fetch
  (cd /tmp/h2apk_fetch && GIT_LFS_SKIP_SMUDGE=1 git sparse-checkout set tools)
  cp -f /tmp/h2apk_fetch/tools/* "$TOOLCHAIN_DIR/"
  rm -rf /tmp/h2apk_fetch
fi

# 4. Resolve Android 9.0 Pie (API 28) platform jar for AAPT resource packaging
if [ ! -f "$TOOLCHAIN_DIR/android-28.jar" ]; then
  if [ -n "${ANDROID_HOME:-}" ] && [ -f "${ANDROID_HOME}/platforms/android-28/android.jar" ]; then
    cp -f "${ANDROID_HOME}/platforms/android-28/android.jar" "$TOOLCHAIN_DIR/android-28.jar"
  else
    echo "[toolchain] Fetching Android 9.0 Pie (API 28) platform jar..."
    rm -rf /tmp/sable_platforms
    GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 --filter=blob:none --sparse https://github.com/Sable/android-platforms.git /tmp/sable_platforms
    (cd /tmp/sable_platforms && GIT_LFS_SKIP_SMUDGE=1 git sparse-checkout set android-28)
    cp -f /tmp/sable_platforms/android-28/android.jar "$TOOLCHAIN_DIR/android-28.jar"
    rm -rf /tmp/sable_platforms
  fi
fi

# 5. Resolve Eclipse Java Compiler (ecj.jar)
if [ ! -f "$TOOLCHAIN_DIR/ecj.jar" ]; then
  echo "[toolchain] Fetching Eclipse Java Compiler (ecj.jar)..."
  rm -rf /tmp/ecj_fetch
  GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 --filter=blob:none --sparse https://github.com/alanvoss/neo4j_openshift_v2.git /tmp/ecj_fetch
  (cd /tmp/ecj_fetch && GIT_LFS_SKIP_SMUDGE=1 git sparse-checkout set usr/lib/java)
  cp -f /tmp/ecj_fetch/usr/lib/java/eclipse-ecj.jar "$TOOLCHAIN_DIR/ecj.jar"
  rm -rf /tmp/ecj_fetch
fi

# 6. Resolve JetBrains Kotlin Compiler 1.9.24
if [ ! -f "$TOOLCHAIN_DIR/kotlin-compiler/.ver_1_9_24" ]; then
  echo "[toolchain] Installing JetBrains Kotlin Compiler 1.9.24..."
  (
    cd "$TOOLCHAIN_DIR"
    curl -sL "https://registry.npmjs.org/kotlin-compiler/-/kotlin-compiler-1.9.24.tgz" -o kotlin-compiler.tgz
    rm -rf kotlin-compiler
    tar -xzf kotlin-compiler.tgz && mv package kotlin-compiler && rm -f kotlin-compiler.tgz
    chmod +x kotlin-compiler/bin/*
    touch kotlin-compiler/.ver_1_9_24
  )
fi

# 7. Resolve Linux x86_64 AAPT binary
if [ ! -x "$TOOLCHAIN_DIR/aaptjs/bin/linux/aapt" ]; then
  echo "[toolchain] Fetching Linux x86_64 AAPT resource compiler..."
  (
    cd "$TOOLCHAIN_DIR"
    curl -sL "https://registry.npmjs.org/aaptjs/-/aaptjs-1.3.2.tgz" -o aaptjs.tgz
    tar -xzf aaptjs.tgz && rm -rf aaptjs && mv package aaptjs && rm -f aaptjs.tgz
    chmod +x aaptjs/bin/linux/aapt
  )
fi

# 8. Generate signing PKCS12 keystore
if [ ! -f "$TOOLCHAIN_DIR/nativeports-debug.p12" ]; then
  echo "[toolchain] Generating RSA-2048 APK signing keystore..."
  "$RESOLVED_JAVA_HOME/bin/keytool" -genkeypair \
    -keystore "$TOOLCHAIN_DIR/nativeports-debug.p12" \
    -storetype PKCS12 \
    -storepass android \
    -alias nativeports \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Native-Ports Oprek Lab, OU=Feeding Frenzy Android 9+, O=AchmadRisqiMaulana2, C=ID" >/dev/null 2>&1
fi

cat > "$TOOLCHAIN_DIR/env.sh" <<EOF
export TOOLCHAIN_DIR="$TOOLCHAIN_DIR"
export PY_BIN="$PY_BIN"
export JAVA_HOME="$RESOLVED_JAVA_HOME"
export JAVA_BIN="$RESOLVED_JAVA_HOME/bin/java"
export AAPT_BIN="$TOOLCHAIN_DIR/aaptjs/bin/linux/aapt"
EOF

echo "[toolchain] Ready (JAVA_HOME=$RESOLVED_JAVA_HOME, PY_BIN=$PY_BIN)"
