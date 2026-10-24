from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from .model import predict, elo_expected, fair_odds
from .football_data import get_matches as get_football_data_matches, ProviderError
from .openligadb import get_matches as get_openliga_matches, get_raw_matches, OpenLigaError
from .analysis_v06 import normalize, estimate, backtest

app = FastAPI(title='PALA BET AI - Research API', version='0.6.0')

class MatchInput(BaseModel):
    home_xg: float = Field(ge=0.05, le=8)
    away_xg: float = Field(ge=0.05, le=8)
    home_elo: float = Field(ge=500, le=3000)
    away_elo: float = Field(ge=500, le=3000)

@app.get('/health')
def health():
    return {'status':'ok','version':'0.6.0','mode':'research','keyless_leagues':['BL1','BL2']}

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


@app.get('/research/{competition}/{match_id}')
def research(competition: str, match_id: str):
    if competition not in ('BL1','BL2'):
        raise HTTPException(status_code=400, detail='Research available for BL1 and BL2 only')
    try:
        raw = get_raw_matches(competition)
    except OpenLigaError as exc:
        raise HTTPException(status_code=503, detail='Historical provider unavailable') from exc
    target = next((m for m in raw if str(m.get('matchID')) == match_id), None)
    if target is None:
        raise HTTPException(status_code=404, detail='Match not found')
    home = (target.get('team1') or {}).get('teamName')
    away = (target.get('team2') or {}).get('teamName')
    before = target.get('matchDateTimeUTC')
    history = normalize(raw)
    result = estimate(history, home, away, before=before) if home and away and before else None
    return {'source':'OpenLigaDB (community data)', 'competition':competition, 'match_id':match_id, 'status':'estimated' if result else 'insufficient_data', 'analysis':result, 'validation':backtest(history), 'note':'Research-only estimates; past match scores are not xG. Data may be incomplete.'}
