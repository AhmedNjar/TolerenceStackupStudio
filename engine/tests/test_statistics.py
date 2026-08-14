import math

import pytest

from tolerance_engine.models import Component, ComponentKind, DistributionType, SpecLimits, Thermal
from tolerance_engine.statistics.monte_carlo import monte_carlo
from tolerance_engine.statistics.rss import modified_rss, rss
from tolerance_engine.statistics.sensitivity import contribution_ranking
from tolerance_engine.statistics.worst_case import worst_case


def _linear(label, nominal, upper, lower, dist=DistributionType.NORMAL_3S, thermal=None):
    return Component(
        id=f"id-{label}", label=label, kind=ComponentKind.LINEAR,
        nominal=nominal, upperTol=upper, lowerTol=lower, distribution=dist, thermal=thermal,
    )


def test_worst_case_shaft_housing_gap():
    components = [_linear("B", 25.0, 0.021, 0.0), _linear("D", 25.0, 0.0, 0.020)]
    result = worst_case("B - D", components)
    assert result["nominal"] == pytest.approx(0.0, abs=1e-9)
    assert result["zMin"] == pytest.approx(0.0, abs=1e-9)
    assert result["zMax"] == pytest.approx(0.041, abs=1e-9)


def test_worst_case_applies_thermal_expansion_before_stackup():
    rod = _linear("L", 100.0, 0.05, 0.05, thermal=Thermal(alpha=0.000012, deltaT=50))
    result = worst_case("L", [rod])
    expected_nominal = 100.0 * (1 + 0.000012 * 50)
    assert result["nominal"] == pytest.approx(expected_nominal, abs=1e-9)


def test_worst_case_triangular_vector_loop_template():
    # Right-angle case (90 deg) reduces to Pythagorean: sqrt(3^2 + 4^2) = 5
    side_a = _linear("A", 3.0, 0.01, 0.01)
    side_b = _linear("B", 4.0, 0.01, 0.01)
    angle = Component(id="id-T", label="T", kind=ComponentKind.ANGULAR,
                       nominal=90.0, upperTol=0.1, lowerTol=0.1, distribution=DistributionType.NORMAL_3S)
    expr = "sqrt(A**2 + B**2 - 2*A*B*cos(pi*T/180))"
    result = worst_case(expr, [side_a, side_b, angle])
    assert result["nominal"] == pytest.approx(5.0, abs=1e-6)


def test_rss_narrower_than_worst_case():
    components = [_linear("A", 50.0, 0.10, 0.10), _linear("B", 20.0, 0.05, 0.05), _linear("C", 5.0, 0.02, 0.02)]
    wc = worst_case("A - B - C", components)
    r = rss("A - B - C", components)
    wc_range = wc["zMax"] - wc["zMin"]
    rss_range = r["zMaxPredicted"] - r["zMinPredicted"]
    assert rss_range < wc_range
    assert r["shiftFactorApplied"] is None


def test_modified_rss_default_shift_factor_widens_rss():
    components = [_linear("A", 50.0, 0.10, 0.10), _linear("B", 20.0, 0.05, 0.05), _linear("C", 5.0, 0.02, 0.02)]
    r = rss("A - B - C", components)
    mr = modified_rss("A - B - C", components, shift_factor=None)
    assert mr["shiftFactorApplied"] == pytest.approx(1.5)  # n=3 -> default heuristic ceiling
    assert mr["sigma"] == pytest.approx(1.5 * r["sigma"])


def test_modified_rss_explicit_shift_factor_overrides_default():
    components = [_linear("A", 50.0, 0.10, 0.10), _linear("B", 20.0, 0.05, 0.05)]
    mr = modified_rss("A - B", components, shift_factor=1.2)
    assert mr["shiftFactorApplied"] == 1.2


def test_monte_carlo_mean_matches_analytic_nominal():
    components = [_linear("A", 50.0, 0.10, 0.10, dist=DistributionType.UNIFORM),
                  _linear("B", 20.0, 0.05, 0.05, dist=DistributionType.TRIANGULAR)]
    result = monte_carlo("A - B", components, runs=40000, spec_limits=None, seed=123)
    assert result["mean"] == pytest.approx(30.0, abs=0.05)
    assert result["runs"] == 40000
    assert len(result["histogram"]["counts"]) > 0
    assert sum(result["histogram"]["counts"]) == 40000


def test_monte_carlo_cp_cpk_and_dppm_with_spec_limits():
    components = [_linear("A", 50.0, 0.10, 0.10), _linear("B", 20.0, 0.05, 0.05)]
    limits = SpecLimits(usl=30.3, lsl=29.7)
    result = monte_carlo("A - B", components, runs=50000, spec_limits=limits, seed=1)
    assert result["cp"] is not None and result["cp"] > 1.0
    assert result["cpk"] is not None
    assert 0 <= result["yieldPct"] <= 100
    assert result["dppmEstimationMethod"] in {"EMPIRICAL", "NORMAL_FIT_TAIL"}


def test_monte_carlo_asymmetric_tolerance_shifts_mean_off_nominal():
    # B has tolerance entirely on the low side -> Z = A - B should shift up
    components = [_linear("A", 50.0, 0.0, 0.0), _linear("B", 20.0, 0.0, 0.10)]
    result = monte_carlo("A - B", components, runs=40000, spec_limits=None, seed=5)
    assert result["mean"] > 30.0  # B centered below nominal -> A-B centered above nominal


def test_contribution_ranking_sums_to_100_and_sorted_desc():
    components = [_linear("A", 50.0, 0.10, 0.10), _linear("B", 20.0, 0.05, 0.05), _linear("C", 5.0, 0.02, 0.02)]
    ranking = contribution_ranking("A - B - C", components)
    assert sum(r["contributionPct"] for r in ranking) == pytest.approx(100.0)
    pcts = [r["contributionPct"] for r in ranking]
    assert pcts == sorted(pcts, reverse=True)
    assert ranking[0]["componentId"] == "id-A"  # largest tolerance dominates variance here


def test_unknown_label_raises_expression_error():
    from tolerance_engine.expression_engine.parser import ExpressionError
    components = [_linear("A", 1.0, 0.1, 0.1)]
    with pytest.raises(ExpressionError):
        worst_case("A - Q", components)
