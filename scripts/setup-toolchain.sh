#!/usr/bin/env bash
set -euo pipefail

# Bootstraps self-contained Linux x86_64 Android SDK/JDK/Kotlin build toolchain into /tmp/android-toolchain
# using only open mirrors on pypi.org, registry.npmjs.org, and github.com.

TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-/tmp/android-toolchain}"
VENV_DIR="${VENV_DIR:-/tmp/jdk-venv}"

mkdir -p "$TOOLCHAIN_DIR"

if [ ! -x "$VENV_DIR/bin/python" ]; then
  echo "[toolchain] Creating Python venv & installing OpenJDK 21 runtime + Pillow..."
  python3 -m venv "$VENV_DIR"
  "$VENV_DIR/bin/pip" install -q "jdk4py==21.0.8.2" Pillow
fi

JAVA_HOME="$("$VENV_DIR/bin/python" -c "import jdk4py; print(str(jdk4py.JAVA_HOME))")"
KEYTOOL_BIN="$JAVA_HOME/bin/keytool"

if [ ! -f "$TOOLCHAIN_DIR/android.jar" ] || [ ! -f "$TOOLCHAIN_DIR/d8.jar" ] || [ ! -f "$TOOLCHAIN_DIR/apksigner.jar" ]; then
  echo "[toolchain] Fetching Android API 34 android.jar, D8 dexer, & APKSigner..."
  rm -rf /tmp/h2apk_fetch
  git clone --depth 1 --filter=blob:none --sparse https://github.com/HashShin/H2APK.git /tmp/h2apk_fetch
  (cd /tmp/h2apk_fetch && git sparse-checkout set tools)
  cp -f /tmp/h2apk_fetch/tools/* "$TOOLCHAIN_DIR/"
  rm -rf /tmp/h2apk_fetch
fi

if [ ! -f "$TOOLCHAIN_DIR/android-28.jar" ]; then
  echo "[toolchain] Fetching Android 9.0 Pie (API 28) platform jar for AAPT resource packaging..."
  rm -rf /tmp/sable_platforms
  git clone --depth 1 --filter=blob:none --sparse https://github.com/Sable/android-platforms.git /tmp/sable_platforms
  (cd /tmp/sable_platforms && git sparse-checkout set android-28)
  cp -f /tmp/sable_platforms/android-28/android.jar "$TOOLCHAIN_DIR/android-28.jar"
  rm -rf /tmp/sable_platforms
fi

if [ ! -f "$TOOLCHAIN_DIR/ecj.jar" ]; then
  echo "[toolchain] Fetching Eclipse Java Compiler (ecj.jar)..."
  rm -rf /tmp/ecj_fetch
  git clone --depth 1 --filter=blob:none --sparse https://github.com/alanvoss/neo4j_openshift_v2.git /tmp/ecj_fetch
  (cd /tmp/ecj_fetch && git sparse-checkout set usr/lib/java)
  cp -f /tmp/ecj_fetch/usr/lib/java/eclipse-ecj.jar "$TOOLCHAIN_DIR/ecj.jar"
  rm -rf /tmp/ecj_fetch
fi

# Use Kotlin 1.9.24 matching D8 8.2 metadata version 1.9 cleanly with zero metadata warnings
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

if [ ! -x "$TOOLCHAIN_DIR/aaptjs/bin/linux/aapt" ]; then
  echo "[toolchain] Fetching Linux x86_64 AAPT resource compiler..."
  (
    cd "$TOOLCHAIN_DIR"
    curl -sL "https://registry.npmjs.org/aaptjs/-/aaptjs-1.3.2.tgz" -o aaptjs.tgz
    tar -xzf aaptjs.tgz && rm -rf aaptjs && mv package aaptjs && rm -f aaptjs.tgz
    chmod +x aaptjs/bin/linux/aapt
  )
fi

if [ ! -f "$TOOLCHAIN_DIR/nativeports-debug.p12" ]; then
  echo "[toolchain] Generating RSA-2048 APK signing keystore..."
  rm -f "$TOOLCHAIN_DIR/debug.keystore"
  "$KEYTOOL_BIN" -genkeypair \
    -keystore "$TOOLCHAIN_DIR/nativeports-debug.p12" \
    -storetype PKCS12 \
    -storepass android \
    -alias nativeports \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Native-Ports Oprek Lab, OU=Feeding Frenzy Android 9+, O=AchmadRisqiMaulana2, C=ID" >/dev/null 2>&1
fi

echo "[toolchain] Ready at $TOOLCHAIN_DIR"
