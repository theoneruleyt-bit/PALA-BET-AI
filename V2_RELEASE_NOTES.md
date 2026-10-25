# PALA AI SCORE 2.0 beta — Real UI implementation

- Five real Jetpack Compose navigation destinations: Matches, Favorites, Leagues, Analysis, Settings.
- Persistent favorite matches and display settings using Android SharedPreferences.
- Date filters Today/Tomorrow/7 days/All and team search.
- Compact card preference and explicit source visibility preference.
- Cache clearing control, loading bar, improved match cards, favorite star.
- Existing match detail tabs, goal/score/corner/card research models retained.
- No unsupported odds, lineups, logos or live feeds fabricated.

## Known limitations
- League and Analysis destinations share fixture selection flow, not full standalone analytics dashboards.
- No verified Android compile or on-device smoke test yet; this is a beta source release, not production final.
- Turkish first league, full bookmaker coverage, penalty prediction and reliable club logos are not available.
- Render deployment may be required if backend changes are made later.
