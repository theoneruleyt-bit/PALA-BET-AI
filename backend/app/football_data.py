"""Optional football-data.org fixture source. No credentials are embedded in the app."""
import json
import os
import time
from datetime import datetime, timezone
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

BASE = "https://api.football-data.org/v4"
_cache = {}

class ProviderError(Exception):
    pass

def get_matches(competition="PL", *, token=None, fetch=None, ttl=300, now=None):
    """Return provider fixtures with provenance. No synthetic fallback masquerading as live."""
    if not competition.isalnum() or len(competition) > 12:
        raise ValueError("Invalid competition code")
    token = token if token is not None else os.environ.get("FOOTBALL_DATA_API_KEY", "")
    if not token:
        raise ProviderError("FOOTBALL_DATA_API_KEY is not configured")
    now = time.time() if now is None else now
    key = (competition, token)
    cached = _cache.get(key)
    if cached and now - cached[0] < ttl:
        return {**cached[1], "cache": "hit"}
    request = Request(f"{BASE}/competitions/{competition}/matches?status=SCHEDULED", headers={"X-Auth-Token": token, "Accept": "application/json"})
    try:
        if fetch is None:
            with urlopen(request, timeout=10) as response:
                raw = response.read()
        else:
            raw = fetch(request)
        data = json.loads(raw)
        matches = [{"id": str(m["id"]), "utc_date": m.get("utcDate"), "home": m.get("homeTeam", {}).get("name"), "away": m.get("awayTeam", {}).get("name"), "status": m.get("status")} for m in data.get("matches", [])]
    except (HTTPError, URLError, TimeoutError, ValueError, KeyError, TypeError) as exc:
        raise ProviderError("Fixture provider request failed") from exc
    result = {"source": "football-data.org", "mode": "provider", "fetched_at": datetime.fromtimestamp(now, timezone.utc).isoformat(), "competition": competition, "matches": matches, "cache": "miss"}
    _cache[key] = (now, result)
    return result
