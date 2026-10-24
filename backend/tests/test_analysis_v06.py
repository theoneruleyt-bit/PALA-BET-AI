from app.analysis_v06 import normalize, estimate, backtest

def sample():
    rows=[]
    for i in range(24):
        rows.append({'matchID':i, 'matchIsFinished':True, 'team1':{'teamName':'A' if i%2==0 else 'B'}, 'team2':{'teamName':'B' if i%2==0 else 'A'}, 'matchDateTimeUTC':f'2026-08-{i+1:02d}T12:00:00', 'matchResults':[{'resultTypeID':2,'pointsTeam1':i%4,'pointsTeam2':(i+1)%3}]})
    return rows

def test_estimate_and_backtest():
    history=normalize(sample())
    assert len(history)==24
    p=estimate(history,'A','B')
    assert p and abs(sum(p['probabilities'][x] for x in ('home','draw','away'))-1)<1e-6
    assert backtest(history)['evaluated_matches']>0

def test_no_leakage_or_insufficient_data():
    history=normalize(sample())
    assert estimate(history,'A','B',before='2026-08-04T12:00:00') is None
    assert estimate(history,'A','Unknown') is None

def test_bad_results_ignored():
    data=sample()
    data[0]['matchResults']=[]
    assert len(normalize(data))==23
