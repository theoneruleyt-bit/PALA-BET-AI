"""Optional observed corners/cards from football-data.co.uk T1 CSV.
No values are imputed; model shown only with sufficient real samples.
"""
import csv
import io
import math
import re
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

CSV_URL = 'https://www.football-data.co.uk/mmz4281/2627/T1.csv'

def _key(name):
    import unicodedata
    s = unicodedata.normalize('NFKD', name or '').casefold()
    return re.sub(r'[^a-z0-9]', '', ''.join(c for c in s if not unicodedata.combining(c)))

ALIASES = {'besiktas':'besiktas', 'fenerbahce':'fenerbahce', 'galatasaray':'galatasaray',
           'istanbulbasaksehir':'basaksehir', 'istanbulbasaksehirfk':'basaksehir',
           'basaksehir':'basaksehir', 'kasimpasa':'kasimpasa', 'caykurrizespor':'rizespor',
           'rizespor':'rizespor', 'gaziantep':'gaziantep', 'gaziantepfk':'gaziantep',
           'goztepe':'goztepe', 'eyupspor':'eyupspor', 'genclerbirligi':'genclerbirligi',
           'trabzonspor':'trabzonspor', 'samsunspor':'samsunspor', 'konyaspor':'konyaspor',
           'alanyaspor':'alanyaspor', 'kocaelispor':'kocaelispor',
           'erzurumspor':'erzurumspor', 'erzurumsporfk':'erzurumspor',
           'corum':'corum', 'corumfk':'corum', 'amedspor':'amedspor',
           'amedsportiffaaliyetler':'amedspor'}

def _team(name):
    key = _key(name)
    return ALIASES.get(key, key)

def _numeric(row, key):
    try:
        value = int(row.get(key, ''))
        return value if 0 <= value <= 50 else None
    except (ValueError, TypeError):
        return None

def _poisson_over(rate, threshold):
    k = int(threshold + .5)
    return round(1 - sum(math.exp(-rate)*rate**i/math.factorial(i) for i in range(k)), 4)

def estimate_events(rows, home, away):
    selected = [r for r in rows if _team(r.get('HomeTeam')) in (_team(home),_team(away)) or _team(r.get('AwayTeam')) in (_team(home),_team(away))]
    def estimate(fields, thresholds):
        values = []
        for row in selected:
            h, a = _numeric(row, fields[0]), _numeric(row, fields[1])
            if h is not None and a is not None:
                values.append(h+a)
        if len(values) < 6:
            return {'status':'insufficient_data', 'sample':len(values), 'required':6}
        rate = sum(values)/len(values)
        return {'status':'estimated', 'sample':len(values), 'average':round(rate,2),
                'over':{str(t):_poisson_over(rate,t) for t in thresholds},
                'method':'Poisson baseline from observed match events; uncalibrated'}
    return {'corners':estimate(('HC','AC'),(6.5,7.5,8.5,9.5,10.5,11.5,12.5)),
            'yellow_cards':estimate(('HY','AY'),(2.5,3.5,4.5,5.5,6.5)),
            'red_cards':estimate(('HR','AR'),(0.5,1.5)),
            'penalties':{'status':'unavailable','reason':'Penalty award history not supplied'},
            'source':'football-data.co.uk, 2026/27 T1 CSV',
            'disclaimer':'Historical observations, not guaranteed predictions; coverage may be incomplete.'}

def get_event_estimates(home, away, *, fetch=None):
    try:
        if fetch is None:
            with urlopen(Request(CSV_URL,headers={'User-Agent':'PalaBetAI/1.2'}),timeout=10) as response:
                raw=response.read()
        else:
            raw=fetch(CSV_URL)
        content=raw.decode('utf-8-sig') if isinstance(raw,bytes) else raw
        rows=list(csv.DictReader(io.StringIO(content)))
        if not rows or not {'HomeTeam','AwayTeam'}.issubset(rows[0]):
            raise ValueError('Unexpected CSV columns')
        return estimate_events(rows,home,away)
    except (HTTPError,URLError,TimeoutError,ValueError,UnicodeError,TypeError):
        return {'corners':{'status':'unavailable'},'yellow_cards':{'status':'unavailable'},
                'red_cards':{'status':'unavailable'},'penalties':{'status':'unavailable'},
                'source':'football-data.co.uk', 'disclaimer':'Event source unavailable or schema changed'}
