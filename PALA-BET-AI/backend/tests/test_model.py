import pytest
from app.model import predict, elo_expected

def test_probabilities_sum_to_one():
    p = predict(1.65, 1.12)
    assert abs(p.home + p.draw + p.away - 1) < 1e-10
    assert 0 < p.over25 < 1

def test_symmetry_without_home_advantage():
    p = predict(1.2, 1.2)
    assert abs(p.home - p.away) < 1e-10

def test_elo_home_advantage():
    assert elo_expected(1500, 1500) > .5

def test_invalid_xg():
    with pytest.raises(ValueError): predict(-1, 1.2)
