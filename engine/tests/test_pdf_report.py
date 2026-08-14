import os
import tempfile

import pypdf
import pytest

from tolerance_engine.models import (
    AnalysisMethod,
    AnalysisOptions,
    ClosingEquation,
    Component,
    ComponentKind,
    DistributionType,
    SpecLimits,
    StandardFit,
)
from tolerance_engine.reporting.pdf_report import generate_pdf


def _shaft_housing_components(with_standard_fit: bool = False):
    b = Component(
        id="c1", label="B", name="Housing bore", kind=ComponentKind.LINEAR,
        nominal=25.0, upperTol=0.021, lowerTol=0.0, distribution=DistributionType.NORMAL_3S,
        standardFit=StandardFit(standard="ISO286", designation="H7") if with_standard_fit else None,
    )
    d = Component(id="c2", label="D", name="Shaft diameter", kind=ComponentKind.LINEAR,
                  nominal=25.0, upperTol=0.0, lowerTol=0.020, distribution=DistributionType.NORMAL_3S)
    return [b, d]


def _extract_text(path: str) -> str:
    reader = pypdf.PdfReader(path)
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def test_pdf_generates_valid_file_with_expected_page_count():
    components = _shaft_housing_components()
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D", specLimits=SpecLimits(usl=0.05, lsl=-0.05))
    options = AnalysisOptions(
        methods=[AnalysisMethod.WORST_CASE, AnalysisMethod.MODIFIED_RSS, AnalysisMethod.MONTE_CARLO],
        monteCarloRuns=5000, randomSeed=1,
    )

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "report.pdf")
        generate_pdf("test-chain", components, [closing], options, path)

        assert os.path.exists(path)
        assert os.path.getsize(path) > 1000  # not a near-empty/broken file

        reader = pypdf.PdfReader(path)
        # cover page + input-params/compliance page + >=1 page of closing-dimension
        # content (WC+RSS+Monte Carlo table+2 charts is dense enough to legitimately
        # overflow onto a second page — that's correct pagination, not a bug, so this
        # checks the invariant that actually matters rather than an exact brittle count).
        assert len(reader.pages) >= 3


def test_pdf_contains_chain_id_and_component_labels():
    components = _shaft_housing_components()
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D")
    options = AnalysisOptions(methods=[AnalysisMethod.WORST_CASE], monteCarloRuns=2000, randomSeed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "report.pdf")
        generate_pdf("shaft-housing-demo", components, [closing], options, path)
        text = _extract_text(path)

        assert "shaft-housing-demo" in text
        assert "Housing bore" in text
        assert "Shaft diameter" in text
        assert "Z1" in text


def test_pdf_standard_compliance_section_lists_applied_fits():
    components = _shaft_housing_components(with_standard_fit=True)
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D")
    options = AnalysisOptions(methods=[AnalysisMethod.WORST_CASE], monteCarloRuns=2000, randomSeed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "report.pdf")
        generate_pdf("chain", components, [closing], options, path)
        text = _extract_text(path)

        assert "ISO286" in text
        assert "H7" in text


def test_pdf_pass_fail_summary_reflects_spec_limits():
    components = _shaft_housing_components()
    # Deliberately impossible spec window -> should FAIL
    closing_fail = ClosingEquation(id="z1", label="Z1", expression="B - D", specLimits=SpecLimits(usl=0.001, lsl=-0.001))
    options = AnalysisOptions(methods=[AnalysisMethod.MONTE_CARLO], monteCarloRuns=5000, randomSeed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "report_fail.pdf")
        generate_pdf("chain", components, [closing_fail], options, path)
        text = _extract_text(path)
        assert "FAIL" in text

    # Generous spec window -> should PASS
    closing_pass = ClosingEquation(id="z1", label="Z1", expression="B - D", specLimits=SpecLimits(usl=1.0, lsl=-1.0))
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "report_pass.pdf")
        generate_pdf("chain", components, [closing_pass], options, path)
        text = _extract_text(path)
        assert "PASS" in text


def test_pdf_no_spec_limits_states_not_evaluated():
    components = _shaft_housing_components()
    closing = ClosingEquation(id="z1", label="Z1", expression="B - D")  # no specLimits
    options = AnalysisOptions(methods=[AnalysisMethod.MONTE_CARLO], monteCarloRuns=2000, randomSeed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "report.pdf")
        generate_pdf("chain", components, [closing], options, path)
        text = _extract_text(path)
        assert "not evaluated" in text


def test_pdf_multi_gap_produces_a_page_per_closing_dimension():
    components = _shaft_housing_components()
    closings = [
        ClosingEquation(id="z1", label="Z1", expression="B - D"),
        ClosingEquation(id="z2", label="Z2", expression="B"),
    ]
    options = AnalysisOptions(methods=[AnalysisMethod.WORST_CASE], monteCarloRuns=2000, randomSeed=1)

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "report.pdf")
        generate_pdf("chain", components, closings, options, path)
        reader = pypdf.PdfReader(path)
        assert len(reader.pages) == 4  # cover + input-params page + Z1 page + Z2 page
