"""
RSS (Root Sum of Squares) and Modified/Extended RSS.

RSS assumes each component contributes an independent, zero-mean-relative-
to-its-own-center random variable; asymmetric tolerances are handled via
the mean-shift/half-range split in statistics/distributions.py.

IMPORTANT — the "dynamic shift factor" for Modified RSS: the literature
(Bender, Spotts, and the various "Six Sigma correction factor" treatments)
does not agree on one universal formula; correction factors in the 1.4-1.6
range for small assemblies, tapering toward 1.0 as component count grows,
are commonly cited, but the exact curve varies by source and by company
internal standard. `default_shift_factor()` below is a documented, smoothly
tapering heuristic in that commonly-cited range — NOT a specific named
published formula. Always prefer `options.modifiedRssShiftFactor` (an
explicit user-supplied value) when the caller has a company standard or a
specific textbook method in mind; the default only fires when the request
leaves it unset.
"""
from __future__ import annotations

from typing import Any, Optional

from tolerance_engine.expression_engine.parser import (
    build_symbols,
    nominal_value,
    parse_expression,
    partial_derivatives,
)
from tolerance_engine.models import Component
from tolerance_engine.statistics.distributions import component_stats

_K_SIGMA_REPORTING = 3.0  # predicted range reported at ±3σ, comparable to a 99.73% band


def default_shift_factor(n_components: int) -> float:
    """Documented heuristic default — see module docstring. Tapers from 1.5
    at n<=3 down to a floor of 1.0 as the assembly grows, reflecting that
    the central limit theorem makes the plain-RSS normal approximation
    progressively more defensible with more independent contributors."""
    if n_components <= 3:
        return 1.5
    return max(1.0, 1.5 - 0.05 * (n_components - 3))


def _rss_core(expression: str, components: list[Component]) -> dict[str, Any]:
    symbols = build_symbols(components)
    expr = parse_expression(expression, symbols)

    nominal = nominal_value(expr, symbols, components)
    derivatives = partial_derivatives(expr, symbols, components)

    mean_shift = 0.0
    variance = 0.0
    for c in components:
        stats = component_stats(c)
        d_dx = derivatives[c.label]
        mean_shift += d_dx * stats.mean_shift
        variance += (d_dx * stats.sigma) ** 2

    sigma_z = variance ** 0.5
    mean_z = nominal + mean_shift
    return {"nominal": nominal, "mean_z": mean_z, "sigma_z": sigma_z}


def rss(expression: str, components: list[Component]) -> dict[str, Any]:
    core = _rss_core(expression, components)
    return {
        "nominal": core["nominal"],
        "sigma": core["sigma_z"],
        "zMinPredicted": core["mean_z"] - _K_SIGMA_REPORTING * core["sigma_z"],
        "zMaxPredicted": core["mean_z"] + _K_SIGMA_REPORTING * core["sigma_z"],
        "shiftFactorApplied": None,
    }


def modified_rss(
    expression: str, components: list[Component], shift_factor: Optional[float]
) -> dict[str, Any]:
    core = _rss_core(expression, components)
    cf = shift_factor if shift_factor is not None else default_shift_factor(len(components))
    sigma_z_modified = cf * core["sigma_z"]
    return {
        "nominal": core["nominal"],
        "sigma": sigma_z_modified,
        "zMinPredicted": core["mean_z"] - _K_SIGMA_REPORTING * sigma_z_modified,
        "zMaxPredicted": core["mean_z"] + _K_SIGMA_REPORTING * sigma_z_modified,
        "shiftFactorApplied": cf,
    }
