"""Score-distribution markets derived only from fitted Poisson goal rates.
Not bookmaker odds, not validated profitability; no unsupported cards/corners markets.
"""
from math import exp, factorial

def extra_markets(analysis):
    if not analysis: return None
    h = analysis['goal_rates']['home']; a = analysis['goal_rates']['away']
    def pmf(lam, n): return exp(-lam)*lam**n/factorial(n)
    grid = [[pmf(h,i)*pmf(a,j) for j in range(16)] for i in range(16)]
    total = sum(map(sum,grid))
    def prob(fn): return round(sum(grid[i][j] for i in range(16) for j in range(16) if fn(i,j))/total,5)
    totals = {str(k/2): {'over':prob(lambda i,j,k=k:i+j>k/2),'under':prob(lambda i,j,k=k:i+j<k/2)} for k in (1,3,5,7,9)}
    # All goal lines are half-integer so no push event.
    scores = sorted(((i,j,grid[i][j]/total) for i in range(8) for j in range(8)),key=lambda x:-x[2])[:5]
    return {'goal_lines':totals,
            'team_goals':{'home_over_0_5':prob(lambda i,j:i>=1),'away_over_0_5':prob(lambda i,j:j>=1),'home_over_1_5':prob(lambda i,j:i>=2),'away_over_1_5':prob(lambda i,j:j>=2)},
            'double_chance':{'1X':prob(lambda i,j:i>=j),'X2':prob(lambda i,j:i<=j),'12':prob(lambda i,j:i!=j)},
            'correct_score_top5':[{'score':f'{i}-{j}','probability':round(p,5)} for i,j,p in scores],
            'first_half':'unavailable','corners':'unavailable','cards':'unavailable',
            'disclaimer':'Only model-derived score markets. No betting odds, no value-bet or guaranteed-profit claim.'}
