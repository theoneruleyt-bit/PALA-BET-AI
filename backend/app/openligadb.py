"""Keyless OpenLigaDB integration. Public community data, not guaranteed live or complete."""
import json
import time
from datetime import datetime, timezone
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

API = 'https://api.openligadb.de'
# Curated shortcuts checked against OpenLigaDB's 2026 league catalogue.
# These are NOT claimed to cover every Id daa competition or every betting market.
LEAGUES = {'BL1':'bl1','BL2':'bl2','BL3':'bl3','UCL':'ucl','DFB':'dfb',
           'PLFREE':'pl','LALIGA':'la1','UEL':'uel2026','WBL':'ffb1',
           'NLA':'nla','RLN':'rln','RLNO':'rlno'}
_cache = {}

class OpenLigaError(Exception):
    pass

def get_matches(competition='BL1', *, fetch=None, now=None, ttl=300, season=None):
    if competition not in LEAGUES:
        raise ValueError('Unsupported keyless league')
    now = time.time() if now is None else now
    year = season if season is not None else (datetime.fromtimestamp(now, timezone.utc).year - (1 if datetime.fromtimestamp(now, timezone.utc).month < 7 else 0))
    key = (competition, year)
    if key in _cache and now - _cache[key][0] < ttl:
        return {**_cache[key][1], 'cache': 'hit'}
    request = Request(f'{API}/getmatchdata/{LEAGUES[competition]}/{year}', headers={'Accept':'application/json','User-Agent':'PalaBetAI/0.6'})
    try:
        if fetch is None:
            with urlopen(request, timeout=12) as response:
                raw = response.read()
        else:
            raw = fetch(request)
        data = json.loads(raw)
        if not isinstance(data, list):
            raise ValueError('Unexpected response')
        matches = []
        for m in data:
            home = (m.get('team1') or {}).get('teamName')
            away = (m.get('team2') or {}).get('teamName')
            when = m.get('matchDateTimeUTC')
            if not home or not away or not when:
                continue
            if when.endswith('Z'):
                utc = when
            else:
                utc = when + 'Z'
            matches.append({'id':str(m.get('matchID','')), 'home':home, 'away':away, 'utc_date':utc, 'status':'FINISHED' if m.get('matchIsFinished') else 'SCHEDULED', 'league':competition})
        matches.sort(key=lambda m: m['utc_date'])
    except (HTTPError, URLError, TimeoutError, ValueError, TypeError, KeyError) as exc:
        raise OpenLigaError('OpenLigaDB request failed') from exc
    result = {'source':'OpenLigaDB (community data)', 'mode':'provider', 'fetched_at':datetime.fromtimestamp(now, timezone.utc).isoformat().replace('+00:00','Z'), 'competition':competition, 'matches':matches, 'cache':'miss', 'disclaimer':'Schedules are provided by a community data service and may be delayed or incomplete. No prediction is implied.'}
    _cache[key] = (now, result)
    return result


def get_raw_matches(competition='BL1', *, fetch=None, now=None, season=None):
    """Season history for research; no prediction from unverified fixture names."""
    if competition not in LEAGUES:
        raise ValueError('Unsupported keyless league')
    now = time.time() if now is None else now
    dt = datetime.fromtimestamp(now, timezone.utc)
    year = season if season is not None else dt.year - (1 if dt.month < 7 else 0)
    request = Request(f'{API}/getmatchdata/{LEAGUES[competition]}/{year}', headers={'Accept':'application/json','User-Agent':'PalaBetAI/0.6'})
    try:
        if fetch is None:
            with urlopen(request, timeout=12) as response: raw = response.read()
        else: raw = fetch(request)
        result = json.loads(raw)
        if not isinstance(result, list): raise ValueError('Unexpected response')
        return result
    except (HTTPError, URLError, TimeoutError, ValueError, TypeError) as exc:
        raise OpenLigaError('Historical data unavailable') from exc
