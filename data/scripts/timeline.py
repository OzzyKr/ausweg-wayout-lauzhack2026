"""Chronologie fusionnée d'un incident SBB (VIA + ticker interne KULT + ticker public).

Usage (depuis la racine du repo) :
    python data/scripts/timeline.py <VIA_ID> <KULT_ID>
    python data/scripts/timeline.py 2195005195853 24666   # incendie Puidoux–Vauderens, 25.09.2026
    python data/scripts/timeline.py 2195005188970 24605   # panne Renens–Morges, 17.09.2026

Les fichiers bruts doivent être dans data/raw/ ou ses sous-dossiers (noms d'origine, avec ou sans préfixe).
Dépendances : pip install pandas openpyxl
"""
import glob
import html
import re
import sys
from pathlib import Path

import pandas as pd

RAW = Path(__file__).resolve().parent.parent / "raw"


def find(pattern: str) -> str:
    hits = sorted(glob.glob(str(RAW / "**" / pattern), recursive=True))
    if not hits:
        sys.exit(f"Fichier introuvable dans {RAW} : {pattern}")
    return hits[0]


def clean(s) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", str(s)))).strip()


def local(series):
    return pd.to_datetime(series, utc=True, format="mixed").dt.tz_convert("Europe/Zurich").dt.tz_localize(None)


def load():
    inc = pd.read_csv(find("*incident_2*.csv"), dtype={"id": str})
    ia = pd.read_csv(find("*incident_audit_*.csv"), dtype={"id": str}, low_memory=False)
    msg = pd.read_csv(find("*message_2*.csv"), dtype={"incident_id_fk": str})
    ma = pd.read_csv(find("*message_audit_*.csv"), dtype={"incident_id_fk": str})
    for d in (inc, ia, msg, ma):
        for c in ("created_date", "last_modified_date"):
            d[c] = local(d[c])
    via = pd.read_excel(find("*Ereignismeldungen[_ ]Via*.xlsx"), dtype={"alea_fall_id": str})
    lt = pd.read_csv(find("*iveticker_messages*.csv"), parse_dates=["timestamp"])
    return inc, ia, msg, ma, via, lt


def timeline(via_id: str, kult_id: int):
    inc, ia, msg, ma, via, lt = load()
    ev = []

    v = via[via.alea_fall_id == via_id]
    for c in ["alea_erstellungszeitpunkt", "alea_zeitpunkt_eroeffnung_zugliste", "zeitpunkt_inbearbeitungnahme",
              "zeitpunkt_erstpublikation", "zeitpunkt_erstpublikation_reiseempfehlung",
              "zeitpunkt_erstpublikation_bahnersatz"]:
        vals = v[c].dropna()
        if len(vals):
            ev.append((pd.to_datetime(vals.min()), "VIA", c))
    end = v.zeitpunkt_globale_endmeldung.dropna()
    if len(end):
        ev.append((pd.to_datetime(end.max()), "VIA", "globale_endmeldung"))

    # Ticker interne : on n'affiche jamais name / vorname / telefonnummer
    for _, r in lt[lt.ereignisfall_id == kult_id].iterrows():
        ev.append((r.timestamp, "INTERNE/" + str(r.sender), clean(r.message)))

    versions = pd.concat([ia[ia.id == via_id], inc[inc.id == via_id]]).sort_values("last_modified_date")
    prev = None
    for _, r in versions.iterrows():
        key = (r.summary_fr, r.duration_type, str(r.duration_to), str(r.duration_at_least), r.lifecycle, r.description_fr)
        if key != prev:
            t = pd.to_datetime(r.updated_at_source, utc=True).tz_convert("Europe/Zurich").tz_localize(None)
            ev.append((t, "PUBLIC/incident",
                       f"[{r.lifecycle}] {r.summary_fr} | dur={r.duration_type} to={r.duration_to} "
                       f"atleast={r.duration_at_least} | {clean(r.description_fr)[:300]}"))
        prev = key

    msgs = pd.concat([ma[ma.incident_id_fk == via_id], msg[msg.incident_id_fk == via_id]]).sort_values("last_modified_date")
    seen = set()
    for _, r in msgs.iterrows():
        k = (r.id, r.title_fr, r.message_fr, r.status)
        if k in seen:
            continue
        seen.add(k)
        ev.append((r.last_modified_date, "PUBLIC/msg", f"[{r.status}] {r.title_fr}: {clean(r.message_fr)[:300]}"))

    for t, s, x in sorted(ev, key=lambda e: e[0]):
        print(t.strftime("%d %H:%M:%S"), f"{s:<22}", x)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    timeline(sys.argv[1], int(sys.argv[2]))
