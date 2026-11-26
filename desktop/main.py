#!/usr/bin/env python3
"""YooFree desktop - entrada.

Uso:
  python3 main.py                 # abre a janela
  python3 main.py --host 192.168.1.2   # ja conecta na camera
"""

import argparse
import os

from yoofree.ui import run


def main():
    parser = argparse.ArgumentParser(description="YooFree - camera Yoosee/GW no Linux")
    parser.add_argument("--host", help="IP da camera (ex.: 192.168.1.2)")
    parser.add_argument(
        "--password",
        default=os.environ.get("YOOFREE_RTSP_PASSWORD", ""),
        help="senha RTSP (so para firmwares que pedem autenticacao)",
    )
    args = parser.parse_args()
    run(args.host, args.password)


if __name__ == "__main__":
    main()
