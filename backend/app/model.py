from dataclasses import dataclass
from math import exp, factorial

@dataclass(frozen=True)
class Prediction:
    home: float
    draw: float
    away: float
    over25: float
    btts: float
    under25: float

def poisson(lam: float, goals: int) -> float:
    if not 0.05 <= lam <= 8.0 or goals < 0:
        raise ValueError("Invalid goal expectation")
    return exp(-lam) * lam**goals / factorial(goals)

def predict(home_xg: float, away_xg: float, max_goals: int = 18) -> Prediction:
    if max_goals < 8:
        raise ValueError("max_goals must be at least 8")
    h = d = a = over = btts = 0.0
    for i in range(max_goals + 1):
        for j in range(max_goals + 1):
            p = poisson(home_xg, i) * poisson(away_xg, j)
            if i > j: h += p
            elif i == j: d += p
            else: a += p
            if i + j > 2: over += p
            if i > 0 and j > 0: btts += p
    total = h + d + a
    return Prediction(h / total, d / total, a / total, over / total, btts / total, 1 - over / total)

def elo_expected(home_elo: float, away_elo: float, home_advantage: float = 65) -> float:
    return 1 / (1 + 10 ** ((away_elo - home_elo - home_advantage) / 400))


def fair_odds(probability: float) -> float:
    if not 0 < probability <= 1:
        raise ValueError("Probability must be in (0,1]")
    return 1.0 / probability

def expected_value(probability: float, decimal_odds: float) -> float:
    if not 0 <= probability <= 1 or decimal_odds < 1:
        raise ValueError("Invalid probability or odds")
    return probability * decimal_odds - 1

def brier_score(probabilities: tuple[float, float, float], outcome: int) -> float:
    if outcome not in (0, 1, 2) or len(probabilities) != 3 or any(not 0 <= x <= 1 for x in probabilities) or abs(sum(probabilities)-1)>1e-6:
        raise ValueError("Invalid probability distribution or outcome")
    return sum((p - int(i == outcome))**2 for i, p in enumerate(probabilities)) / 3
