"""Sensitivity-based Worst Case calculation, refactored out of handlers.py
to share expression parsing with rss.py / monte_carlo.py / sensitivity.py."""
from __future__ import annotations

from typing import Any

from tolerance_engine.expression_engine.parser import (
    build_symbols,
    nominal_value,
    parse_expression,
    partial_derivatives,
)
from tolerance_engine.models import Component


def worst_case(expression: str, components: list[Component]) -> dict[str, Any]:
    symbols = build_symbols(components)
    expr = parse_expression(expression, symbols)

    nominal = nominal_value(expr, symbols, components)
    derivatives = partial_derivatives(expr, symbols, components)

    max_dev = 0.0
    min_dev = 0.0
    for c in components:
        d_dx = derivatives[c.label]
        if d_dx >= 0:
            max_dev += d_dx * c.upperTol
            min_dev += d_dx * (-c.lowerTol)
        else:
            max_dev += d_dx * (-c.lowerTol)
            min_dev += d_dx * c.upperTol

    return {
        "nominal": nominal,
        "zMin": nominal + min_dev,
        "zMax": nominal + max_dev,
    }
