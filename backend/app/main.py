from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from .model import predict, elo_expected, fair_odds
from .football_data import get_matches as get_football_data_matches, ProviderError
from .openligadb import get_matches as get_openliga_matches, OpenLigaError

app = FastAPI(title='PALA BET AI - Research API', version='0.5.0')

class MatchInput(BaseModel):
    home_xg: float = Field(ge=0.05, le=8)
    away_xg: float = Field(ge=0.05, le=8)
    home_elo: float = Field(ge=500, le=3000)
    away_elo: float = Field(ge=500, le=3000)

@app.get('/health')
def health():
    return {'status':'ok','version':'0.5.0','mode':'research','keyless_leagues':['BL1','BL2']}

@app.get('/leagues')
def leagues():
    return {'keyless':[{'code':'BL1','name':'Bundesliga'},{'code':'BL2','name':'2. Bundesliga'}], 'optional_token':['PL','PD','SA','FL1'], 'note':'Optional leagues require server-side FOOTBALL_DATA_API_KEY.'}

@app.post('/analyze')
def analyze(data: MatchInput):
    p = predict(data.home_xg, data.away_xg)
    return {'probabilities':p.__dict__, 'fair_odds_1x2':{'home':fair_odds(p.home),'draw':fair_odds(p.draw),'away':fair_odds(p.away)}, 'elo_home_strength':elo_expected(data.home_elo,data.away_elo), 'data_source':'user_supplied', 'disclaimer':'Research only. Input parameters are not verified match statistics. No guaranteed outcome.'}

@app.get('/fixtures/{competition}')
def fixtures(competition: str):
    try:
        if competition in ('BL1','BL2'):
            return get_openliga_matches(competition)
        if competition in ('PL','PD','SA','FL1'):
            return get_football_data_matches(competition)
        raise HTTPException(status_code=400, detail='Unsupported competition')
    except (OpenLigaError, ProviderError) as exc:
        raise HTTPException(status_code=503, detail='Fixture provider unavailable or not configured') from exc
