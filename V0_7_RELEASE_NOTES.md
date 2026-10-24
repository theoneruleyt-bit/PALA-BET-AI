# PALA BET AI V0.7 — Premium UI

- Replaces plain text match analysis with a premium dark match header, team monogram placeholders, tab navigation, probability tiles, and visual bars.
- Real backend probabilities only: no invented model confidence scores, Elo ratings, lineups, logos, venues, or xG.
- Tabs: Analysis, Statistics, Predictions, Lineups and H2H; unavailable data is explicitly labeled.
- Keeps OpenLigaDB fixtures and V0.6 Poisson research endpoint.
- Backend and tests are carried forward unchanged from V0.6.
- BuildConfig.VERSION_NAME is displayed dynamically; versionCode 7/versionName 0.7.0.

## Testing

Run GitHub Actions Android Debug APK and Backend Tests. Then install the new APK and open a Bundesliga match. Verify real values against /research/BL1/{match_id}, check small-screen scrolling, and test unavailable tabs.

## Notes

Team badges are initials, not official logos. No unverified stadium or lineup is shown. Free Render instances may take time to wake up. This package has not been Android-compiled in this environment.
