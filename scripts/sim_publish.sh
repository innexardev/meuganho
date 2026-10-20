#!/usr/bin/env bash
# Copia o relatório da simulação para a página pública (/meuganho/sim/) e guarda o histórico do placar.
set -euo pipefail
OUT="$1"; DIST="$2"
[[ -f "$OUT/report.json" ]] || { echo "Sem relatório em $OUT" >&2; exit 0; }
mkdir -p "$DIST/sim"
VERSION=$(grep -oP 'versionName = "\K[^"]+' "$(dirname "$0")/../app/build.gradle.kts")
python3 - "$OUT/report.json" "$DIST/sim/history.json" "$VERSION" <<'PY'
import json, os, sys
report, hist_path, version = sys.argv[1:]
d = json.load(open(report))
h = json.load(open(hist_path)) if os.path.exists(hist_path) else []
h.insert(0, {"version": version, "at": d["generatedAt"], "shifts": d["shifts"], "platforms": d["platforms"]})
json.dump(h[:100], open(hist_path + ".tmp", "w"), ensure_ascii=False)
os.replace(hist_path + ".tmp", hist_path)
PY
cp "$OUT/report.json" "$DIST/sim/report.json.tmp" && mv "$DIST/sim/report.json.tmp" "$DIST/sim/report.json"
cp "$OUT/index.html" "$DIST/sim/index.html.tmp" && mv "$DIST/sim/index.html.tmp" "$DIST/sim/index.html"
chmod -R a+rX "$DIST/sim"
