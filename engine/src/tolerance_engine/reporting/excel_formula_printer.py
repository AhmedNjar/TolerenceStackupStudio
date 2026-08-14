"""
Translates a parsed SymPy expression into an Excel formula string, so the
Excel export can ship *live* formulas for the closed-form methods (nominal,
Worst Case, RSS) rather than a static snapshot of Python-computed numbers —
edit a tolerance in the workbook and the WC/RSS cells recalculate the way a
real MITCalc-style spreadsheet would.

This walks the SymPy expression TREE (expr.func / expr.args), not the
original expression string — the string form is whatever variant the user
typed (spacing, redundant parens, whichever function order), while the tree
is canonical, so translating the tree is what keeps this in sync with
exactly what the engine's own WC/RSS/Monte Carlo math evaluates, and it
sidesteps a whole class of string-matching bugs.

GOTCHA WORTH KNOWING: Excel's ATAN2(x_num, y_num) computes atan(y_num /
x_num) — x first, then y. SymPy's atan2(y, x) (matching Python's
math.atan2 and most languages) takes y first, then x. Same math, swapped
argument order. Get this backwards and every angle silently comes out
wrong. _translate() below swaps the argument order explicitly, and
test_excel_formula_printer.py checks it against a known angle rather than
just trusting the swap looks right.
"""
from __future__ import annotations

import sympy as sp

# Every function name this project's restricted expression parser allows
# (see expression_engine/parser.py's _ALLOWED_FUNCTIONS) needs an entry here,
# or a template/user expression using it silently can't be exported live.
_SYMPY_TO_EXCEL_FUNCTION = {
    sp.sin: "SIN", sp.cos: "COS", sp.tan: "TAN",
    sp.asin: "ASIN", sp.acos: "ACOS", sp.atan: "ATAN",
    sp.sqrt: "SQRT", sp.exp: "EXP", sp.Abs: "ABS",
    # sp.log with one argument is natural log -> Excel's LN, not LOG
    # (Excel's LOG() defaults to base 10). Handled specially in _translate.
}


class ExcelFormulaError(ValueError):
    pass


def to_excel_formula(expr: sp.Expr, cell_refs: dict[str, str]) -> str:
    """cell_refs maps component label -> Excel cell reference (e.g.
    {'A': "'Components'!$E$2"}). Returns a formula string INCLUDING the
    leading '='."""
    missing = {str(s) for s in expr.free_symbols} - set(cell_refs.keys())
    if missing:
        raise ExcelFormulaError(f"No cell reference provided for symbol(s): {sorted(missing)}")
    return "=" + _translate(expr, cell_refs)


def _translate(expr: sp.Expr, cell_refs: dict[str, str]) -> str:
    if expr.is_Symbol:
        return cell_refs[str(expr)]

    if expr.is_Number:
        return _format_number(expr)

    if expr == sp.pi:
        return "PI()"

    if expr.is_Add:
        return "(" + "+".join(_translate(term, cell_refs) for term in expr.args) + ")"

    if expr.is_Mul:
        # Render explicit division for any negative-power (1/x) factor rather
        # than a literal "^-1" — clearer in the exported formula and avoids
        # relying on Excel's operator precedence for a chain of "*x^-1"s.
        numerator_factors = []
        denominator_factors = []
        for factor in expr.args:
            if factor.is_Pow and factor.args[1].is_Number and factor.args[1] < 0:
                denominator_factors.append(_translate(factor.args[0] ** (-factor.args[1]), cell_refs))
            else:
                numerator_factors.append(_translate(factor, cell_refs))
        numerator = "*".join(numerator_factors) if numerator_factors else "1"
        if denominator_factors:
            return "(" + numerator + "/" + "*".join(denominator_factors) + ")"
        return "(" + numerator + ")"

    if expr.is_Pow:
        base, exponent = expr.args
        if exponent == sp.Rational(1, 2):
            return f"SQRT({_translate(base, cell_refs)})"
        return f"({_translate(base, cell_refs)}^{_translate(exponent, cell_refs)})"

    if isinstance(expr, sp.log) and len(expr.args) == 1:
        return f"LN({_translate(expr.args[0], cell_refs)})"

    if isinstance(expr, sp.atan2):
        # SymPy: atan2(y, x). Excel: ATAN2(x_num, y_num). Argument order
        # swaps — see module docstring.
        y, x = expr.args
        return f"ATAN2({_translate(x, cell_refs)},{_translate(y, cell_refs)})"

    for sympy_fn, excel_name in _SYMPY_TO_EXCEL_FUNCTION.items():
        if isinstance(expr, sympy_fn):
            args = ",".join(_translate(a, cell_refs) for a in expr.args)
            return f"{excel_name}({args})"

    raise ExcelFormulaError(
        f"No Excel translation for SymPy node type {type(expr).__name__} in expression {expr!r}. "
        "This should only happen for a function outside expression_engine.parser's allowed set — "
        "if that set changes, _SYMPY_TO_EXCEL_FUNCTION needs a matching entry."
    )


def _format_number(n: sp.Expr) -> str:
    if n.is_Integer:
        return str(int(n))
    return repr(float(n))
