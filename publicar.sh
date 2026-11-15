#!/usr/bin/env bash
# Publica la versión compilada para que Inicio la encuentre en "Buscar actualizaciones":
# copia build/Inicio.apk a dist/ y escribe update.json con la versión de app/build.gradle.
# Uso: ./build.sh && ./publicar.sh "Novedades de esta versión"   (luego git commit + git push)
set -euo pipefail
cd "$(dirname "$0")"
[ -f build/Inicio.apk ] || { echo "Falta build/Inicio.apk: ejecuta antes ./build.sh" >&2; exit 1; }
VN=$(grep -oE "versionName '[^']+'" app/build.gradle | cut -d"'" -f2)
NOTAS="${1:-}"
mkdir -p dist
cp build/Inicio.apk dist/Inicio.apk
python3 -c 'import json,sys; print(json.dumps({"version": sys.argv[1], "apk": "dist/Inicio.apk", "notas": sys.argv[2]}, ensure_ascii=False, indent=2))' "$VN" "$NOTAS" > update.json
echo "Preparada la versión $VN: dist/Inicio.apk y update.json"
