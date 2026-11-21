"""Vérifie le format des scénarios data/scenarios/*.events.json (contrat avec le backend et le code partagé).

Usage (depuis la racine du repo) :
    python data/scripts/validate.py                      # tous les scénarios
    python data/scripts/validate.py data/scenarios/x.events.json
Sortie non nulle si une erreur est trouvée. Aucune dépendance.
"""
import glob
import json
import sys
from datetime import datetime
from pathlib import Path

SCENARIOS = Path(__file__).resolve().parent.parent / "scenarios"

SOURCES = {"VIA", "INTERNAL", "PUBLIC"}
KINDS = {"DETECTED", "CAUSE", "INTERRUPTION", "RESTRICTION", "SCOPE_CHANGE", "PROGNOSIS", "SEVERITY_UP",
         "REPLACEMENT_BUS", "RESTORATION", "INFO", "ENDED"}
END_TYPES = {"APPROX", "AT_LEAST", "UNKNOWN"}
CONFIDENCE = {"LOW", "MEDIUM", "HIGH"}
INCIDENT_KEYS = {"id", "kultId", "title", "line", "lineStations", "stationIds", "lines", "replayStart", "replayEnd"}
OPTIONAL_INCIDENT_KEYS = {"otherStations"}
EVENT_KEYS = {"t", "source", "kind", "section", "expectedEnd", "confidence", "bus", "firstTrains", "noAlternative",
              "cause", "summary", "raw"}
BUS_KEYS = {"status", "from", "to", "eta", "capacityPerBus", "count", "direct", "routes"}


def ts(value, where, errors):
    """Parse an ISO 8601 timestamp that must carry a UTC offset."""
    try:
        d = datetime.fromisoformat(value)
    except (TypeError, ValueError):
        errors.append(f"{where}: horodatage invalide {value!r}")
        return None
    if d.tzinfo is None:
        errors.append(f"{where}: fuseau manquant {value!r}")
        return None
    return d


def section_ok(sec, where, errors, stations):
    if not isinstance(sec, dict) or not sec.get("from") or not sec.get("to"):
        errors.append(f"{where}: section doit avoir from et to")
        return
    for end in (sec["from"], sec["to"]):
        if end not in stations:
            errors.append(f"{where}: gare {end!r} absente de lineStations / otherStations")


def check(path):
    errors = []
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    inc = data.get("incident", {})
    for k in INCIDENT_KEYS - inc.keys():
        errors.append(f"incident: clé manquante {k}")
    for k in inc.keys() - INCIDENT_KEYS - OPTIONAL_INCIDENT_KEYS:
        errors.append(f"incident: clé inconnue {k}")
    stations = set(inc.get("lineStations", [])) | set(inc.get("otherStations", []))
    ids = inc.get("stationIds", {})
    for name in stations - ids.keys():
        errors.append(f"incident.stationIds: numéro manquant pour {name!r}")
    for name, number in ids.items():
        if name not in stations:
            errors.append(f"incident.stationIds: {name!r} absente de lineStations / otherStations")
        if not (isinstance(number, int) and 8_500_000 <= number <= 8_599_999):
            errors.append(f"incident.stationIds: numéro DIDOK invalide pour {name!r} : {number!r}")
    start = ts(inc.get("replayStart"), "incident.replayStart", errors)
    end = ts(inc.get("replayEnd"), "incident.replayEnd", errors)

    prev = None
    for i, e in enumerate(data.get("events", [])):
        w = f"events[{i}] {e.get('t')}"
        for k in e.keys() - EVENT_KEYS:
            errors.append(f"{w}: clé inconnue {k}")
        t = ts(e.get("t"), w, errors)
        if t and prev and t < prev:
            errors.append(f"{w}: événements pas triés par t")
        if t and start and end and not start <= t <= end:
            errors.append(f"{w}: hors de la fenêtre de replay")
        prev = t or prev
        if e.get("source") not in SOURCES:
            errors.append(f"{w}: source inconnue {e.get('source')!r}")
        if e.get("kind") not in KINDS:
            errors.append(f"{w}: kind inconnu {e.get('kind')!r}")
        for k in ("summary", "raw"):
            if not str(e.get(k, "")).strip():
                errors.append(f"{w}: {k} vide")
        if "section" in e:
            section_ok(e["section"], w, errors, stations)
        if "expectedEnd" in e:
            ee = e["expectedEnd"]
            if ee.get("type") not in END_TYPES:
                errors.append(f"{w}: expectedEnd.type inconnu {ee.get('type')!r}")
            if ee.get("time") is not None:
                ts(ee["time"], f"{w} expectedEnd.time", errors)
            elif ee.get("type") != "UNKNOWN":
                errors.append(f"{w}: expectedEnd.time requis sauf si type UNKNOWN")
        if "confidence" in e and e["confidence"] not in CONFIDENCE:
            errors.append(f"{w}: confidence inconnue {e['confidence']!r}")
        if "bus" in e:
            bus = e["bus"]
            for k in bus.keys() - BUS_KEYS:
                errors.append(f"{w}: bus.{k} inconnu")
            for side in ("from", "to"):
                if bus.get(side) and bus[side] not in stations:
                    errors.append(f"{w}: bus.{side} {bus[side]!r} absente de lineStations / otherStations")
            if bus.get("eta") is not None:
                ts(bus["eta"], f"{w} bus.eta", errors)
            for j, r in enumerate(bus.get("routes", [])):
                section_ok(r, f"{w} bus.routes[{j}]", errors, stations)
    return errors


def main():
    paths = sys.argv[1:] or sorted(glob.glob(str(SCENARIOS / "*.events.json")))
    failed = False
    for p in paths:
        errors = check(p)
        print(f"{'OK ' if not errors else 'ERR'} {Path(p).name}")
        for err in errors:
            print("   ", err)
        failed = failed or bool(errors)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
