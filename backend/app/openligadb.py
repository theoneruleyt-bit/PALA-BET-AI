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


# Discover community-maintained Turkish leagues without guessing league shortcuts.
# Only entries returned by OpenLigaDB are eligible; coverage is not guaranteed.
def discover_turkish_leagues(*, fetch=None):
    request = Request(f'{API}/getavailableleagues', headers={'Accept':'application/json','User-Agent':'PalaBetAI/1.1'})
    try:
        if fetch is None:
            with urlopen(request, timeout=12) as response: raw = response.read()
        else: raw = fetch(request)
        entries = json.loads(raw)
        if not isinstance(entries, list): raise ValueError('Unexpected league catalogue')
    except (HTTPError, URLError, TimeoutError, ValueError, TypeError) as exc:
        raise OpenLigaError('League catalogue unavailable') from exc
    terms = ('türkei', 'tuerkei', 'turkey', 'türkiye', 'turkiye', 'süper lig', 'super lig', 'tff 1.', 'tff 2.', 'tff 3.')
    found = {}
    for entry in entries:
        if not isinstance(entry, dict): continue
        name = str(entry.get('leagueName') or '')
        shortcut = str(entry.get('leagueShortcut') or '')
        season = str(entry.get('leagueSeason') or '')
        if any(term in name.casefold() for term in terms) and shortcut and season.isdigit():
            key = (shortcut, season)
            found[key] = {'code': 'TR:'+shortcut+':'+season, 'name':name, 'season':int(season), 'provider':'OpenLigaDB', 'coverage':'unverified'}
    return sorted(found.values(),key=lambda x:(-x['season'],x['name']))


def resolve_community_league(code, *, fetch=None):
    if not code.startswith('TR:'): raise ValueError('Invalid dynamic league code')
    catalog = discover_turkish_leagues(fetch=fetch)
    item = next((x for x in catalog if x['code']==code),None)
    if item is None: raise ValueError('Turkish league not present in live catalogue')
    return item


def get_dynamic_raw(code, *, fetch_catalog=None, fetch_matches=None):
    item = resolve_community_league(code,fetch=fetch_catalog)
    shortcut=code.split(':')[1]
    req=Request(f'{API}/getmatchdata/{shortcut}/{item["season"]}',headers={'Accept':'application/json','User-Agent':'PalaBetAI/1.1'})
    try:
        if fetch_matches is None:
            with urlopen(req,timeout=12) as response: raw=response.read()
        else: raw=fetch_matches(req)
        matches=json.loads(raw)
        if not isinstance(matches,list): raise ValueError('Unexpected matches')
        return matches
    except (HTTPError,URLError,TimeoutError,ValueError,TypeError) as exc:
        raise OpenLigaError('Community league fixtures unavailable') from exc


def dynamic_fixtures(code, *, fetch_catalog=None, fetch_matches=None):
    raw=get_dynamic_raw(code,fetch_catalog=fetch_catalog,fetch_matches=fetch_matches)
    matches=[]
    for m in raw:
        home=(m.get('team1') or {}).get('teamName')
        away=(m.get('team2') or {}).get('teamName')
        when=m.get('matchDateTimeUTC')
        if not (home and away and when): continue
        matches.append({'id':str(m.get('matchID','')),'home':home,'away':away,'utc_date':when if when.endswith('Z') else when+'Z','status':'FINISHED' if m.get('matchIsFinished') else 'SCHEDULED','league':code})
    matches.sort(key=lambda x:x['utc_date'])
    return {'source':'OpenLigaDB (community data)','mode':'provider','competition':code,'matches':matches,'fetched_at':datetime.now(timezone.utc).isoformat(),'disclaimer':'Community catalogue presence does not guarantee current fixtures or event statistics.'}
