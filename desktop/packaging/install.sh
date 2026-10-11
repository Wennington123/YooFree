#!/usr/bin/env bash
# YooFree desktop - instalador universal (Linux)
#
# Uso:
#   ./install.sh              # instala em ~/.local
#   ./install.sh --deps       # instala tambem as dependencias que faltarem
#   ./install.sh --prefix /usr/local
#   curl -fsSL https://raw.githubusercontent.com/Wennington123/YooFree/main/desktop/packaging/install.sh | bash
set -euo pipefail

REPO="Wennington123/YooFree"
TARBALL="https://github.com/$REPO/releases/latest/download/yoofree-desktop.tar.gz"
PREFIX="${PREFIX:-$HOME/.local}"
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WITH_DEPS=0

while [ $# -gt 0 ]; do
  case "$1" in
    --deps) WITH_DEPS=1 ;;
    --prefix) shift; PREFIX="$1" ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  esac
  shift
done

c() { printf '\033[1;36m%s\033[0m\n' "$*"; }
ok() { printf '\033[1;32m%s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m%s\033[0m\n' "$*"; }
die() { printf '\033[1;31m%s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- dependencias

have_python() { command -v python3 >/dev/null 2>&1; }
have_pyside() { python3 -c "import PySide6" 2>/dev/null; }
have_gi() { python3 -c "import gi; gi.require_version('Gst','1.0'); from gi.repository import Gst" 2>/dev/null; }
have_gst() { command -v gst-inspect-1.0 >/dev/null 2>&1 && gst-inspect-1.0 avdec_h265 >/dev/null 2>&1; }
have_numpy() { python3 -c "import numpy" 2>/dev/null; }
have_av() { python3 -c "import av" 2>/dev/null; }

detect_pm() {
  for pm in pacman apt-get dnf zypper apk xbps-install emerge; do
    command -v "$pm" >/dev/null 2>&1 && { echo "$pm"; return; }
  done
  echo ""
}

pkg_names() {
  # ecoa os pacotes que faltam, separados por espaco, para o gerenciador detectado
  local pm="$1" pkgs=""
  local need_gi=0 need_gst=0 need_pyside=0
  have_gi || need_gi=1
  have_gst || need_gst=1
  have_pyside || need_pyside=1
  # sem GStreamer o video usa PyAV, que precisa de numpy
  local need_numpy=0
  if [ "$need_gst" = 1 ]; then have_numpy || need_numpy=1; fi
  case "$pm" in
    pacman)
      [ $need_gi = 1 ] && pkgs="$pkgs python-gobject"
      [ $need_gst = 1 ] && pkgs="$pkgs gstreamer gst-plugins-base gst-plugins-good gst-plugins-bad gst-libav gst-plugins-ugly"
      [ $need_numpy = 1 ] && pkgs="$pkgs python-numpy"
      [ $need_pyside = 1 ] && pkgs="$pkgs pyside6" ;;
    apt-get)
      [ $need_gi = 1 ] && pkgs="$pkgs python3-gi gir1.2-gstreamer-1.0 gir1.2-gst-plugins-base-1.0"
      [ $need_gst = 1 ] && pkgs="$pkgs gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-libav"
      [ $need_numpy = 1 ] && pkgs="$pkgs python3-numpy"
      [ $need_pyside = 1 ] && pkgs="$pkgs python3-pyside6.qtwidgets python3-pyside6.qtgui python3-pyside6.qtcore" ;;
    dnf)
      [ $need_gi = 1 ] && pkgs="$pkgs python3-gobject"
      [ $need_gst = 1 ] && pkgs="$pkgs gstreamer1-plugins-base gstreamer1-plugins-good gstreamer1-plugins-bad-free gstreamer1-plugins-ugly-free gstreamer1-libav"
      [ $need_numpy = 1 ] && pkgs="$pkgs python3-numpy"
      [ $need_pyside = 1 ] && pkgs="$pkgs python3-pyside6" ;;
    zypper)
      [ $need_gi = 1 ] && pkgs="$pkgs python3-gobject python3-gobject-Gdk"
      [ $need_gst = 1 ] && pkgs="$pkgs gstreamer-plugins-base gstreamer-plugins-good gstreamer-plugins-bad gstreamer-plugins-libav"
      [ $need_numpy = 1 ] && pkgs="$pkgs python3-numpy"
      [ $need_pyside = 1 ] && pkgs="$pkgs python3-pyside6" ;;
    apk)
      [ $need_gi = 1 ] && pkgs="$pkgs py3-gobject3"
      [ $need_gst = 1 ] && pkgs="$pkgs gst-plugins-base gst-plugins-good gst-plugins-bad gst-libav"
      [ $need_numpy = 1 ] && pkgs="$pkgs py3-numpy"
      [ $need_pyside = 1 ] && pkgs="$pkgs py3-pyside6" ;;
    xbps-install)
      [ $need_gi = 1 ] && pkgs="$pkgs python3-gobject"
      [ $need_gst = 1 ] && pkgs="$pkgs gst-plugins-base1 gst-plugins-good1 gst-plugins-bad1 gst-libav1"
      [ $need_numpy = 1 ] && pkgs="$pkgs python3-numpy"
      [ $need_pyside = 1 ] && pkgs="$pkgs python3-pyside6" ;;
    emerge)
      [ $need_gi = 1 ] && pkgs="$pkgs dev-python/pygobject"
      [ $need_gst = 1 ] && pkgs="$pkgs media-plugins/gst-plugins-meta"
      [ $need_numpy = 1 ] && pkgs="$pkgs dev-python/numpy"
      [ $need_pyside = 1 ] && pkgs="$pkgs dev-python/pyside6" ;;
  esac
  echo "$pkgs"
}

install_pm() {
  local pm="$1"; shift
  case "$pm" in
    pacman) sudo pacman -S --needed --noconfirm "$@" ;;
    apt-get) sudo apt-get update && sudo apt-get install -y "$@" ;;
    dnf) sudo dnf install -y "$@" ;;
    zypper) sudo zypper install -y "$@" ;;
    apk) sudo apk add "$@" ;;
    xbps-install) sudo xbps-install -y "$@" ;;
    emerge) sudo emerge -av "$@" ;;
  esac
}

check_deps() {
  c "Conferindo dependencias..."
  have_python || die "python3 nao encontrado. Instale o Python 3.9+."
  local missing=""
  have_pyside || missing="$missing PySide6"
  have_gi || missing="$missing PyGObject(gi)"
  have_gst || missing="$missing GStreamer+avdec_h265"
  if ! have_gst && ! have_numpy; then missing="$missing numpy"; fi
  if ! have_gst && ! have_av; then missing="$missing av(PyAV)"; fi
  if [ -z "$missing" ]; then
    ok "Tudo pronto (Python, PySide6, GStreamer)."
    return
  fi

  warn "Faltando:$missing"
  local pm; pm="$(detect_pm)"
  if [ -z "$pm" ]; then
    warn "Nao identifiquei o gerenciador de pacotes."
    warn "Instale manualmente: python3, PyGObject, GStreamer (plugins base/good/bad + libav) e PySide6."
    return
  fi

  local pkgs; pkgs="$(pkg_names "$pm")"
  if [ -z "$pkgs" ]; then
    warn "Nao mapeei os pacotes para '$pm'. Instale PySide6, PyGObject e GStreamer manualmente."
    return
  fi

  echo "Comando sugerido ($pm):"
  echo "    $pm: $pkgs"
  if [ "$WITH_DEPS" = 1 ]; then
    c "Instalando dependencias via $pm..."
    install_pm "$pm" $pkgs
  else
    warn "Rode de novo com --deps para eu instalar isso automaticamente,"
    warn "ou cole o comando acima manualmente."
  fi
}

# ------------------------------------------------------------------- instalacao

install_app() {
  local lib_src="$1"
  local dest="$PREFIX/share/yoofree"
  local bin="$PREFIX/bin"

  c "Instalando em $dest"
  rm -rf "$dest"
  mkdir -p "$dest"
  cp -r "$lib_src/yoofree" "$dest/"
  cp "$lib_src/main.py" "$dest/"

  mkdir -p "$bin"
  cat > "$bin/yoofree" <<EOF
#!/usr/bin/env bash
exec python3 "$dest/main.py" "\$@"
EOF
  chmod +x "$bin/yoofree"

  local apps="$PREFIX/share/applications"
  mkdir -p "$apps"
  cat > "$apps/yoofree.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=YooFree
Comment=Camera Yoosee/GW na rede local
Exec=$bin/yoofree
Terminal=false
Categories=AudioVideo;Video;
EOF

  ok "Instalado:"
  echo "    comando : yoofree"
  echo "    app     : $dest"
  echo "    atalho  : $apps/yoofree.desktop"

  case ":$PATH:" in
    *":$bin:"*) ;;
    *) warn "Adicione $bin ao PATH (ex.: no ~/.bashrc: export PATH=\"$bin:\$PATH\")" ;;
  esac
}

# ------------------------------------------------------------------- principal

if [ -d "$SRC/yoofree" ]; then
  LIB_SRC="$SRC"
elif [ -d "$SRC/../yoofree" ]; then
  LIB_SRC="$(cd "$SRC/.." && pwd)"
else
  c "Baixando o YooFree mais recente..."
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL "$TARBALL" | tar xz -C "$tmp"
  LIB_SRC="$tmp/desktop"
  [ -d "$LIB_SRC/yoofree" ] || LIB_SRC="$tmp"
fi

check_deps
install_app "$LIB_SRC"
ok "Pronto! Rode: yoofree   (ou: yoofree --host 192.168.1.2)"
