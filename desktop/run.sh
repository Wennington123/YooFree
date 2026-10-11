#!/usr/bin/env bash
# Roda o YooFree desktop.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
exec python3 main.py "$@"
