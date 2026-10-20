#!/usr/bin/env python3
"""
Análises do banco de estatísticas do MeuGanho (servidor).

  scripts/insights.py                 resumo geral + melhores horários + melhores regiões
  scripts/insights.py --install ID    só uma instalação (futuro: por usuário)
  scripts/insights.py --days 30       janela de tempo (padrão: tudo)

Só ofertas confiáveis (AUTO_ACCEPTED/CONFIRMED) entram nas médias.
"""
import argparse
import sqlite3
import time

DB = "/srv/innexar/production/meuganho-dist/data/db/meuganho.db"
WEEKDAYS = {1: "seg", 2: "ter", 3: "qua", 4: "qui", 5: "sex", 6: "sáb", 7: "dom"}


def money(c):
    return "—" if c is None else f"R$ {c / 100:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install")
    ap.add_argument("--days", type=int)
    ap.add_argument("--min", type=int, default=3, help="mínimo de ofertas por grupo")
    a = ap.parse_args()

    where = ["status IN ('AUTO_ACCEPTED','CONFIRMED')", "cents_per_hour IS NOT NULL"]
    args = []
    if a.install:
        where.append("install_id = ?"); args.append(a.install)
    if a.days:
        where.append("detected_at >= ?"); args.append(int((time.time() - a.days * 86400) * 1000))
    w = " AND ".join(where)
    c = sqlite3.connect(DB)

    total, installs = c.execute(f"SELECT COUNT(*), COUNT(DISTINCT install_id) FROM offers WHERE {w}", args).fetchone()
    print(f"Ofertas confiáveis: {total} · instalações: {installs}\n")

    print("Por app:")
    for p, n, h, k, acc in c.execute(f"""SELECT platform, COUNT(*), AVG(cents_per_hour), AVG(cents_per_km),
            SUM(outcome='ACCEPTED') FROM offers WHERE {w} GROUP BY platform""", args):
        print(f"  {p:12} {n:5} ofertas · média {money(h)}/h · {money(k)}/km · aceitas {acc}")

    print("\nMelhores horários (média de R$/h das ofertas que tocaram):")
    rows = c.execute(f"""SELECT local_hour, COUNT(*), AVG(cents_per_hour), AVG(cents_per_km),
            SUM(rating='EXCELLENT') FROM offers WHERE {w} GROUP BY local_hour HAVING COUNT(*) >= ?
            ORDER BY AVG(cents_per_hour) DESC""", args + [a.min]).fetchall()
    for hour, n, h, k, exc in rows:
        print(f"  {hour:02d}h  {n:4} ofertas · {money(h)}/h · {money(k)}/km · excelentes {exc}")

    print("\nMelhores dias:")
    for d, n, h in c.execute(f"""SELECT local_weekday, COUNT(*), AVG(cents_per_hour) FROM offers WHERE {w}
            GROUP BY local_weekday HAVING COUNT(*) >= ? ORDER BY AVG(cents_per_hour) DESC""", args + [a.min]):
        print(f"  {WEEKDAYS.get(d, d)}  {n:4} ofertas · {money(h)}/h")

    print("\nMelhores regiões (célula ~1 km onde você estava quando a oferta tocou):")
    for cell, n, h, k in c.execute(f"""SELECT cell, COUNT(*), AVG(cents_per_hour), AVG(cents_per_km) FROM offers
            WHERE {w} AND cell IS NOT NULL GROUP BY cell HAVING COUNT(*) >= ? ORDER BY AVG(cents_per_hour) DESC LIMIT 15""",
                               args + [a.min]):
        print(f"  {cell}  {n:4} ofertas · {money(h)}/h · {money(k)}/km")
    no_cell = c.execute(f"SELECT COUNT(*) FROM offers WHERE {w} AND cell IS NULL", args).fetchone()[0]
    if no_cell:
        print(f"  ({no_cell} ofertas sem região: GPS do turno desligado quando tocaram)")


if __name__ == "__main__":
    main()
