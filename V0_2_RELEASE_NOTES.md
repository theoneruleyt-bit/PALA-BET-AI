# PALA BET AI V0.2 — Incremental development package

- Keeps the same repository root layout as the successfully compiled V0.1.
- Adds BTTS and Under 2.5 probabilities in Android demo analysis.
- Adds backend fair odds, expected value, and Brier scoring primitives for offline research.
- Adds fair odds to the backend analysis response.
- Fixes the workflow to use the repository root and avoids the failing setup-android action.
- Adds tests for the new model features.

## Limitations
- Android still uses clearly labeled synthetic fixtures and calculates locally.
- The backend is not yet wired to Android; no API credentials or licensed real-time feed are included.
- No APK has been built in this environment. GitHub Actions should be used to validate compilation.
- Not a wagering or guaranteed-profit product.

## Upload
Extract ZIP; upload all extracted files to the GitHub repository root, replacing same-name files. Ensure hidden .github folder is included. Check Actions > Android Debug APK.
