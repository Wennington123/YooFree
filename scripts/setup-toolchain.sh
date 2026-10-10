#!/usr/bin/env bash
# YooFree - toolchain local (nada instalado no sistema)
set -euo pipefail

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$PROJ/.toolchain"
JDK="$TC/jdk"
SDK="$TC/android-sdk"
DL="$TC/downloads"
mkdir -p "$JDK" "$SDK" "$DL"

log() { printf '\n=== %s ===\n' "$*"; }

log "1/3 JDK 17 (Temurin)"
if [ -x "$JDK/bin/javac" ]; then
  echo "ja existe: $("$JDK/bin/java" -version 2>&1 | head -1)"
else
  curl -fL --retry 3 -o "$DL/jdk17.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf "$DL/jdk17.tar.gz" -C "$JDK" --strip-components=1
  echo "ok: $("$JDK/bin/java" -version 2>&1 | head -1)"
fi
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

log "2/3 Android cmdline-tools"
if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "ja existe"
else
  curl -fL --retry 3 -o "$DL/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf "$SDK/cmdline-tools"; mkdir -p "$SDK/cmdline-tools"
  unzip -q "$DL/cmdline-tools.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"

log "3/3 Pacotes SDK (platform-tools, android-34, build-tools 34)"
yes | "$SDKM" --licenses >/dev/null 2>&1 || true
"$SDKM" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

log "PRONTO"
"$JDK/bin/java" -version 2>&1 | head -1
"$SDKM" --list_installed 2>/dev/null | sed -n '1,20p'
echo "JAVA_HOME=$JDK"
echo "ANDROID_HOME=$SDK"
