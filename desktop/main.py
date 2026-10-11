#!/usr/bin/env python3
"""YooFree desktop - entrada.

Uso:
  python3 main.py                 # abre a janela
  python3 main.py --host 192.168.1.2   # ja conecta na camera
"""

import argparse

from yoofree.ui import run


def main():
    parser = argparse.ArgumentParser(description="YooFree - camera Yoosee/GW no Linux")
    parser.add_argument("--host", help="IP da camera (ex.: 192.168.1.2)")
    args = parser.parse_args()
    run(args.host)


if __name__ == "__main__":
    main()
