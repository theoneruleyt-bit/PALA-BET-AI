from app.main import _cache_get, _cache_put, app
from fastapi.testclient import TestClient

def test_research_cache_roundtrip():
    key = ('test', 'unique')
    assert _cache_get(key) is None
    assert _cache_put(key, {'value': 1}) == {'value': 1}
    assert _cache_get(key) == {'value': 1}

def test_validation_route_rejects_unknown_league():
    r = TestClient(app).get('/validation/INVALID')
    assert r.status_code == 400
