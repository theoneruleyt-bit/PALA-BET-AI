# PALA BET AI V0.6 — research estimates

- BL1/BL2 fixture details can request conservative estimates from completed OpenLigaDB results.
- At least five completed matches for each team are required; no fabricated predictions.
- Goal averages with shrinkage feed Poisson 1X2, BTTS, and over/under 2.5.
- Chronological walk-forward Brier diagnostic (not proof of calibration).
- Missing history is explicitly marked insufficient; no xG or lineup claims.
- Backend HTTPS URL preconfigured for the existing Render service.

## Limits
The model uses same-season community score data only. It does not include xG, Elo rating training, lineups, injuries, bookmaker odds, or a statistically validated betting edge. A production-grade prediction engine requires multi-season data, out-of-sample calibration and model monitoring. Render deploys on GitHub push; test `/health` and `/research/BL1/<real-match-id>` after deploy.
