from app.markets import extra_markets

def test_market_consistency():
    r=extra_markets({'goal_rates':{'home':1.4,'away':1.1}})
    for v in r['goal_lines'].values(): assert abs(v['over']+v['under']-1)<0.0001
    assert 0 <= r['double_chance']['1X'] <= 1
    assert len(r['correct_score_top5'])==5
