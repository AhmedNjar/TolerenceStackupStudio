"""
Central place every statistics module goes through to turn a closing-
equation string into SymPy objects. Kept in one place so worst_case.py,
rss.py, monte_carlo.py, and sensitivity.py can't drift into parsing the
expression slightly differently from each other.

Parsing is deliberately restricted to a whitelist of trig/algebraic
functions rather than sympy's full namespace. These expressions come from
the same local user who owns the desktop app (there is no network-facing
"other user" submitting them), so this isn't a cross-user sandboxing
boundary — it's just good hygiene: it turns a typo like referencing an
unexpected name into a clear parse error instead of silently resolving to
some unrelated sympy symbol/function.
"""
from __future__ import annotations

import sympy as sp
from sympy.parsing.sympy_parser import (
    convert_xor,
    parse_expr,
    standard_transformations,
)

from tolerance_engine.models import Component

_ALLOWED_FUNCTIONS = {
    "sin": sp.sin, "cos": sp.cos, "tan": sp.tan,
    "asin": sp.asin, "acos": sp.acos, "atan": sp.atan, "atan2": sp.atan2,
    "sqrt": sp.sqrt, "exp": sp.exp, "log": sp.log, "Abs": sp.Abs,
    "pi": sp.pi, "E": sp.E,
    # Needed so SymPy's transformations can construct core objects for things
    # it doesn't find in local_dict — Symbol for an unrecognized name (see
    # below), Integer/Float/Rational for numeric literals like the `2` and
    # `180` in a template expression. Without these, parsing something as
    # ordinary as "2*A*B*cos(...)" fails with an internal "name 'Integer' is
    # not defined" NameError instead of actually parsing.
    "Symbol": sp.Symbol, "Integer": sp.Integer, "Float": sp.Float, "Rational": sp.Rational,
}


class ExpressionError(ValueError):
    """Raised for a closing-equation expression that fails to parse or
    references a name outside the component labels / allowed functions."""


def build_symbols(components: list[Component]) -> dict[str, sp.Symbol]:
    return {c.label: sp.symbols(c.label, real=True) for c in components}


def parse_expression(expression: str, symbols: dict[str, sp.Symbol]) -> sp.Expr:
    local_dict = {**symbols}
    global_dict = {**_ALLOWED_FUNCTIONS}
    try:
        expr = parse_expr(
            expression,
            local_dict=local_dict,
            global_dict=global_dict,
            transformations=(*standard_transformations, convert_xor),
            evaluate=True,
        )
    except Exception as exc:  # noqa: BLE001 - re-raised as a domain-specific error
        raise ExpressionError(f"Could not parse expression '{expression}': {exc}") from exc

    unknown = {str(s) for s in expr.free_symbols} - set(symbols.keys())
    if unknown:
        raise ExpressionError(
            f"Expression '{expression}' references unknown component label(s): {sorted(unknown)}"
        )
    return expr


def effective_nominal_substitutions(
    components: list[Component], symbols: dict[str, sp.Symbol]
) -> dict[sp.Symbol, float]:
    # NOTE: this must be Symbol-keyed, not string-keyed. build_symbols() creates
    # symbols with real=True (so sqrt/trig simplify sensibly), and SymPy's
    # evalf(subs=...) does NOT auto-sympify string keys the way .subs() does —
    # a string-keyed dict here silently fails to substitute anything and
    # evalf() returns the expression unevaluated. Symbol-keyed avoids that.
    return {symbols[c.label]: c.effective_nominal() for c in components}


def nominal_value(expr: sp.Expr, symbols: dict[str, sp.Symbol], components: list[Component]) -> float:
    return float(expr.evalf(subs=effective_nominal_substitutions(components, symbols)))


def partial_derivatives(
    expr: sp.Expr, symbols: dict[str, sp.Symbol], components: list[Component]
) -> dict[str, float]:
    substitutions = effective_nominal_substitutions(components, symbols)
    return {
        label: float(sp.diff(expr, sym).evalf(subs=substitutions))
        for label, sym in symbols.items()
    }


def lambdify_numpy(expr: sp.Expr, components: list[Component]):
    """Returns (fn, ordered_labels). fn accepts one 1-D numpy array per
    component, in ordered_labels order, and returns a vectorized result
    array — used by the Monte Carlo engine."""
    ordered_labels = [c.label for c in components]
    symbols = [sp.symbols(label, real=True) for label in ordered_labels]
    fn = sp.lambdify(symbols, expr, modules="numpy")
    return fn, ordered_labels
