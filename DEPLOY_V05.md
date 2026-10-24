# PALA BET AI V0.5 — single package release guide

## What works without an API key
- The backend fetches **Bundesliga (BL1)** and **2. Bundesliga (BL2)** match schedules from the public community OpenLigaDB API. The app shows source and retrieval time. Schedule availability and accuracy are not guaranteed.
- PL, PD, SA and FL1 require an **optional server-side** football-data.org token. They are not falsely represented as keyless.
- No real-match probability is generated without validated match-specific input statistics. Existing Poisson demonstration is moved out of the default home experience.

## One-time deployment
1. Push the ZIP contents into the GitHub repository root (keep `.github`, `app`, `backend`, `render.yaml` at root).
2. In Render, create a **Blueprint** from the repository; `render.yaml` defines a Python web service. Confirm the free plan is offered before deployment. A free instance may sleep when idle; startup may be slow.
3. Visit `https://YOUR-SERVICE.onrender.com/health`, then `/fixtures/BL1` to confirm actual provider data.
4. In `app/build.gradle.kts`, set `buildConfigField("String", "API_BASE_URL", "\"https://YOUR-SERVICE.onrender.com\"")` (replace placeholder with actual URL) before building. This bakes the public **backend URL**, never a secret API key, into the app.
5. GitHub Actions builds the debug APK. For Play release, use a proper signed AAB, privacy disclosures and production testing.

## Caveats
- No host account was connected or deployed automatically; the Android app will show a clear setup message until `API_BASE_URL` is set to a real HTTPS service.
- `BL1`/`BL2` data is sourced from OpenLigaDB. Coverage varies. Backend filters upcoming fixtures for the UI; it does not claim live in-play updates.
- The backend `/analyze` endpoint is for explicitly user-supplied inputs; it is **not** a validated automated betting prediction model.
- Avoid claims of guaranteed wins or financial returns. Check local gambling and Google Play policies before publishing.
