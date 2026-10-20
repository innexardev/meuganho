#!/usr/bin/env python3
"""
Análise das amostras de calibração recebidas em innexar.com.br/meuganho/api/samples.

  scripts/samples.py                      resumo (por dia, plataforma, tipo, motivo)
  scripts/samples.py list [filtro]        lista amostras (filtro: UBER, OFFER, UNKNOWN, NEEDS_REVIEW…)
  scripts/samples.py show N               texto + parse da amostra N (índice da listagem)
  scripts/samples.py fixture N nome       grava a amostra N como fixture de teste
"""
import json
import sys
from collections import Counter
from pathlib import Path

SAMPLES = Path("/srv/innexar/production/meuganho-dist/data/samples")
FIXTURES = Path(__file__).resolve().parent.parent / "app/src/test/resources/fixtures"


def load():
    rows = []
    for f in sorted(SAMPLES.glob("*/*.jsonl")):
        for line in f.read_text(encoding="utf-8").splitlines():
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                pass
    return sorted(rows, key=lambda r: r.get("capturedAt", 0))


def label(i, r):
    parse = r.get("parse", "")
    return f"[{i}] {r.get('platform')} {r.get('screenType')} {r.get('reason')} app={r.get('sourceAppVersion')} {parse[:110]}"


def main(argv):
    rows = load()
    cmd = argv[1] if len(argv) > 1 else "summary"
    if cmd == "summary":
        print(f"{len(rows)} amostras em {SAMPLES}")
        for key in ("platform", "screenType", "reason", "appVersionName", "device"):
            print(f"\n{key}:")
            for k, n in Counter(str(r.get(key)) for r in rows).most_common():
                print(f"  {n:5}  {k}")
        review = [r for r in rows if '"status":"NEEDS_REVIEW"' in r.get("parse", "") or '"status":"INVALID"' in r.get("parse", "")]
        print(f"\nofertas em revisão/inválidas: {len(review)}  ·  telas desconhecidas: {sum(r.get('reason') == 'UNKNOWN' for r in rows)}")
    elif cmd == "list":
        flt = argv[2] if len(argv) > 2 else ""
        for i, r in enumerate(rows):
            if flt in json.dumps(r, ensure_ascii=False):
                print(label(i, r))
    elif cmd == "show":
        r = rows[int(argv[2])]
        print(label(int(argv[2]), r))
        print(r.get("text", ""))
    elif cmd == "fixture":
        i, name = int(argv[2]), argv[3]
        r = rows[i]
        folder = "uber" if r["platform"] == "UBER" else "ninetynine"
        out = FIXTURES / folder / f"{name}.txt"
        out.write_text(f"# Amostra real {r['screenType']} ({r['reason']}) · app {r.get('sourceAppVersion')} · {r.get('parserVersion')}\n"
                       f"# parse V1: {r.get('parse')}\n{r['text']}\n", encoding="utf-8")
        print(f"fixture gravada: {out}")
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv)
