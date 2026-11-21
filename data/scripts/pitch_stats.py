"""Chiffres pour le pitch, recalculés depuis VIA (septembre 2026).

Usage (depuis la racine du repo) :
    python data/scripts/pitch_stats.py
Une ligne par incident VIA (`alea_fall_id`), en prenant le premier horodatage de chaque colonne.
Dépendances : pip install pandas openpyxl
"""
import glob
import sys
from pathlib import Path

import pandas as pd

RAW = Path(__file__).resolve().parent.parent / "raw"
COLS = ["alea_erstellungszeitpunkt", "zeitpunkt_erstpublikation",
        "zeitpunkt_erstpublikation_reiseempfehlung", "zeitpunkt_erstpublikation_bahnersatz"]


def main():
    hits = sorted(glob.glob(str(RAW / "**" / "*Ereignismeldungen[_ ]Via*.xlsx"), recursive=True))
    if not hits:
        sys.exit(f"Fichier VIA introuvable dans {RAW}")
    via = pd.read_excel(hits[0], dtype={"alea_fall_id": str})
    for c in COLS:
        via[c] = pd.to_datetime(via[c], errors="coerce")
    inc = via.groupby("alea_fall_id")[COLS].min()
    n = len(inc)

    def minutes(later, earlier):
        return ((inc[later] - inc[earlier]).dt.total_seconds() / 60).dropna()

    pub = minutes("zeitpunkt_erstpublikation", "alea_erstellungszeitpunkt")
    bus = minutes("zeitpunkt_erstpublikation_bahnersatz", "zeitpunkt_erstpublikation")
    reco = inc["zeitpunkt_erstpublikation_reiseempfehlung"].notna().sum()
    has_bus = inc["zeitpunkt_erstpublikation_bahnersatz"].notna().sum()

    print(f"Incidents VIA : {n}")
    print(f"Création → 1re publication : médiane {pub.median():.0f} min, moyenne {pub.mean():.0f} min (n={len(pub)})")
    print(f"Avec recommandation de voyage : {reco} ({reco / n:.0%})")
    print(f"Avec info bus de remplacement : {has_bus} ({has_bus / n:.0%})")
    print(f"1re publication → info bus : médiane {bus.median():.0f} min (n={len(bus)})")


if __name__ == "__main__":
    main()
