# Contexte pour l'agent : partie DATA (dossier `data/`)

Les règles du repo s'appliquent en premier : lire [`AGENTS.md`](../AGENTS.md) (une ligne dans `AGENT_LOG.md` par tâche, branche `<outil>/<zone>/<tâche>`, commits `[claude] server: …`). Ici, la zone est `server`.

## Le projet en 3 phrases
App **Ausweg** (nom provisoire), LauzHack 2026, double challenge : **SBB** (« Turn disruption data into the traveller's best next move ») + **JetBrains** (app Kotlin Multiplatform sur ≥ 2 plateformes, Android + Web). Pendant une perturbation ferroviaire, le voyageur dit où il est et où il va ; l'app lui dit quoi faire maintenant (attendre / détour / continuer) et le prévient seulement quand le conseil change. Démo principale : **rejouer minute par minute un vrai incident de septembre 2026** à partir des données SBB.

## Équipe et répartition
- A (lead) : code partagé Kotlin `commonMain` (état de la perturbation, croisement trajet × perturbation, `advise()`), fichiers Gradle, merges.
- B : écrans Compose Multiplatform.
- C : backend Ktor (sert les scénarios, horloge de replay, appels OJP).
- **D (moi, avec toi) : la data.** Transformer les données SBB brutes en scénarios propres et horodatés (`data/scenarios/*.events.json`), écrire les règles par mots-clés, sortir les chiffres du pitch.

Pipeline : données brutes → (D) règles + scénario JSON → (C) serveur « ce qu'on sait à l'instant t » → (A) `advise()` → (B) écrans.

## Les données (détails dans `data/README.md`)
Fichiers bruts dans `data/raw/` et ses sous-dossiers (**gitignoré, ne jamais committer, ne jamais copier ailleurs**) :
- **VIA** `Ereignismeldungen Via.xlsx` : 168 incidents de sept. 2026, horodatages clés (création, 1re publication, 1re reco de voyage, 1er bus, fin), lignes, codes de gares.
- **Ticker public** `incident*.csv`, `incident_audit*.csv`, `message*.csv`, `message_audit*.csv`, `reason*.csv` : ce que voyait le voyageur, version par version, en 4 langues (2024 → oct. 2026).
- **Ticker interne KULT** `1__Ereignisfaelle*.csv`, `3_liveticker_messages*.csv`, `4__Logs*.csv`, `2__Ereigniskategorien*.csv` : messages du centre de contrôle (TCC) et des gares, sept. 2026.

Liens : `VIA.alea_fall_id` = `incident.id` = `message.incident_id_fk`. KULT n'a pas l'id VIA → rapprochement par heure (± 45-60 min) + lieu. **13 incidents de sept. 2026 sont dans les 3 sources.**

Pièges : textes KULT en HTML ; heures KULT/VIA en heure locale sans fuseau, ticker public avec `+0200` ; historique public trié par `last_modified_date` (pas `version`) ; les textes non originaux sont des traductions automatiques.

## Confidentialité (règle absolue)
Le ticker interne contient des noms et numéros de téléphone d'employés (`name`, `vorname`, `telefonnummer` ; `user_name` dans `4__Logs`). **Ne jamais les lire dans une sortie, les copier, les committer ou les afficher.** Dans l'app, le contenu interne est toujours reformulé (`summary`), jamais montré brut.

## Outils
- `python data/scripts/timeline.py <VIA_ID> <KULT_ID>` : chronologie fusionnée des 3 sources (dépendances : pandas, openpyxl).
  - Puidoux–Vauderens (incendie, 25.09.2026, ligne Lausanne–Fribourg) : `2195005195853 24666` ← **scénario de démo principal**
  - Renens–Morges (panne d'alimentation, 17.09.2026, ligne Lausanne–Genève) : `2195005188970 24605`
- `python data/scripts/validate.py` : vérifie le format de tous les scénarios. À lancer après chaque modification.

## Format d'un scénario (`data/scenarios/*.events.json`)
La référence du format est la section « Format d'un scénario » de `data/README.md` (et `validate.py`). Exemple : `data/scenarios/puidoux-vauderens_2026-09-25.events.json`. Si le format doit changer, prévenir le lead (A) : c'est le contrat avec le backend et le code partagé, et mettre à jour le README et `validate.py` dans la même PR.

## Règles de travail
1. **Ne jamais inventer** une heure, un lieu, un nombre ou un bus. Chaque événement doit venir d'une ligne de `timeline.py`. En cas de doute, le dire.
2. Une tâche à la fois. Ne toucher qu'aux fichiers de `data/` sauf demande explicite. Ne jamais modifier les fichiers Gradle.
3. À la fin de chaque tâche : ce qui a été fait, **ce qui n'a pas été testé / vérifié**, et une proposition de message de commit commençant par `[claude]`.
4. Tester ce qu'on produit : `python data/scripts/validate.py` doit passer.

## Tâches de la partie data (dans l'ordre)
1. ~~`data/scenarios/puidoux-vauderens_2026-09-25.events.json` (scénario de démo)~~ fait, à valider par le lead.
2. Règles par mots-clés (DE/FR/IT) sur le ticker interne : « Bahnersatz / bus de remplacement / Bus » → bus ; « Totalunterbruch / interrompu / interruption totale » → ligne fermée ; « bis 16:00 / jusqu'à 18h30 / au minimum » → fin prévue (+ type) ; « 17h15 / 17.10 + lieu » → heure + lieu du bus. Avec tests sur de vrais messages. Emplacement (Kotlin `shared` ou `server`) à confirmer avec le lead.
3. ~~Vérifier et adapter `renens-morges_2026-09-17.events.json` (2e scénario)~~ fait : `raw` = vrai texte, événements publics manquants ajoutés.
4. Chiffres pour le pitch (sept. 2026, 168 incidents VIA) : médiane 15 min création → 1re publication ; 21 % seulement avec recommandation de voyage ; bus publié en médiane 41 min après la 1re publication ; Renens–Morges : bus connus en interne à 19:08, publiés à 20:02 (54 min).
