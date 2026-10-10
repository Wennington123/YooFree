#!/usr/bin/env bash
set -euo pipefail
PROJ="$HOME/YooFree"; TC="$PROJ/.toolchain"; DL="$TC/downloads"
mkdir -p "$DL"
if [ -x "$TC/gradle/bin/gradle" ]; then
  echo "gradle ja existe"
else
  curl -fL --retry 3 -o "$DL/gradle.zip" "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  unzip -q "$DL/gradle.zip" -d "$TC"
  mv "$TC/gradle-8.9" "$TC/gradle"
fi
export JAVA_HOME="$TC/jdk"
"$TC/gradle/bin/gradle" --version
