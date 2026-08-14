import math

import pytest
import sympy as sp

from tolerance_engine.reporting.excel_formula_printer import ExcelFormulaError, to_excel_formula


def _excel_eval(formula: str, values: dict[str, float]) -> float:
    """Tiny Excel-semantics evaluator for test purposes only: substitutes
    cell names with numeric values, then evaluates using Python functions
    that match EXCEL's argument order/behavior (critically: ATAN2) rather
    than Python's. This lets tests check the translator by actually running
    its output, not by eyeballing the string."""
    body = formula.removeprefix("=")
    for name, value in values.items():
        body = body.replace(name, repr(value))

    def excel_atan2(x_num: float, y_num: float) -> float:
        return math.atan2(y_num, x_num)  # Excel ATAN2(x,y) == atan(y/x)

    namespace = {
        "SIN": math.sin, "COS": math.cos, "TAN": math.tan,
        "ASIN": math.asin, "ACOS": math.acos, "ATAN": math.atan,
        "SQRT": math.sqrt, "EXP": math.exp, "ABS": abs, "LN": math.log,
        "ATAN2": excel_atan2, "PI": lambda: math.pi,
    }
    return eval(body.replace("^", "**"), {"__builtins__": {}}, namespace)  # noqa: S307 - test-only, controlled input


def test_simple_subtraction_matches_sympy():
    A, B = sp.symbols("A B", real=True)
    expr = A - B
    formula = to_excel_formula(expr, {"A": "cA", "B": "cB"})
    values = {"cA": 25.021, "cB": 24.98}
    assert _excel_eval(formula, values) == pytest.approx(float(expr.evalf(subs={A: 25.021, B: 24.98})))


def test_triangular_vector_loop_matches_sympy():
    A, B, T = sp.symbols("A B T", real=True)
    expr = sp.sqrt(A**2 + B**2 - 2 * A * B * sp.cos(sp.pi * T / 180))
    formula = to_excel_formula(expr, {"A": "cA", "B": "cB", "T": "cT"})
    values = {"cA": 3.0, "cB": 4.0, "cT": 90.0}
    excel_result = _excel_eval(formula, values)
    sympy_result = float(expr.evalf(subs={A: 3.0, B: 4.0, T: 90.0}))
    assert excel_result == pytest.approx(sympy_result, abs=1e-6)
    assert excel_result == pytest.approx(5.0, abs=1e-6)  # 3-4-5 right triangle


def test_atan2_argument_order_swap_is_correct():
    # This is the specific gotcha the module docstring warns about: get the
    # swap backwards and this test catches it because the two angles below
    # are NOT symmetric (atan2(1,2) != atan2(2,1)).
    Y, X = sp.symbols("Y X", real=True)
    expr = sp.atan2(Y, X)  # SymPy convention: atan2(y, x)
    formula = to_excel_formula(expr, {"Y": "cY", "X": "cX"})

    values = {"cY": 1.0, "cX": 2.0}
    excel_result = _excel_eval(formula, values)
    sympy_result = float(expr.evalf(subs={Y: 1.0, X: 2.0}))
    assert excel_result == pytest.approx(sympy_result, abs=1e-9)
    assert excel_result == pytest.approx(math.atan2(1.0, 2.0), abs=1e-9)


def test_division_renders_as_explicit_fraction():
    A, B = sp.symbols("A B", real=True)
    expr = A / B
    formula = to_excel_formula(expr, {"A": "cA", "B": "cB"})
    values = {"cA": 10.0, "cB": 4.0}
    assert _excel_eval(formula, values) == pytest.approx(2.5)


def test_natural_log_uses_LN_not_LOG():
    A = sp.symbols("A", real=True)
    expr = sp.log(A)
    formula = to_excel_formula(expr, {"A": "cA"})
    assert "LN(" in formula
    assert "LOG(" not in formula
    assert _excel_eval(formula, {"cA": 7.5}) == pytest.approx(math.log(7.5))


def test_missing_cell_reference_raises():
    A, B = sp.symbols("A B", real=True)
    expr = A - B
    with pytest.raises(ExcelFormulaError):
        to_excel_formula(expr, {"A": "cA"})  # B missing


def test_thermal_expansion_style_expression_matches_sympy():
    # L0 * (1 + alpha*deltaT) — the same form Component.effective_nominal()
    # applies before stack-up evaluation.
    L0, alpha, dT = sp.symbols("L0 alpha dT", real=True)
    expr = L0 * (1 + alpha * dT)
    formula = to_excel_formula(expr, {"L0": "cL0", "alpha": "ca", "dT": "cdT"})
    values = {"cL0": 100.0, "ca": 0.000012, "cdT": 50.0}
    excel_result = _excel_eval(formula, values)
    sympy_result = float(expr.evalf(subs={L0: 100.0, alpha: 0.000012, dT: 50.0}))
    assert excel_result == pytest.approx(sympy_result)
