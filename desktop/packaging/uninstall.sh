#!/usr/bin/env bash
# Remove o YooFree instalado pelo install.sh
set -euo pipefail
PREFIX="${PREFIX:-$HOME/.local}"
rm -rf "$PREFIX/share/yoofree" "$PREFIX/bin/yoofree" "$PREFIX/share/applications/yoofree.desktop"
printf '\033[1;32mYooFree removido.\033[0m\n'
