"""Transparent, conservative estimates from completed OpenLigaDB fixtures.

Not xG, not bookmaker advice. Never use future games to fit a prediction.
"""
from collections import defaultdict
from datetime import datetime, timezone
from .model import predict, brier_score

MIN_GAMES = 5

def _goals(raw):
    scores = [r for r in raw.get('matchResults', []) if r.get('resultTypeID') == 2]
    if not scores:
        scores = [r for r in raw.get('matchResults', []) if r.get('resultOrderID') == 1]
    if not scores:
        return None
    r = scores[-1]
    a, b = r.get('pointsTeam1'), r.get('pointsTeam2')
    if not isinstance(a, int) or not isinstance(b, int) or a < 0 or b < 0:
        return None
    return a, b

def normalize(raw):
    out = []
    for m in raw:
        if not m.get('matchIsFinished'):
            continue
        home, away = (m.get('team1') or {}).get('teamName'), (m.get('team2') or {}).get('teamName')
        score, when = _goals(m), m.get('matchDateTimeUTC')
        if home and away and home != away and score and when:
            out.append({'home': home, 'away': away, 'hg': score[0], 'ag': score[1], 'utc': when})
    return sorted(out, key=lambda x: x['utc'])

def estimate(history, home, away, *, before=None):
    if before is not None:
        history = [m for m in history if m['utc'] < before]
    h = [m for m in history if m['home'] == home or m['away'] == home][-10:]
    a = [m for m in history if m['home'] == away or m['away'] == away][-10:]
    if len(h) < MIN_GAMES or len(a) < MIN_GAMES:
        return None
    def scored(m, team):
        return (m['hg'], m['ag']) if m['home'] == team else (m['ag'], m['hg'])
    hs = [scored(m, home) for m in h]
    aws = [scored(m, away) for m in a]
    # Empirical goal rates with conservative league-neutral prior; these are not xG.
    h_for = (sum(x for x, _ in hs) + 3 * 1.35) / (len(hs) + 3)
    h_against = (sum(y for _, y in hs) + 3 * 1.35) / (len(hs) + 3)
    a_for = (sum(x for x, _ in aws) + 3 * 1.20) / (len(aws) + 3)
    a_against = (sum(y for _, y in aws) + 3 * 1.20) / (len(aws) + 3)
    lam_h = max(.15, min(5., (h_for + a_against) / 2 * 1.06))
    lam_a = max(.15, min(5., (a_for + h_against) / 2 * .94))
    p = predict(lam_h, lam_a)
    return {'probabilities': {'home': p.home, 'draw': p.draw, 'away': p.away, 'over25': p.over25, 'under25': p.under25, 'btts': p.btts}, 'goal_rates': {'home': round(lam_h, 3), 'away': round(lam_a, 3)}, 'sample': {'home': len(h), 'away': len(a)}, 'method': 'Poisson + recent goals, shrinkage; not xG', 'disclaimer': 'Exploratory estimates, not validated predictions or guaranteed betting outcomes.'}

def backtest(history):
    scores = []
    for m in history:
        p = estimate(history, m['home'], m['away'], before=m['utc'])
        if p is None:
            continue
        probs = p['probabilities']
        outcome = 0 if m['hg'] > m['ag'] else (1 if m['hg'] == m['ag'] else 2)
        scores.append(brier_score((probs['home'], probs['draw'], probs['away']), outcome))
    return {'evaluated_matches': len(scores), 'brier_mean': round(sum(scores) / len(scores), 5) if scores else None, 'method': 'chronological walk-forward, no future results', 'note': 'Exploratory; not proof of calibrated or profitable forecasts.'}
