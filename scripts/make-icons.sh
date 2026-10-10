#!/usr/bin/env bash
# Gera os PNGs do icone a partir dos SVGs em assets/.
set -euo pipefail
PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RES="$PROJ/app/src/main/res"

declare -A LEGACY=( [mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192 )
declare -A FG=( [mdpi]=108 [hdpi]=162 [xhdpi]=216 [xxhdpi]=324 [xxxhdpi]=432 )

for d in "${!LEGACY[@]}"; do
  mkdir -p "$RES/mipmap-$d"
  rsvg-convert -w "${LEGACY[$d]}" -h "${LEGACY[$d]}" "$PROJ/assets/icon.svg" \
    -o "$RES/mipmap-$d/ic_launcher.png"
  rsvg-convert -w "${LEGACY[$d]}" -h "${LEGACY[$d]}" "$PROJ/assets/icon.svg" \
    -o "$RES/mipmap-$d/ic_launcher_round.png"
  rsvg-convert -w "${FG[$d]}" -h "${FG[$d]}" "$PROJ/assets/icon_foreground.svg" \
    -o "$RES/mipmap-$d/ic_launcher_foreground.png"
  echo "mipmap-$d ok"
done
