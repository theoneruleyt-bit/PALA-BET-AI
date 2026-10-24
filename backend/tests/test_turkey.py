import json
from app.turkey import parse_feed, get_data
from app.event_stats import estimate_events

SAMPLE = [
 {'MatchNumber':1,'DateUtc':'2026-10-26 16:00:00Z','HomeTeam':'Besiktas','AwayTeam':'Galatasaray','HomeTeamScore':None,'AwayTeamScore':None},
 {'MatchNumber':2,'DateUtc':'2026-10-10 16:00:00Z','HomeTeam':'Fenerbahce','AwayTeam':'Besiktas','HomeTeamScore':2,'AwayTeamScore':1}]

def test_feed_real_schema():
    fixtures, history = parse_feed(SAMPLE)
    assert fixtures[0]['status']=='FINISHED'
    assert fixtures[1]['status']=='SCHEDULED'
    assert history[0]['matchResults']==[]
    assert history[1]['matchResults'][0]['pointsTeam1']==2

def test_fixture_source_injection():
    data=get_data(fetch=lambda _:json.dumps(SAMPLE).encode())
    assert data['competition']=='TR1'
    assert len(data['matches'])==2

def test_event_minimum_sample_and_nonfabrication():
    rows=[{'HomeTeam':'Besiktas','AwayTeam':'Galatasaray','HC':'5','AC':'6','HY':'2','AY':'3','HR':'0','AR':'0'} for _ in range(6)]
    r=estimate_events(rows,'Besiktas','Galatasaray')
    assert r['corners']['status']=='estimated'
    assert r['penalties']['status']=='unavailable'
    assert r['corners']['average']==11
    assert estimate_events(rows[:2],'Besiktas','Galatasaray')['corners']['status']=='insufficient_data'
