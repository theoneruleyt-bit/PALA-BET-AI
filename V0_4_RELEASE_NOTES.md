# PALA BET AI V0.4.0

- Android shows version V0.4.0 and Gradle versionName is 0.4.0 / versionCode 4.
- Android HTTPS backend URL input and competition selectors (PL, PD, BL1, SA, FL1).
- Fetches real fixtures from existing FastAPI /fixtures/{competition} endpoint using a background thread.
- Real fixtures are clearly separated from DEMO matches. Real fixtures do not display unsupported model probabilities.
- Network timeout, provider errors and no-data UI.
- Android INTERNET permission. No API credentials stored in Android app.
- Backend requires FOOTBALL_DATA_API_KEY in server environment; backend hosting is not included.
- GitHub Actions artifact renamed to V0.4.

## Important limitations

This is a real fixture integration, not a validated real-match prediction engine. The backend must be deployed to an HTTPS endpoint for Android to reach it. The fixture provider free plan may limit leagues and usage. Do not paste API tokens into the app.
