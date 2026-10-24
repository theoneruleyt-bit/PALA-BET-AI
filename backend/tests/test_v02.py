from app.model import predict, fair_odds, expected_value, brier_score

def test_probabilities():
    p = predict(1.4, 1.1)
    assert abs(p.home + p.draw + p.away - 1) < 1e-8
    assert 0 < p.btts < 1
    assert abs(p.under25 + p.over25 - 1) < 1e-8

def test_fair_odds():
    assert fair_odds(0.5) == 2.0
    assert abs(expected_value(0.5, 2.1) - 0.05) < 1e-9

def test_brier():
    assert brier_score((1.0, 0.0, 0.0), 0) == 0.0
