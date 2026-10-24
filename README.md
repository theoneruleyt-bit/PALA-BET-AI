# PALA BET AI V0.3 — Provider Foundation

- Android versionCode 3, versionName 0.3.0; explicit DEMO data provenance on analysis screen.
- Backend `GET /fixtures/{competition}` with optional football-data.org API integration.
- Backend API key is read only from `FOOTBALL_DATA_API_KEY` environment variable, never shipped in the APK.
- 5-minute in-process cache and controlled provider failure responses.
- Provider adapter unit tests using mock transport (no network needed).
- Updated Android GitHub Actions artifact name.

**Scope limit:** The Android app still uses demo fixtures and on-device Poisson calculations. The backend endpoint is NOT yet connected to the Android UI. No claim of live odds or verified predictive accuracy.

**Repo cleanup:** The repository currently contains both root `app/`, `backend/` and a nested `PALA-BET-AI/` copy. Upload the ZIP contents into repository ROOT, then remove only the nested `PALA-BET-AI/` folder after verifying it is an obsolete duplicate. Do not delete root `app/`, `backend/` or `.github/`.

**Provider setup:** Configure `FOOTBALL_DATA_API_KEY` only on the backend server environment. Do not commit secrets or insert them in Android code. No paid plan required for offline demo mode.
