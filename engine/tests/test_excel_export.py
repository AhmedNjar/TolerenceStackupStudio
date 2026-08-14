import os
import tempfile

import formulas
import openpyxl
import pytest

from tolerance_engine.models import ClosingEquation, Component, ComponentKind, DistributionType
from tolerance_engine.reporting.excel_export import build_workbook
from tolerance_engine.statistics.rss import rss
from tolerance_engine.statistics.sensitivity import contribution_ranking
from tolerance_engine.statistics.worst_case import worst_case


def _shaft_housing_components():
    return [
        Component(id="c1", label="B", name="Housing bore", kind=ComponentKind.LINEAR,
                   nominal=25.0, upperTol=0.021, lowerTol=0.0, distribution=DistributionType.NORMAL_3S),
        Component(id="c2", label="D", name="Shaft diameter", kind=ComponentKind.LINEAR,
                   nominal=25.0, upperTol=0.0, lowerTol=0.020, distribution=DistributionType.NORMAL_3S),
    ]


def _recalculate(path: str) -> dict:
    """Loads and recalculates a saved workbook with the `formulas` library,
    returning {sheet!cell: value} for every cell in the solution."""
    xl_model = formulas.ExcelModel().loads(path).finish()
    solution = xl_model.calculate()
    return {k: v.value for k, v in solution.items()}


def _find(solution: dict, sheet: str, cell: str):
    for k, v in solution.items():
        if k.upper().endswith(f"'{sheet.upper()}'!{cell.upper()}") or k.upper().endswith(f"]{sheet.upper()}'!{cell.upper()}"):
            try:
                return v[0][0]
            except (TypeError, IndexError):
                return v
    raise KeyError(f"{sheet}!{cell} not found in solution (keys sample: {list(solution.keys())[:5]})")


def test_workbook_nominal_and_wc_match_engine_output():
    components = _shaft_housing_components()
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D")
    wc = worst_case("B - D", components)

    wb = build_workbook(components, [closing], monte_carlo_runs=2000, random_seed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "export.xlsx")
        wb.save(path)
        solution = _recalculate(path)

        # Row 2 = "Nominal", row 3 = WC zMin, row 4 = WC zMax (per _write_analysis_sheet's fixed layout for one closing eq)
        nominal = _find(solution, "ANALYSIS", "B2")
        zmin = _find(solution, "ANALYSIS", "B3")
        zmax = _find(solution, "ANALYSIS", "B4")

        assert nominal == pytest.approx(wc["nominal"], abs=1e-9)
        assert zmin == pytest.approx(wc["zMin"], abs=1e-9)
        assert zmax == pytest.approx(wc["zMax"], abs=1e-9)


def test_workbook_rss_sigma_matches_engine_output():
    components = _shaft_housing_components()
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D")
    r = rss("B - D", components)

    wb = build_workbook(components, [closing], monte_carlo_runs=2000, random_seed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "export.xlsx")
        wb.save(path)
        solution = _recalculate(path)
        sigma = _find(solution, "ANALYSIS", "B5")
        assert sigma == pytest.approx(r["sigma"], abs=1e-9)


def test_workbook_contribution_percentages_match_and_sum_to_100():
    components = _shaft_housing_components()
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D")
    ranking = {r["componentId"]: r["contributionPct"] for r in contribution_ranking("B - D", components)}

    wb = build_workbook(components, [closing], monte_carlo_runs=2000, random_seed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "export.xlsx")
        wb.save(path)
        solution = _recalculate(path)
        # Contribution rows are rows 8-9 (after nominal/zmin/zmax/sigma/predicted-range/blank/header)
        b_contribution = _find(solution, "ANALYSIS", "B9")
        d_contribution = _find(solution, "ANALYSIS", "B10")

        assert b_contribution == pytest.approx(ranking["c1"], abs=1e-6)
        assert d_contribution == pytest.approx(ranking["c2"], abs=1e-6)
        assert (b_contribution + d_contribution) == pytest.approx(100.0, abs=1e-6)


def test_workbook_wc_is_genuinely_live_to_tolerance_edits():
    """The strongest test: actually edit a tolerance cell in the saved
    workbook (simulating a user in Excel), re-save, re-recalculate, and
    check the WC formula picks up the change — not just that the formula
    string looks plausible."""
    components = _shaft_housing_components()
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D")
    wb = build_workbook(components, [closing], monte_carlo_runs=2000, random_seed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "export.xlsx")
        wb.save(path)

        # Edit component B's upperTol from 0.021 to 0.100 directly in the file.
        edited = openpyxl.load_workbook(path)
        edited["Components"]["E2"] = 0.100
        edited.save(path)

        solution = _recalculate(path)
        zmax = _find(solution, "ANALYSIS", "B4")

        # New expected zMax: nominal(0) + 1*0.100 (B's new upperTol) + 1*(-0.020) (D's lowerTol, d_dx=-1 -> -(-lowerTol))
        expected_zmax = worst_case("B - D", [
            Component(id="c1", label="B", kind=ComponentKind.LINEAR, nominal=25.0,
                       upperTol=0.100, lowerTol=0.0, distribution=DistributionType.NORMAL_3S),
            components[1],
        ])["zMax"]
        assert zmax == pytest.approx(expected_zmax, abs=1e-9)
        assert zmax != pytest.approx(0.041, abs=1e-9)  # sanity: actually changed from the original value


def test_workbook_triangular_vector_loop_nominal_matches_engine():
    components = [
        Component(id="c1", label="A", kind=ComponentKind.LINEAR, nominal=3.0, upperTol=0.01, lowerTol=0.01, distribution=DistributionType.NORMAL_3S),
        Component(id="c2", label="B", kind=ComponentKind.LINEAR, nominal=4.0, upperTol=0.01, lowerTol=0.01, distribution=DistributionType.NORMAL_3S),
        Component(id="c3", label="T", kind=ComponentKind.ANGULAR, nominal=90.0, upperTol=0.1, lowerTol=0.1, distribution=DistributionType.NORMAL_3S),
    ]
    expr = "sqrt(A**2 + B**2 - 2*A*B*cos(pi*T/180))"
    closing = ClosingEquation(id="z1", label="Z1", expression=expr)
    wc = worst_case(expr, components)

    wb = build_workbook(components, [closing], monte_carlo_runs=2000, random_seed=1)
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "export.xlsx")
        wb.save(path)
        solution = _recalculate(path)
        nominal = _find(solution, "ANALYSIS", "B2")
        assert nominal == pytest.approx(wc["nominal"], abs=1e-6)
        assert nominal == pytest.approx(5.0, abs=1e-6)  # 3-4-5 right triangle


def test_workbook_has_hidden_histogram_sheet_and_chart():
    components = _shaft_housing_components()
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D",
                               specLimits=None)
    wb = build_workbook(components, [closing], monte_carlo_runs=2000, random_seed=1)

    assert "hist_z1" in wb.sheetnames
    assert wb["hist_z1"].sheet_state == "hidden"

    analysis_sheet = wb["Analysis"]
    assert len(analysis_sheet._charts) == 1  # openpyxl internal, but there's no public chart-count API
