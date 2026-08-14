"""Variance contribution (Pareto) ranking: which components drive the
stack-up's spread, using the same linearized sensitivity (∂Z/∂xi at nominal)
as the RSS calculation, so the ranking is consistent with whichever RSS
number is being reported alongside it."""
from __future__ import annotations

from typing import Any

from tolerance_engine.expression_engine.parser import build_symbols, parse_expression, partial_derivatives
from tolerance_engine.models import Component
from tolerance_engine.statistics.distributions import component_stats


def contribution_ranking(expression: str, components: list[Component]) -> list[dict[str, Any]]:
    symbols = build_symbols(components)
    expr = parse_expression(expression, symbols)
    derivatives = partial_derivatives(expr, symbols, components)

    variance_terms: dict[str, float] = {}
    for c in components:
        stats = component_stats(c)
        variance_terms[c.id] = (derivatives[c.label] * stats.sigma) ** 2

    total_variance = sum(variance_terms.values())
    if total_variance == 0:
        # All components contribute nothing (e.g. every sigma is zero) —
        # avoid a divide-by-zero; report an even/undefined split as 0% each.
        return [{"componentId": cid, "contributionPct": 0.0} for cid in variance_terms]

    ranked = sorted(
        ({"componentId": cid, "contributionPct": 100.0 * v / total_variance} for cid, v in variance_terms.items()),
        key=lambda r: r["contributionPct"],
        reverse=True,
    )
    return ranked
