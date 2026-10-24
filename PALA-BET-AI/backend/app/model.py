from dataclasses import dataclass
from math import exp, factorial

@dataclass(frozen=True)
class Prediction:
    home: float
    draw: float
    away: float
    over25: float

def poisson(lam: float, goals: int) -> float:
    if not 0.05 <= lam <= 8.0 or goals < 0:
        raise ValueError("Invalid goal expectation")
    return exp(-lam) * lam**goals / factorial(goals)

def predict(home_xg: float, away_xg: float, max_goals: int = 18) -> Prediction:
    if max_goals < 8:
        raise ValueError("max_goals must be at least 8")
    h = d = a = over = 0.0
    for i in range(max_goals + 1):
        for j in range(max_goals + 1):
            p = poisson(home_xg, i) * poisson(away_xg, j)
            if i > j: h += p
            elif i == j: d += p
            else: a += p
            if i + j > 2: over += p
    total = h + d + a
    return Prediction(h / total, d / total, a / total, over / total)

def elo_expected(home_elo: float, away_elo: float, home_advantage: float = 65) -> float:
    return 1 / (1 + 10 ** ((away_elo - home_elo - home_advantage) / 400))
