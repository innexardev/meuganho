#!/usr/bin/env bash
# Simulação de turnos no servidor: gera N turnos com Uber e 99 juntos, passa pelo MeuGanho real
# (leitura, corridas, conciliador) e publica o placar em https://innexar.com.br/meuganho/sim/
#
# Uso:  scripts/simular.sh [turnos]      (padrão 50)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="${MEUGANHO_DIST:-/srv/innexar/production/meuganho-dist/public}"
SHIFTS="${1:-50}"
OUT="$ROOT/app/build/sim-report"
cd "$ROOT"
echo "▶ Simulando $SHIFTS turnos…"
status=0
./gradlew -q -PskipScreens -Psim.shifts="$SHIFTS" -Psim.out="$OUT" :app:testDebugUnitTest --tests '*ShiftSimulationTest*' || status=$?
"$ROOT/scripts/sim_publish.sh" "$OUT" "$DIST"
python3 - "$OUT/report.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
print(f"\n=== {d['shifts']} turnos ===")
for p, m in d["platforms"].items():
    print(p)
    for k, v in m.items():
        if isinstance(v, list):
            a, b = v
            print(f"  {k:34} {a}/{b}" + (f" ({a*100//b}%)" if b and 'Faturamento' not in k else ""))
    print(f"  {'Corridas que não existiram':34} {m['falseTrips']}")
PY
echo "Relatório: https://innexar.com.br/meuganho/sim/"
[[ $status -eq 0 ]] || echo "✗ A precisão ficou abaixo do piso (veja as divergências no relatório)."
exit $status
