# PALA BET AI V0.5 — keyless fixture foundation

- Redesigned dark UI with explicit text colors, readable cards, search, league selection, match details, refresh and loading/error/empty states.
- No demo teams or simulated betting probabilities on the main screen.
- Keyless OpenLigaDB integration for BL1/BL2 via backend, source provenance, cache and test coverage.
- Optional football-data.org server token remains supported for PL/PD/SA/FL1; no secret embedded in APK.
- Server URL is configured at build time as `BuildConfig.API_BASE_URL`, not typed by end users.
- One-click Render blueprint deployment configuration; see `DEPLOY_V05.md`.
- Explicitly avoids claiming validated predictive accuracy: research model remains available for user-supplied inputs via `/analyze`.

## Verification
Backend tests: 13 passed locally on 2026-10-24. Android APK has not been compiled in this environment; GitHub Actions will validate Kotlin/Gradle build after upload. Live provider and hosting have not been end-to-end tested.
