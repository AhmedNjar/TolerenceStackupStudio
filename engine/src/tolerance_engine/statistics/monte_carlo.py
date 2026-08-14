"""
Vectorized Monte Carlo stack-up simulation.

DPPM resolution note: with N Monte Carlo runs, empirical (counting) DPPM has
a floor of roughly 1e6/N — e.g. 20,000 runs cannot empirically distinguish
"0 defects" from "50 DPPM", let alone report a true Six Sigma ~3.4 DPMO.
When the empirical run shows zero defects, this module additionally fits a
normal distribution to the sample (using the simulated mean/std) and reports
the analytic tail probability instead, so a tight, well-centered stack still
gets a meaningful DPPM number rather than a floor-effect "0". That fallback
assumes near-normality — for a stack whose skewness/kurtosis (also returned)
show strong asymmetry, treat the normal-fit DPPM as approximate.
"""
from __future__ import annotations

from typing import Any, Optional

import numpy as np
from scipy import stats as sp_stats

from tolerance_engine.expression_engine.parser import build_symbols, lambdify_numpy, parse_expression
from tolerance_engine.models import Component, SpecLimits
from tolerance_engine.statistics.distributions import sample_component

_DEFAULT_HISTOGRAM_BINS_MIN = 20
_DEFAULT_HISTOGRAM_BINS_MAX = 100


def _histogram_bin_count(n: int) -> int:
    return int(np.clip(np.sqrt(n), _DEFAULT_HISTOGRAM_BINS_MIN, _DEFAULT_HISTOGRAM_BINS_MAX))


def monte_carlo(
    expression: str,
    components: list[Component],
    runs: int,
    spec_limits: Optional[SpecLimits],
    seed: Optional[int] = None,
) -> dict[str, Any]:
    symbols = build_symbols(components)
    expr = parse_expression(expression, symbols)
    fn, ordered_labels = lambdify_numpy(expr, components)

    rng = np.random.default_rng(seed)
    by_label = {c.label: c for c in components}
    sampled = {label: sample_component(by_label[label], runs, rng) for label in ordered_labels}

    z = np.asarray(fn(*[sampled[label] for label in ordered_labels]), dtype=float)

    mean = float(np.mean(z))
    std = float(np.std(z, ddof=1))
    skewness = float(sp_stats.skew(z))
    kurtosis = float(sp_stats.kurtosis(z))  # Fisher's definition: normal distribution -> 0

    counts, bin_edges = np.histogram(z, bins=_histogram_bin_count(runs))

    cp: Optional[float] = None
    cpk: Optional[float] = None
    yield_pct: float
    dppm: float
    dppm_method: Optional[str] = None

    if spec_limits is not None:
        usl, lsl = spec_limits.usl, spec_limits.lsl
        within = (z >= lsl) & (z <= usl)
        empirical_defects = runs - int(np.sum(within))

        if std > 0:
            cp = (usl - lsl) / (6 * std)
            cpk = min((usl - mean) / (3 * std), (mean - lsl) / (3 * std))

        if empirical_defects > 0 or std == 0:
            yield_pct = 100.0 * within.sum() / runs
            dppm = 1_000_000.0 * empirical_defects / runs
            dppm_method = "EMPIRICAL"
        else:
            # Zero defects observed at this sample size — fall back to the
            # analytic normal-fit tail so a tight/capable stack still gets a
            # non-floored DPPM estimate. See module docstring for caveats.
            p_within = float(sp_stats.norm.cdf(usl, mean, std) - sp_stats.norm.cdf(lsl, mean, std))
            yield_pct = 100.0 * p_within
            dppm = 1_000_000.0 * (1.0 - p_within)
            dppm_method = "NORMAL_FIT_TAIL"
    else:
        yield_pct = 100.0
        dppm = 0.0

    return {
        "runs": runs,
        "mean": mean,
        "stdDev": std,
        "skewness": skewness,
        "kurtosis": kurtosis,
        "cp": cp,
        "cpk": cpk,
        "yieldPct": yield_pct,
        "dppm": dppm,
        "dppmEstimationMethod": dppm_method,
        "histogram": {
            "binEdges": bin_edges.tolist(),
            "counts": counts.tolist(),
        },
    }
