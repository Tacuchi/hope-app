#!/usr/bin/env python3
"""Revisa store/ficha.md contra los límites de Play y las palabras prohibidas.

Cuenta los caracteres de cada bloque marcado (<!-- nombre -->, <!-- corta -->, <!-- completa -->)
y falla si alguno pasa su límite o si aparece una palabra de descarga.
"""
import pathlib
import re
import sys

LIMITS = {"nombre": 30, "corta": 80, "completa": 4000}
FORBIDDEN = re.compile(r"descarg|download|bajar|bájal|bajal|baja videos|downloader", re.IGNORECASE)

path = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "store/ficha.md")
text = path.read_text(encoding="utf-8")
ok = True
for name, limit in LIMITS.items():
    match = re.search(rf"<!-- {name} -->\n(.*?)\n<!-- /{name} -->", text, re.DOTALL)
    if not match:
        print(f"{name}: falta el bloque")
        ok = False
        continue
    block = match.group(1).strip()
    size = len(block)
    state = "ok" if 0 < size <= limit else "PASA EL LÍMITE"
    ok &= state == "ok"
    print(f"{name}: {size}/{limit} {state}")
    if FORBIDDEN.search(block):
        print(f"{name}: menciona descargas")
        ok = False
sys.exit(0 if ok else 1)
