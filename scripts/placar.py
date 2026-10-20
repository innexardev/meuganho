#!/usr/bin/env python3
"""Placar de precisão do MeuGanho por dia e plataforma (dados reais do banco de estatísticas).

  scripts/placar.py              últimos 7 dias
  scripts/placar.py 2026-09-29   um dia

Mede o que importa para o motorista:
  ofertas lidas · aceites ligados a corrida · corridas concluídas · % com valor OFICIAL
  (histórico / total do dia / tela final) · canceladas · para revisar.
Horários em BRT (UTC-3).
"""
import sqlite3
import sys

DB = "/srv/innexar/production/meuganho-dist/data/db/meuganho.db"
DAY = "date(%s/1000, 'unixepoch', '-3 hours')"
OFFICIAL = ("PLATFORM_HISTORY", "PLATFORM_TOTAL", "COMPLETION_SCREEN")


def main():
    db = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    if len(sys.argv) > 1:
        days = [sys.argv[1]]
    else:
        days = [r[0] for r in db.execute(
            f"SELECT DISTINCT {DAY % 'detected_at'} d FROM offers ORDER BY d DESC LIMIT 7")]
    for day in days:
        print(f"\n=== {day} ===")
        print(f"{'':12}{'ofertas':>8}{'aceites':>8}{'corridas':>9}{'c/ oferta':>10}{'oficial':>9}{'cancel.':>8}{'revisar':>8}{'R$':>10}")
        for platform, label in (("UBER", "Uber"), ("NINETY_NINE", "99")):
            offers, accepted = db.execute(
                f"SELECT COUNT(*), SUM(outcome='ACCEPTED') FROM offers WHERE platform=? AND {DAY % 'detected_at'}=?",
                (platform, day)).fetchone()
            rows = db.execute(
                f"SELECT state, status, value_source, offer_local_id, value_cents FROM trips "
                f"WHERE platform=? AND {DAY % 'accepted_at'}=?", (platform, day)).fetchall()
            done = [r for r in rows if r[0] == "COMPLETED"]
            linked = sum(1 for r in done if r[3] is not None)
            official = sum(1 for r in done if r[2] in OFFICIAL)
            cancelled = sum(1 for r in rows if r[0] == "CANCELLED")
            review = sum(1 for r in rows if r[1] == "NEEDS_REVIEW")
            gross = sum((r[4] or 0) for r in done) / 100
            pct = lambda n: f"{n}/{len(done)}" if done else "-"
            print(f"{label:12}{offers:>8}{accepted or 0:>8}{len(done):>9}{pct(linked):>10}{pct(official):>9}"
                  f"{cancelled:>8}{review:>8}{gross:>10.2f}")


if __name__ == "__main__":
    main()
