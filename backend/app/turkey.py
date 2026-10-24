"""Public Turkish Super Lig 2026/27 feed with strict schema validation.
FixtureDownload provides schedules/results, NOT corner/card/penalty events.
"""
import json
import time
from datetime import datetime, timezone
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

URL = 'https://fixturedownload.com/feed/json/super-lig-2026'
_CACHE = None

class TurkeyFeedError(Exception):
    pass

def _parse_date(value):
    if not isinstance(value, str):
        raise ValueError('Missing UTC date')
    dt = datetime.fromisoformat(value.strip().replace(' ', 'T').replace('Z', '+00:00'))
    if dt.tzinfo is None:
        raise ValueError('Unzoned fixture date')
    return dt.astimezone(timezone.utc).isoformat().replace('+00:00', 'Z')

def parse_feed(raw):
    rows = json.loads(raw) if isinstance(raw, (str, bytes)) else raw
    if not isinstance(rows, list):
        raise ValueError('Invalid feed')
    fixtures, history = [], []
    for row in rows:
        if not isinstance(row, dict):
            continue
        home, away = row.get('HomeTeam'), row.get('AwayTeam')
        try:
            utc = _parse_date(row.get('DateUtc'))
        except (ValueError, TypeError):
            continue
        if not isinstance(home, str) or not isinstance(away, str) or not home.strip() or not away.strip() or home == away:
            continue
        number = row.get('MatchNumber')
        if not isinstance(number, int) or number < 1:
            continue
        hg, ag = row.get('HomeTeamScore'), row.get('AwayTeamScore')
        finished = type(hg) is int and type(ag) is int and 0 <= hg <= 30 and 0 <= ag <= 30
        fixtures.append({'id': str(number), 'home': home, 'away': away, 'utc_date': utc,
                         'status': 'FINISHED' if finished else 'SCHEDULED', 'league': 'TR1'})
        history.append({'matchID': number, 'matchDateTimeUTC': utc,
                        'team1': {'teamName': home}, 'team2': {'teamName': away},
                        'matchIsFinished': finished,
                        'matchResults': ([{'resultTypeID': 2, 'pointsTeam1': hg, 'pointsTeam2': ag}] if finished else [])})
    fixtures.sort(key=lambda m: m['utc_date'])
    if not fixtures:
        raise ValueError('No valid fixtures')
    return fixtures, history

def get_data(*, fetch=None, now=None, ttl=900):
    global _CACHE
    now = time.time() if now is None else now
    if fetch is None and _CACHE and 0 <= now - _CACHE[0] < ttl:
        return _CACHE[1]
    try:
        if fetch is None:
            with urlopen(Request(URL, headers={'Accept': 'application/json', 'User-Agent': 'PalaBetAI/1.2'}), timeout=15) as response:
                raw = response.read()
        else:
            raw = fetch(URL)
        fixtures, history = parse_feed(raw)
    except (HTTPError, URLError, TimeoutError, ValueError, TypeError, KeyError) as exc:
        raise TurkeyFeedError('2026/27 Türkiye Süper Lig fikstür kaynağı erişilemiyor') from exc
    data = {'source': 'FixtureDownload (2026/27, public feed)', 'mode': 'provider',
            'competition': 'TR1', 'fetched_at': datetime.fromtimestamp(now, timezone.utc).isoformat().replace('+00:00', 'Z'),
            'matches': fixtures, 'raw': history,
            'disclaimer': 'Public fixture feed; may be delayed. Does not contain corners, cards or penalties.'}
    if fetch is None:
        _CACHE = (now, data)
    return data

def fixtures():
    return {k: v for k, v in get_data().items() if k != 'raw'}

def raw_matches():
    return get_data()['raw']
