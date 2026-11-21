# data

- `raw/`: SBB datasets (public live ticker, VIA timetable disruptions, internal KULT ticker, service points). **Gitignored, never committed.** Each person downloads them locally.
- `seed/`: small demo seed files (e.g. a handful of community tips). Committed; must not contain personal data.
  - `seed/events.json`: disruption versions + replay scenarios the server reads (provisional format, see [`docs/api.md`](../docs/api.md)). Currently one scenario: the 25 Sep 2026 fire between Puidoux and Vauderens, paraphrased from the public live ticker.

Internal (KULT) data: names and phone numbers are stripped and the text is paraphrased before anything leaves the server.
