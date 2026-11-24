#!/usr/bin/env bash
# Empacota o YooFree desktop como AppImage.
#
# Requer: python3 (com PySide6/av/numpy/PyInstaller instalados), rsvg-convert,
# curl. O appimagetool e baixado automaticamente.
#
# Uso:
#   bash packaging/build-appimage.sh [saida.AppImage]
#
# Variaveis:
#   ARCH      x86_64 (padrao) | aarch64
#   VERSION   sobrescreve a versao lida de yoofree.__version__
#   SKIP_TEST 1 pula o smoke test do executavel
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
PY="${PYTHON:-python3}"
ROOT="$(cd .. && pwd)"

VERSION="${VERSION:-$("$PY" -c 'import yoofree; print(yoofree.__version__)')}"
ARCH="${ARCH:-x86_64}"
OUT="${1:-$ROOT/YooFree-${VERSION}-${ARCH}.AppImage}"
BUILD="$ROOT/build/appimage"
APPDIR="$BUILD/YooFree.AppDir"
DIST="$BUILD/dist/yoofree"

rm -rf "$BUILD"
mkdir -p "$BUILD/dist" "$BUILD/work"

echo ":: YooFree $VERSION ($ARCH)"
echo ":: PyInstaller (onedir)"
# os wheels do PyAV guardam as libs do FFmpeg num diretorio irmao 'av.libs'
AV_LIBS="$("$PY" - <<'EOF'
import pathlib, av, sys
libs = pathlib.Path(av.__file__).resolve().parent.parent / "av.libs"
print(libs if libs.is_dir() else "")
EOF
)"
EXTRA=()
if [ -n "$AV_LIBS" ]; then EXTRA+=(--add-binary "$AV_LIBS/*:av.libs"); fi

"$PY" -m PyInstaller --noconfirm --clean --onedir --name yoofree \
  --distpath "$BUILD/dist" --workpath "$BUILD/work" --specpath "$BUILD" \
  --collect-submodules yoofree \
  --collect-binaries av --collect-data av \
  "${EXTRA[@]}" \
  --exclude-module tkinter \
  main.py > "$BUILD/pyinstaller.log" 2>&1 || { tail -40 "$BUILD/pyinstaller.log"; exit 1; }

if [ "${SKIP_TEST:-0}" != 1 ]; then
  echo ":: smoke test"
  QT_QPA_PLATFORM=offscreen YOOFREE_PLAYER=av timeout 60 "$DIST/yoofree" --help >/dev/null
fi

echo ":: AppDir"
mkdir -p "$APPDIR/usr/bin" \
  "$APPDIR/usr/share/applications" \
  "$APPDIR/usr/share/icons/hicolor/256x256/apps"
cp -a "$DIST/." "$APPDIR/usr/bin/"

render_icon() {  # $1=svg $2=png 256x256
  if command -v rsvg-convert >/dev/null 2>&1; then
    rsvg-convert -w 256 -h 256 "$1" -o "$2"
  elif command -v inkscape >/dev/null 2>&1; then
    inkscape "$1" -w 256 -h 256 -o "$2"
  else  # fallback: PySide6 (QtSvg) ja e dependencia do app
    QT_QPA_PLATFORM=offscreen "$PY" - "$1" "$2" <<'EOF'
import sys
from PySide6.QtGui import QGuiApplication, QImage, QPainter
from PySide6.QtSvg import QSvgRenderer
app = QGuiApplication([])
img = QImage(256, 256, QImage.Format_ARGB32)
img.fill(0)
p = QPainter(img)
QSvgRenderer(sys.argv[1]).render(p)
p.end()
sys.exit(0 if img.save(sys.argv[2]) else 1)
EOF
  fi
}
render_icon ../assets/icon.svg "$APPDIR/yoofree.png"
cp "$APPDIR/yoofree.png" "$APPDIR/.DirIcon"
cp "$APPDIR/yoofree.png" "$APPDIR/usr/share/icons/hicolor/256x256/apps/yoofree.png"

cat > "$APPDIR/yoofree.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=YooFree
GenericName=Camera Yoosee/GW
Comment=Cliente aberto para cameras Yoosee/GW (sem nuvem, sem conta)
Exec=yoofree
Icon=yoofree
Terminal=false
Categories=AudioVideo;Network;
Keywords=camera;rtsp;yoosee;gwell;hevc;
StartupWMClass=yoofree
EOF
cp "$APPDIR/yoofree.desktop" "$APPDIR/usr/share/applications/yoofree.desktop"

cat > "$APPDIR/AppRun" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
HERE="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"
BIN="$HERE/usr/bin"
export PATH="$BIN:$PATH"
export LD_LIBRARY_PATH="$BIN/_internal:$BIN/_internal/av.libs${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
# o bundle traz o FFmpeg do PyAV: nao depende dos plugins GStreamer da distro
export YOOFREE_PLAYER="${YOOFREE_PLAYER:-av}"
exec "$BIN/yoofree" "$@"
EOF
chmod +x "$APPDIR/AppRun"

echo ":: appimagetool"
TOOL="$BUILD/appimagetool-$ARCH.AppImage"
curl -fsSL -o "$TOOL" \
  "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-$ARCH.AppImage"
chmod +x "$TOOL"
mkdir -p "$(dirname "$OUT")"
ARCH="$ARCH" VERSION="$VERSION" APPIMAGE_EXTRACT_AND_RUN=1 \
  "$TOOL" --no-appstream "$APPDIR" "$OUT"

echo
echo "gerado: $OUT  ($(du -h "$OUT" | cut -f1))  versao $VERSION  arch $ARCH"
