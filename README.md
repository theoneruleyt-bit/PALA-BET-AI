# PALA BET AI V0.4

Android (Jetpack Compose) + FastAPI research backend.

## Android
Build via `.github/workflows/android-build.yml` on GitHub Actions. Debug APK artifact: `PALA-BET-AI-V0.4-APK`.

## Backend (server)
```bash
cd backend
python -m pip install -r requirements.txt
export FOOTBALL_DATA_API_KEY=YOUR_TOKEN
uvicorn app.main:app --host 0.0.0.0 --port 8000
```
For Windows CMD use `set FOOTBALL_DATA_API_KEY=YOUR_TOKEN`. Expose backend through an HTTPS host before entering its public base URL into the Android app. Never commit API tokens to GitHub.

`GET /health` checks status. `GET /fixtures/PL` returns real scheduled fixtures from football-data.org if the provider is configured. Demo predictions are separate from real fixtures.

See `V0_4_RELEASE_NOTES.md` for limits.
