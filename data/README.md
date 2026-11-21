# data : données SBB, scénarios de replay, scripts

| Dossier | Contenu | Committé ? |
|---|---|---|
| `raw/` | Jeux de données SBB bruts, chacun les télécharge en local (`raw/Ereignismeldungen/`, `raw/extern-liveticker/`, `raw/intern-liveticker/`) | **Non** (gitignoré, ne jamais committer) |
| `scenarios/` | Un `*.events.json` par incident rejoué (format ci-dessous) | Oui |
| `scripts/` | `timeline.py` (chronologie fusionnée), `validate.py` (vérifie les scénarios) | Oui |
| `seed/` | Petits fichiers de démo (ex. quelques astuces communautaires). Sans données personnelles | Oui |

**Slides du challenge SBB** (`data/sbb_lauzhack.pdf`, gitignoré : document interne SBB « C2 - Intern », chacun le garde en local). L'essentiel :
- Répondre à « qu'est-ce que je fais maintenant ? » pour **un trajet précis** (où va le voyageur, comment la perturbation le touche, quoi faire).
- Fonctionner avec une information **incomplète**, **reconnaître les mises à jour utiles**, **adapter** la recommandation et **prévenir au bon moment**.
- Les horodatages de publication sont dans les données ; combiner les 3 sources (ticker public, ticker interne, infos perturbation de l'horaire / VIA).
- Livrable : un prototype fonctionnel et présentable ; canal et expérience libres.

**Données internes (KULT)** : les noms, prénoms et numéros de téléphone (`name`, `vorname`, `telefonnummer`, `user_name` dans les logs) ne sont jamais lus, copiés ni committés. Le texte interne est reformulé (`summary`) avant de quitter le serveur ; `raw` sert seulement à vérifier et n'est jamais envoyé à l'app.

## Les 3 sources et comment elles se relient
| Source | Fichiers | Période | Ce qu'elle apporte |
|---|---|---|---|
| **VIA** (système amont) | `Ereignismeldungen Via.xlsx` (2 710 lignes, 168 incidents) | sept. 2026 | Horodatages clés : création, première publication, 1re recommandation de voyage, 1er bus de remplacement, fin. Lignes touchées, codes de gares (`stoerungs_ort1/2`) |
| **Live ticker public** | `incident*.csv` (523), `incident_audit` (4 782 versions), `message*.csv` (3 234), `message_audit` (4 722), `reason` (93) | mars 2024 → oct. 2026 | Ce que le voyageur voyait, version par version : résumé, description, fin prévue (`duration_type` + `duration_to`), messages en 4 langues. Détails : `raw/extern-liveticker/README.md` |
| **KULT, ticker interne** | `1__Ereignisfaelle` (137), `3_liveticker_messages` (882), `4__Logs` (4 372), `2__Ereigniskategorien` | sept. 2026 | Ce que le centre de contrôle (TCC) et les gares se disaient : trains rebroussés/supprimés, bus commandés, pronostics. Détails : `raw/intern-liveticker/README-intern.md` |

**Liens :**
- `VIA.alea_fall_id` = `incident.id` (public) = `message.incident_id_fk`.
- KULT n'a **pas** l'id VIA. On relie un `Ereignisfall` à un incident VIA par l'heure (±45 min) + le lieu (`zug_nr_strecke`).
- **13 incidents de septembre 2026 sont dans les 3 sources.** Ce sont nos candidats pour la démo.
- Historique public : `incident_audit` + `incident`, triés par `last_modified_date` (pas par `version`, qui n'est pas unique).

**Pièges :** textes KULT en HTML (`&gt;`, `<span>`) ; heures KULT/VIA en heure locale sans fuseau, public avec `+0200` ; les textes non originaux sont des traductions automatiques (`*_translated = true`) ; le liveticker public ne garde pas les toutes premières versions VIA (ex. Puidoux : publié dans VIA à 16:07, première version du ticker à 16:27).

## Scripts
```bash
pip install pandas openpyxl
python data/scripts/timeline.py <VIA_ID> <KULT_ID>   # chronologie fusionnée VIA + interne + public
python data/scripts/validate.py                      # vérifie tous les scenarios/*.events.json
python data/scripts/pitch_stats.py                   # chiffres du pitch depuis VIA
```

## Chiffres pour le pitch (168 incidents VIA, septembre 2026)
Recalculés avec `python data/scripts/pitch_stats.py` (une ligne par incident VIA, premier horodatage de chaque colonne) :
- Médiane de **15 min** entre la création de l'incident et la première publication (moyenne 32 min).
- Seuls **21 %** des incidents ont reçu une **recommandation de voyage**, et 18 % une info bus de remplacement.
- Quand un bus de remplacement est publié, c'est en médiane **41 min** après la première publication.

## Cas de démo n°1 : incendie Puidoux – Vauderens, ven. 25.09.2026, 16:03 (ligne Lausanne – Fribourg)
Scénario de démo principal. Chronologie : `python data/scripts/timeline.py 2195005195853 24666`. Événements : `scenarios/puidoux-vauderens_2026-09-25.events.json`.

Plutôt bien communiqué (8 messages publics), mais avec des écarts :
| Fait | Connu en interne | Publié | Écart |
|---|---|---|---|
| 4 bus de 150 places au départ de Palézieux, en gare à 17:15 | 16:45 | 16:59 : « des bus arrivent mais capacité insuffisante, reportez votre voyage » | L'heure et le lieu des bus ne sont jamais publiés |
| Pronostic | 18:00 (16:43, maintenu à 18:00) | 18:00 → 18:45 (17:46) → « provisoire 18:30 » (17:52) | Trois pronostics différents en 6 minutes côté public |
| Courant rétabli | 17:56 | 18:09 | 14 min |

Bonus : à 17:29, le personnel en gare de Lausanne demande au TCC d'où partent les bus de remplacement. Cause publiée à 16:54 : un oiseau a provoqué un court-circuit.

## Cas de démo n°2 : panne Renens VD – Morges, jeu. 17.09.2026, 17:22 (heure de pointe, axe Lausanne – Genève)
Chronologie : `python data/scripts/timeline.py 2195005188970 24605`. Événements : `scenarios/renens-morges_2026-09-17.events.json`.

| Fait | Connu en interne | Publié | Écart |
|---|---|---|---|
| « Au minimum jusqu'à 18:30 », aucune possibilité de rejoindre Genève | 17:44 | Public : « jusqu'à 18:30 **environ** » (17:43) | Le public a l'air plus optimiste que l'interne |
| Pronostic de reprise **inconnu**, interruption totale en préparation | 18:19 | Fin prévue repoussée à 20:30 à 18:30 | 11 min, et l'incertitude n'est jamais dite |
| Bus Renens – Morges sur place à **19:30** | 19:08 | 20:02 | **54 min** : les bus roulaient déjà depuis 30 min |
| Impossible de voyager entre Allaman et Renens | 19:07 | Jamais dit tel quel | Le public conseille des détours via Berne/Payerne (18:57, 19:03), rien pour Lausanne – Genève |
| Premiers trains Lausanne – Genève | 21:04 | 21:05 (restriction) | Quasi rien |

Bonus : à 20:38, quelqu'un en gare écrit au TCC « écrivez si c'est le chaos total mais COMMUNIQUEZ!!! ». Même le personnel manquait d'information.

## Format d'un scénario (`scenarios/*.events.json`)
C'est le contrat avec le backend (C) et le code partagé (A) : tout changement se discute avec le lead. `validate.py` vérifie ce format.

**`incident`** : `id` (id VIA), `kultId`, `title`, `line`, `lineStations` (toutes les gares de la ligne, dans l'ordre), `otherStations` (optionnel : gares hors de la ligne citées dans les événements, ex. un embranchement), `stationIds` (numéro DIDOK de chaque gare de `lineStations` et `otherStations`, ex. `"Morges": 8501037` : c'est le même numéro que `StopRef.didok` côté OJP et que les colonnes VIA `stoerungs_ort*`, donc **le croisement trajet × perturbation se fait sur ce numéro**, pas sur le nom), `lines` (IC1, R8…), `replayStart`, `replayEnd`.

**Noms de gares** : toujours le nom officiel SBB, identique à OJP (vérifié avec `https://transport.opendata.ch/v1/locations?query=…`, mêmes numéros que les colonnes VIA `stoerungs_ort*`), ex. `Romont FR`, `Fribourg/Freiburg`. `lineStations` vient de la liste d'arrêts d'un train omnibus (Puidoux : S41 ; Renens : R9 + RE33 + RL2). Dans `sections`, `bus` et `bus.routes`, chaque gare doit être dans `lineStations` ou `otherStations` (`validate.py` le vérifie). Les `summary` peuvent utiliser les noms courts.

**`events[]`**, triés par `t` :
| Champ | Obligatoire | Valeurs |
|---|---|---|
| `t` | oui | ISO 8601 avec fuseau (`+02:00` en septembre) |
| `source` | oui | `VIA` \| `INTERNAL` \| `PUBLIC` |
| `kind` | oui | `DETECTED` \| `CAUSE` \| `INTERRUPTION` \| `RESTRICTION` \| `SCOPE_CHANGE` \| `PROGNOSIS` \| `SEVERITY_UP` \| `REPLACEMENT_BUS` \| `RESTORATION` \| `INFO` \| `ENDED` |
| `summary` | oui | Phrase claire en français pour le voyageur |
| `raw` | oui | Texte d'origine (ou nom de la colonne VIA), pour vérifier. Jamais envoyé à l'app |
| `sections` | non | Liste non vide de `{from, to}` : tronçons touchés (ex. Puidoux – Vauderens **et** Puidoux – Lucens). Une seule entrée si le texte ne cite qu'un tronçon |
| `expectedEnd` | non | `{time, type}`, `type` = `APPROX` \| `AT_LEAST` \| `UNKNOWN` ; `time` = `null` seulement si `UNKNOWN` |
| `confidence` | non | `LOW` \| `MEDIUM` \| `HIGH` (seulement si le texte le dit : « provisoire », « au minimum »…) |
| `cause` | non | Cause en clair (événements `CAUSE`) |
| `bus` | non | `{status, from, to, eta, capacityPerBus, count, direct, routes}` : `status` = `ORDERED` \| `SCHEDULED` \| `PUBLISHED` ; `eta` = heure à laquelle le bus est sur place ; `count` = nombre de bus ; `direct` = nombre de bus directs ; `routes` = liste de `{from, to}` quand les bus vont vers plusieurs gares |
| `firstTrains` | non | `{"<départ> -> <arrivée>": ["<n° de train>", …]}` |
| `noAlternative` | non | `true` si le texte dit qu'il n'y a aucune alternative |

## Esquisse Kotlin (`commonMain`)
Indicatif : le modèle et `advise()` appartiennent au lead (A).
```kotlin
@Serializable data class Section(val from: String, val to: String)
@Serializable data class ExpectedEnd(val time: Instant? = null, val type: EndType)
enum class EndType { APPROX, AT_LEAST, UNKNOWN }
@Serializable data class Event(
    val t: Instant, val source: Source, val kind: Kind,
    val sections: List<Section> = emptyList(), val expectedEnd: ExpectedEnd? = null,
    val confidence: Confidence? = null, val bus: Bus? = null,
    val summary: String,            // pas de `raw` : le serveur le retire avant d'envoyer à l'app
)

// Le cœur : (trajet, événements connus à l'instant t) -> recommandation.
// Pas de texte ici : l'UI construit la phrase depuis les clés i18n (AGENTS.md : pas de texte en dur).
fun advise(trip: Trip, known: List<Event>, now: Instant): Advice
data class Advice(
    val action: Action,             // WAIT, TAKE_BUS, REROUTE, POSTPONE, GO_AS_PLANNED
    val params: AdviceParams,       // ex. TAKE_BUS -> bus Palézieux → Romont FR, eta 17:15
    val confidence: Confidence,
    val changedSinceLast: Boolean,  // -> notifier seulement si true
)
data class AdviceParams(
    val bus: Bus? = null,           // from, to, eta
    val sections: List<Section> = emptyList(),
    val expectedEnd: ExpectedEnd? = null,
)
```
