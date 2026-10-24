import json
import pytest
from app.football_data import get_matches, ProviderError, _cache

def test_requires_key(monkeypatch):
    monkeypatch.delenv("FOOTBALL_DATA_API_KEY", raising=False)
    with pytest.raises(ProviderError): get_matches("PL")

def test_validates_competition():
    with pytest.raises(ValueError): get_matches("../../bad", token="test")

def test_provider_and_cache():
    _cache.clear()
    calls=[]
    def fetch(req):
        calls.append(req)
        return json.dumps({"matches":[{"id":42,"utcDate":"2026-11-01T12:00:00Z","homeTeam":{"name":"Home"},"awayTeam":{"name":"Away"},"status":"SCHEDULED"}]}).encode()
    first=get_matches("PL",token="test",fetch=fetch,now=1000)
    second=get_matches("PL",token="test",fetch=fetch,now=1001)
    assert first["mode"] == "provider" and first["cache"] == "miss"
    assert second["cache"] == "hit" and len(calls)==1
    assert first["matches"][0]["home"]=="Home"
