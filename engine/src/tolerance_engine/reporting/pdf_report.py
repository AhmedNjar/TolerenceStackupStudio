"""
PDF report generation via ReportLab, with chart images rendered by
matplotlib.

matplotlib MUST use the 'Agg' (non-interactive, raster-only) backend here —
this module runs inside a headless background engine process with no
display, and the default backend selection can try to open a GUI window or
simply fail in that environment. `matplotlib.use("Agg")` is set at import
time, before pyplot is imported, which is the only point it's guaranteed to
take effect.
"""
from __future__ import annotations

import datetime
import io
from typing import Any, Optional

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402 - must follow matplotlib.use()

from reportlab.lib import colors
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    Image,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from tolerance_engine.expression_engine.parser import build_symbols, parse_expression, partial_derivatives
from tolerance_engine.models import AnalysisMethod, AnalysisOptions, ClosingEquation, Component
from tolerance_engine.statistics.monte_carlo import monte_carlo
from tolerance_engine.statistics.rss import modified_rss, rss
from tolerance_engine.statistics.sensitivity import contribution_ranking
from tolerance_engine.statistics.worst_case import worst_case

_STYLES = getSampleStyleSheet()
_TITLE_STYLE = ParagraphStyle("ReportTitle", parent=_STYLES["Title"], fontSize=22, spaceAfter=6)
_H2 = _STYLES["Heading2"]
_BODY = _STYLES["BodyText"]


def generate_pdf(
    chain_id: str,
    components: list[Component],
    closing_equations: list[ClosingEquation],
    options: AnalysisOptions,
    output_path: str,
) -> None:
    doc = SimpleDocTemplate(output_path, pagesize=letter, title=f"Tolerance Stack-up Report — {chain_id}")
    story: list[Any] = []

    story.extend(_cover_page(chain_id, components, closing_equations))
    story.append(PageBreak())
    story.extend(_input_parameters_section(components))
    story.extend(_standard_compliance_section(components))

    for closing in closing_equations:
        story.append(PageBreak())
        story.extend(_closing_dimension_section(closing, components, options))

    doc.build(story)


def _cover_page(chain_id: str, components: list[Component], closing_equations: list[ClosingEquation]) -> list[Any]:
    generated = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    return [
        Spacer(1, 2 * inch),
        Paragraph("Tolerance Stack-up Analysis Report", _TITLE_STYLE),
        Spacer(1, 0.3 * inch),
        Paragraph(f"Chain: {chain_id}", _H2),
        Paragraph(f"Generated: {generated}", _BODY),
        Paragraph(f"Components: {len(components)}", _BODY),
        Paragraph(f"Closing dimensions: {len(closing_equations)}", _BODY),
    ]


def _input_parameters_section(components: list[Component]) -> list[Any]:
    story: list[Any] = [Paragraph("Input Parameters", _H2)]

    header = ["Label", "Name", "Kind", "Nominal", "+Upper", "-Lower", "Distribution"]
    rows = [header]
    for c in components:
        rows.append([
            c.label, c.name, c.kind.value, f"{c.nominal:.4f}",
            f"{c.upperTol:.4f}", f"{c.lowerTol:.4f}", c.distribution.value,
        ])

    table = Table(rows, repeatRows=1)
    table.setStyle(_table_style())
    story.append(table)
    story.append(Spacer(1, 0.2 * inch))
    return story


def _standard_compliance_section(components: list[Component]) -> list[Any]:
    with_fits = [c for c in components if c.standardFit is not None]
    story: list[Any] = [Paragraph("Standard Compliance", _H2)]
    if not with_fits:
        story.append(Paragraph("No components reference a standard fit.", _BODY))
        return story

    rows = [["Label", "Standard", "Designation"]]
    for c in with_fits:
        rows.append([c.label, c.standardFit.standard, c.standardFit.designation])
    table = Table(rows, repeatRows=1)
    table.setStyle(_table_style())
    story.append(table)
    story.append(Spacer(1, 0.2 * inch))
    return story


def _closing_dimension_section(closing: ClosingEquation, components: list[Component], options: AnalysisOptions) -> list[Any]:
    story: list[Any] = [
        Paragraph(f"Closing Dimension: {closing.label}", _H2),
        Paragraph(f"Equation: {closing.label} = {closing.expression}", _BODY),
        Spacer(1, 0.15 * inch),
    ]

    methods = set(options.methods)

    if AnalysisMethod.WORST_CASE in methods:
        wc = worst_case(closing.expression, components)
        story.append(Paragraph(
            f"Worst Case — nominal {wc['nominal']:.4f}, range [{wc['zMin']:.4f}, {wc['zMax']:.4f}]", _BODY,
        ))

    rss_result = None
    if AnalysisMethod.MODIFIED_RSS in methods:
        rss_result = modified_rss(closing.expression, components, options.modifiedRssShiftFactor)
        shift_note = f" (shift factor {rss_result['shiftFactorApplied']:.3f})" if rss_result["shiftFactorApplied"] else ""
        story.append(Paragraph(
            f"Modified RSS{shift_note} — sigma {rss_result['sigma']:.4f}, "
            f"predicted [{rss_result['zMinPredicted']:.4f}, {rss_result['zMaxPredicted']:.4f}]", _BODY,
        ))
    elif AnalysisMethod.RSS in methods:
        rss_result = rss(closing.expression, components)
        story.append(Paragraph(
            f"RSS — sigma {rss_result['sigma']:.4f}, "
            f"predicted [{rss_result['zMinPredicted']:.4f}, {rss_result['zMaxPredicted']:.4f}]", _BODY,
        ))

    mc_result = None
    if AnalysisMethod.MONTE_CARLO in methods:
        mc_result = monte_carlo(
            closing.expression, components, options.monteCarloRuns, closing.specLimits, options.randomSeed,
        )
        story.append(Spacer(1, 0.1 * inch))
        story.append(_monte_carlo_table(mc_result))
        story.append(Spacer(1, 0.15 * inch))
        story.append(Image(_histogram_chart_png(mc_result, closing.label), width=5.5 * inch, height=3 * inch))

    contributions = contribution_ranking(closing.expression, components)
    if contributions:
        story.append(Spacer(1, 0.15 * inch))
        story.append(Paragraph("Variance Contribution (Pareto)", _H2))
        story.append(Image(_pareto_chart_png(contributions, closing.label), width=5.5 * inch, height=3 * inch))

    story.append(Spacer(1, 0.2 * inch))
    story.append(_pass_fail_summary(closing, mc_result))

    return story


def _monte_carlo_table(mc: dict[str, Any]) -> Table:
    rows = [
        ["Runs", "Mean", "Std Dev", "Skewness", "Kurtosis", "Cp", "Cpk", "Yield %", "DPPM"],
        [
            str(mc["runs"]), f"{mc['mean']:.4f}", f"{mc['stdDev']:.4f}",
            f"{mc['skewness']:.3f}", f"{mc['kurtosis']:.3f}",
            f"{mc['cp']:.3f}" if mc["cp"] is not None else "—",
            f"{mc['cpk']:.3f}" if mc["cpk"] is not None else "—",
            f"{mc['yieldPct']:.4f}", f"{mc['dppm']:.2f}",
        ],
    ]
    table = Table(rows, repeatRows=1)
    table.setStyle(_table_style())
    return table


def _pass_fail_summary(closing: ClosingEquation, mc_result: Optional[dict[str, Any]]) -> Paragraph:
    if closing.specLimits is None:
        return Paragraph("Pass/Fail: not evaluated — no spec limits (USL/LSL) defined for this closing dimension.", _BODY)
    if mc_result is None:
        return Paragraph("Pass/Fail: not evaluated — Monte Carlo was not run for this closing dimension.", _BODY)

    passed = mc_result["cpk"] is not None and mc_result["cpk"] >= 1.0
    verdict = "PASS" if passed else "FAIL"
    color = "green" if passed else "red"
    return Paragraph(
        f'<font color="{color}"><b>{verdict}</b></font> — Cpk {mc_result["cpk"]:.3f} '
        f'({"meets" if passed else "does not meet"} the common Cpk ≥ 1.0 threshold); '
        f'yield {mc_result["yieldPct"]:.4f}%, {mc_result["dppm"]:.2f} DPPM.',
        _BODY,
    )


def _table_style() -> TableStyle:
    return TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#37474F")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTSIZE", (0, 0), (-1, -1), 8),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F5F5F5")]),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ])


def _histogram_chart_png(mc_result: dict[str, Any], closing_label: str) -> io.BytesIO:
    bin_edges = mc_result["histogram"]["binEdges"]
    counts = mc_result["histogram"]["counts"]
    centers = [(bin_edges[i] + bin_edges[i + 1]) / 2 for i in range(len(counts))]
    width = bin_edges[1] - bin_edges[0] if len(bin_edges) > 1 else 1.0

    fig, ax = plt.subplots(figsize=(6, 3.2), dpi=150)
    ax.bar(centers, counts, width=width, color="#1976D2", edgecolor="none")
    ax.set_title(f"{closing_label} — Monte Carlo distribution ({mc_result['runs']} runs)")
    ax.set_xlabel("Value")
    ax.set_ylabel("Count")
    fig.tight_layout()

    buf = io.BytesIO()
    fig.savefig(buf, format="png")
    plt.close(fig)
    buf.seek(0)
    return buf


def _pareto_chart_png(contributions: list[dict[str, Any]], closing_label: str) -> io.BytesIO:
    labels = [c["componentId"] for c in contributions]
    values = [c["contributionPct"] for c in contributions]
    cumulative = []
    running = 0.0
    for v in values:
        running += v
        cumulative.append(running)

    fig, ax1 = plt.subplots(figsize=(6, 3.2), dpi=150)
    ax1.bar(labels, values, color="#546E7A")
    ax1.set_ylabel("Contribution %")
    ax1.set_xlabel("Component")
    ax1.set_title(f"{closing_label} — Variance contribution (Pareto)")

    ax2 = ax1.twinx()
    ax2.plot(labels, cumulative, color="#D32F2F", marker="o")
    ax2.set_ylabel("Cumulative %")
    ax2.set_ylim(0, 110)
    fig.tight_layout()

    buf = io.BytesIO()
    fig.savefig(buf, format="png")
    plt.close(fig)
    buf.seek(0)
    return buf
