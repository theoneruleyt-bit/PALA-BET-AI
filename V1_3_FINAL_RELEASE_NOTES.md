# PALA BET AI V1.3 – Performance and Stability Release

- Match research: single API call on Android instead of two identical requests.
- In-memory client cache (3 min fixtures, 10 min analysis), explicit refresh bypass.
- Backend research response TTL cache (10 min), bounded size.
- Shared football-data CSV parsed-event cache (15 min).
- Expensive chronological backtest removed from each match request; new `/validation/{competition}` endpoint.
- Existing verified 2026/27 TR1 fixtures, goal, corner and card models retained.
- Missing penalty data still explicitly unavailable; not fabricated.

## Deployment
1. Replace repository files with this archive, push to GitHub.
2. Wait for Android Debug APK and Backend Tests workflows to pass.
3. Deploy latest commit to Render; APK alone does not upgrade backend.
4. Test first-load vs second-load speed, TR1 fixture, goals/corners/cards and errors.

## Limits
- No Android APK is bundled: requires GitHub Actions Android build.
- Cold starts on free Render instances can still be slow.
- In-memory caches are cleared when backend restarts; cache is not distributed.
- Full all-league/all-market coverage cannot be promised with public feeds.
