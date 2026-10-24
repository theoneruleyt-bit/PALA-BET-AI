from app.openligadb import get_matches, OpenLigaError
from app.main import app
from fastapi.testclient import TestClient
import json


def test_keyless_mapping_and_cache():
    payload = [{'matchID':11,'matchDateTimeUTC':'2026-10-24T18:30:00','matchIsFinished':False,'team1':{'teamName':'Home'},'team2':{'teamName':'Away'}}]
    calls = []
    def fetch(req):
        calls.append(req.full_url)
        return json.dumps(payload).encode()
    a = get_matches('BL1',fetch=fetch,now=1792850000,season=2026)
    b = get_matches('BL1',fetch=fetch,now=1792850001,season=2026)
    assert a['mode'] == 'provider' and a['matches'][0]['home']=='Home'
    assert a['matches'][0]['utc_date'].endswith('Z')
    assert b['cache']=='hit' and len(calls)==1
    assert '/bl1/2026' in calls[0]


def test_invalid_league():
    try:
        get_matches('../bad')
    except ValueError:
        pass
    else:
        assert False


def test_health_and_leagues():
    c = TestClient(app)
    assert c.get('/health').json()['version']=='0.6.0'
    assert c.get('/leagues').status_code==200
    assert c.get('/fixtures/UNKNOWN').status_code==400
