from fastapi import FastAPI, HTTPException
from functools import lru_cache
from time import monotonic
from threading import Lock
from pydantic import BaseModel, Field
from .model import predict, elo_expected, fair_odds
from .football_data import get_matches as get_football_data_matches, ProviderError
from .openligadb import get_matches as get_openliga_matches, get_raw_matches, OpenLigaError, LEAGUES, discover_turkish_leagues, dynamic_fixtures, get_dynamic_raw
from .analysis_v06 import normalize, estimate, backtest
from .markets import extra_markets
from .turkey import fixtures as turkey_fixtures, raw_matches as turkey_raw, TurkeyFeedError
from .event_stats import get_event_estimates

FREE_LEAGUE_NAMES = {'BL1':'Bundesliga','BL2':'2. Bundesliga','BL3':'3. Liga','UCL':'Şampiyonlar Ligi','DFB':'Almanya Kupası','PLFREE':'Premier League (topluluk)','LALIGA':'La Liga (topluluk)','UEL':'Avrupa Ligi','WBL':'Kadınlar Bundesliga','NLA':'Uluslar Ligi A','RLN':'Regionalliga Nord','RLNO':'Regionalliga Nordost'}

app = FastAPI(title='PALA AI SCORE - Research API', version='1.4.0')

_RESEARCH_CACHE = {}
_RESEARCH_LOCK = Lock()
_RESEARCH_TTL = 600

def _cache_get(key):
    with _RESEARCH_LOCK:
        item = _RESEARCH_CACHE.get(key)
        if item and monotonic() - item[0] < _RESEARCH_TTL:
            return item[1]
    return None

def _cache_put(key, value):
    with _RESEARCH_LOCK:
        if len(_RESEARCH_CACHE) > 400:
            _RESEARCH_CACHE.clear()
        _RESEARCH_CACHE[key] = (monotonic(), value)
    return value


class MatchInput(BaseModel):
    home_xg: float = Field(ge=0.05, le=8)
    away_xg: float = Field(ge=0.05, le=8)
    home_elo: float = Field(ge=500, le=3000)
    away_elo: float = Field(ge=500, le=3000)

@app.get('/health')
def health():
    return {'status':'ok','version':'1.3.0','mode':'research','keyless_leagues':list(LEAGUES)}

@app.get('/leagues')
def leagues():
    return {'keyless':[{'code':'TR1','name':'Türkiye Süper Lig 2026/27','provider':'FixtureDownload'}]+[{'code':code,'name':name} for code,name in FREE_LEAGUE_NAMES.items()], 'turkish_discovery':[], 'optional_token':['PL','PD','SA','FL1'], 'note':'Public data coverage varies. Only current TR1 fixtures are curated; other leagues may be incomplete.'}

def _turkish_catalog():
    try: return discover_turkish_leagues()
    except OpenLigaError: return []

@app.post('/analyze')
def analyze(data: MatchInput):
    p = predict(data.home_xg, data.away_xg)
    return {'probabilities':p.__dict__, 'fair_odds_1x2':{'home':fair_odds(p.home),'draw':fair_odds(p.draw),'away':fair_odds(p.away)}, 'elo_home_strength':elo_expected(data.home_elo,data.away_elo), 'data_source':'user_supplied', 'disclaimer':'Research only. Input parameters are not verified match statistics. No guaranteed outcome.'}

@app.get('/fixtures/{competition}')
def fixtures(competition: str):
    try:
        if competition == 'TR1':
            return turkey_fixtures()
        if competition in LEAGUES:
            return get_openliga_matches(competition)
        if competition.startswith('TR:'):
            return dynamic_fixtures(competition)
        if competition in ('PL','PD','SA','FL1'):
            return get_football_data_matches(competition)
        raise HTTPException(status_code=400, detail='Unsupported competition')
    except (OpenLigaError, ProviderError, TurkeyFeedError) as exc:
        raise HTTPException(status_code=503, detail='Fixture provider unavailable or not configured') from exc


@app.get('/research/{competition}/{match_id}')
def research(competition: str, match_id: str):
    cached = _cache_get((competition, match_id))
    if cached is not None:
        return cached
    if competition != 'TR1' and competition not in LEAGUES and not competition.startswith('TR:'):
        raise HTTPException(status_code=400, detail='Research not available for this competition')
    try:
        raw = turkey_raw() if competition == 'TR1' else (get_dynamic_raw(competition) if competition.startswith('TR:') else get_raw_matches(competition))
    except (OpenLigaError, TurkeyFeedError) as exc:
        raise HTTPException(status_code=503, detail='Historical provider unavailable') from exc
    target = next((m for m in raw if str(m.get('matchID')) == match_id), None)
    if target is None:
        raise HTTPException(status_code=404, detail='Match not found')
    home = (target.get('team1') or {}).get('teamName')
    away = (target.get('team2') or {}).get('teamName')
    before = target.get('matchDateTimeUTC')
    history = normalize(raw)
    result = estimate(history, home, away, before=before) if home and away and before else None
    return _cache_put((competition, match_id), {'source':'FixtureDownload 2026/27' if competition == 'TR1' else 'OpenLigaDB (community data)', 'competition':competition, 'match_id':match_id, 'status':'estimated' if result else 'insufficient_data', 'analysis':result, 'markets':extra_markets(result) if result else None, 'validation':{'status':'available_on_demand','note':'Full historical backtest excluded from interactive match request to reduce latency'}, 'event_markets':get_event_estimates(home, away) if competition == 'TR1' else {'corners':{'status':'unavailable'},'yellow_cards':{'status':'unavailable'},'red_cards':{'status':'unavailable'},'penalties':{'status':'unavailable'}}, 'note':'Research-only estimates; past match scores are not xG. Data may be incomplete.'})


@app.get('/validation/{competition}')
def validation(competition: str):
    """Expensive walk-forward validation is deliberately separate from match loading."""
    if competition != 'TR1' and competition not in LEAGUES:
        raise HTTPException(status_code=400, detail='Unsupported competition')
    cached = _cache_get(('validation', competition))
    if cached is not None:
        return cached
    try:
        raw = turkey_raw() if competition == 'TR1' else get_raw_matches(competition)
    except (OpenLigaError, TurkeyFeedError) as exc:
        raise HTTPException(status_code=503, detail='Historical provider unavailable') from exc
    return _cache_put(('validation', competition), {'competition':competition,'validation':backtest(normalize(raw))})
