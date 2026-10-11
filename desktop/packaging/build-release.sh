#!/usr/bin/env bash
# Empacota o YooFree desktop em yoofree-desktop-<versao>.tar.gz (para o release).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
VERSION="$(python3 -c 'import yoofree; print(yoofree.__version__)')"
# nome fixo: o install.sh baixa de releases/latest/download/yoofree-desktop.tar.gz
OUT="../yoofree-desktop.tar.gz"

find . -name '__pycache__' -type d -prune -exec rm -rf {} + 2>/dev/null || true

tar czf "$OUT" \
  --exclude='*__pycache__*' \
  --transform "s,^,desktop/," \
  yoofree main.py run.sh README.md packaging

echo "gerado: $(cd .. && pwd)/$(basename "$OUT")  ($(du -h "$OUT" | cut -f1))  versao $VERSION"
