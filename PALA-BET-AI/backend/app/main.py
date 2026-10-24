from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from .model import predict, elo_expected

app = FastAPI(title="PALA BET AI - Research API", version="0.1.0")
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
    return {"probabilities": p.__dict__, "elo_home_strength": elo_expected(data.home_elo, data.away_elo), "disclaimer": "Statistical research only. No guaranteed outcome.", "data_source": "user_supplied"}
