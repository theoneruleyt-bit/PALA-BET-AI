from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from .model import predict, elo_expected, fair_odds
from .football_data import get_matches, ProviderError

app = FastAPI(title="PALA BET AI - Research API", version="0.3.0")
class MatchInput(BaseModel):
    home_xg: float = Field(ge=0.05, le=8)
    away_xg: float = Field(ge=0.05, le=8)
    home_elo: float = Field(ge=500, le=3000)
    away_elo: float = Field(ge=500, le=3000)

@app.get("/health")
def health(): return {"status": "ok", "mode": "research"}

@app.post("/analyze")
def analyze(data: MatchInput):
    p = predict(data.home_xg, data.away_xg)
    return {"probabilities": p.__dict__, "fair_odds_1x2": {"home": fair_odds(p.home), "draw": fair_odds(p.draw), "away": fair_odds(p.away)}, "elo_home_strength": elo_expected(data.home_elo, data.away_elo), "disclaimer": "Statistical research only. No guaranteed outcome.", "data_source": "user_supplied"}

@app.get("/fixtures/{competition}")
def fixtures(competition: str):
    try:
        return get_matches(competition)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid competition code")
    except ProviderError:
        raise HTTPException(status_code=503, detail="Fixture provider unavailable or not configured")
