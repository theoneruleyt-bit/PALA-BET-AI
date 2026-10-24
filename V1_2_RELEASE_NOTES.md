# PALA BET AI V1.2 — Current-season data & event research

## What changed
- Türkiye Süper Lig **2026/27** is now the first, dedicated league option. The historical community catalogue is removed from the selector.
- FixtureDownload 2026/27 public JSON feed parser: handles `DateUtc`, `HomeTeamScore`, `AwayTeamScore`, UTC dates, finished status, 15-minute cache, schema failures. **No sample matches are injected in production.**
- Same verified score feed supplies historical matches for the existing Poisson goal model. Predictions require enough completed matches before kickoff.
- Optional `football-data.co.uk/mmz4281/2627/T1.csv` supplies observed match corners and cards, only when actual CSV columns and at least 6 observations are present. These are preliminary Poisson estimates, not validated forecasts. Penalty event data remains unavailable.
- New Android league picker dialog, more compact navigation, Turkish league first, cleaner premium dark palette, new event-market panels, clearer source and sample labels.
- Backend tests extended to validate the real fixture JSON field names and missing-event behavior.

## Explicit limitations
- 1. Lig / 2. Lig / 3. Lig, all İddaa leagues, full live events, odds, penalty probabilities, and calibrated event forecasting are **not delivered**.
- football-data.co.uk data may be incomplete, delayed, subject to schema changes or licensing restrictions; check permissions for commercial redistribution before publication.
- Public fixtures can change or become unavailable. No betting or profit guarantees.
- APK compilation and actual Render deployment must be verified through GitHub Actions and the deployed service; backend unit tests alone are not sufficient.

## Deployment
1. Extract ZIP; copy the contents of `PALA-BET-AI-main/` into the existing repository root (not as a nested directory).
2. Commit and push. Android Debug APK and Backend Tests GitHub workflows should run.
3. Render must redeploy the updated backend for `/fixtures/TR1` and `/research/TR1/{match_id}` to work.
4. Confirm `/health` returns `1.2.0`, then test current Super Lig fixtures and the event tabs on a phone.
