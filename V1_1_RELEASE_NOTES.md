# PALA BET AI V1.1 Free — expanded UI and verified-data safeguards

- Dynamic Turkey league discovery from the OpenLigaDB public catalogue. **No Turkish league is guaranteed**: only entries actually returned by the provider appear. Provider can be empty or outdated.
- Turkish fixtures/research resolve against the live catalogue; no fabricated shortcuts.
- Expanded Android tabs: goal lines, team goals, top correct scores, corners, cards, penalties.
- Corners/cards/penalties explicitly unavailable until event-level verified history exists; never invent probabilities.
- Retains V1 goal model and honest data limitations.
- Backend tests should be run with `cd backend && python -m pytest -q`; Android build with GitHub Actions.
- Not Play Store ready. No verified live Turkish coverage, cards/corners/penalties, or full İddaa market coverage.
