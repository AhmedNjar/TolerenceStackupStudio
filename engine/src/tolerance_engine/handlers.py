"""
Envelope-type handlers. Each function takes the request payload dict and
returns the response payload dict; protocol.dispatch() wraps these in the
envelope. Handlers stay thin — the actual math lives in expression_engine/,
statistics/, and optimization/.
"""
from __future__ import annotations

import os
from typing import Any

from tolerance_engine.expression_engine import templates as template_lib
from tolerance_engine.expression_engine.parser import ExpressionError
from tolerance_engine.models import (
    AnalysisMethod,
    AnalyzeStackupRequest,
    GenerateReportRequest,
    OptimizeTolerancesRequest,
)
from tolerance_engine.optimization.tolerance_optimizer import optimize_tolerances
from tolerance_engine.reporting.excel_export import build_workbook
from tolerance_engine.reporting.pdf_report import generate_pdf
from tolerance_engine.standards import ansi_b4_1, iso286, iso2768
from tolerance_engine.statistics.monte_carlo import monte_carlo
from tolerance_engine.statistics.rss import modified_rss, rss
from tolerance_engine.statistics.sensitivity import contribution_ranking
from tolerance_engine.statistics.worst_case import worst_case


def handle_ping(payload: dict) -> dict:
    return {"echo": payload}


def handle_analyze_stackup(payload: dict) -> dict:
    request = AnalyzeStackupRequest.model_validate(payload)
    methods = set(request.options.methods)

    results = []
    for closing in request.closingEquations:
        result: dict[str, Any] = {"closingId": closing.id}
        try:
            if AnalysisMethod.WORST_CASE in methods:
                result["worstCase"] = worst_case(closing.expression, request.components)
            else:
                result["worstCase"] = None

            if AnalysisMethod.RSS in methods:
                result["rss"] = rss(closing.expression, request.components)
            elif AnalysisMethod.MODIFIED_RSS in methods:
                result["rss"] = modified_rss(
                    closing.expression, request.components, request.options.modifiedRssShiftFactor
                )
            else:
                result["rss"] = None

            if AnalysisMethod.MONTE_CARLO in methods:
                result["monteCarlo"] = monte_carlo(
                    closing.expression,
                    request.components,
                    request.options.monteCarloRuns,
                    closing.specLimits,
                    request.options.randomSeed,
                )
            else:
                result["monteCarlo"] = None

            result["contributions"] = contribution_ranking(closing.expression, request.components)
        except ExpressionError as exc:
            result["error"] = str(exc)
            result["worstCase"] = result.get("worstCase")
            result["rss"] = result.get("rss")
            result["monteCarlo"] = result.get("monteCarlo")
            result["contributions"] = []

        results.append(result)

    return {"chainId": request.chainId, "results": results}


def handle_optimize_tolerances(payload: dict) -> dict:
    request = OptimizeTolerancesRequest.model_validate(payload)
    return optimize_tolerances(
        expression=request.closingEquation.expression,
        components=request.components,
        usl=request.closingEquation.specLimits.usl,
        lsl=request.closingEquation.specLimits.lsl,
        target_yield_pct=request.targetYieldPct,
        optimizable=request.optimizable,
        monte_carlo_runs=request.monteCarloVerificationRuns,
        seed=request.randomSeed,
    )


def handle_list_templates(payload: dict) -> dict:
    return {
        "templates": [
            {
                "id": t.id,
                "name": t.name,
                "description": t.description,
                "roles": [
                    {
                        "roleId": r.role_id,
                        "labelHint": r.label_hint,
                        "kind": r.kind,
                        "description": r.description,
                    }
                    for r in t.roles
                ],
            }
            for t in template_lib.list_templates()
        ]
    }


def handle_instantiate_template(payload: dict) -> dict:
    template_id = payload["templateId"]
    role_bindings = payload["roleBindings"]
    expression = template_lib.instantiate(template_id, role_bindings)
    return {"templateId": template_id, "expression": expression}


def handle_get_standard_table(payload: dict) -> dict:
    standard = payload.get("standard")

    if standard == "ISO286":
        result = iso286.fit_tolerance_mm(payload["basicSize"], payload["designation"])
        return {"standard": "ISO286", **result}

    if standard == "ISO2768":
        tol = iso2768.general_tolerance_mm(payload["nominalSize"], payload["toleranceClass"])
        return {"standard": "ISO2768", "upperTol": tol, "lowerTol": tol, "exact": True}

    if standard == "ANSI_B4_1":
        result = ansi_b4_1.lookup(payload["basicSize"], payload["fitClass"])
        return {"standard": "ANSI_B4_1", **result}

    raise ValueError(f"Unknown or unsupported standard: {standard!r} (expected ISO286, ISO2768, or ANSI_B4_1)")


def handle_generate_pdf_report(payload: dict) -> dict:
    request = GenerateReportRequest.model_validate(payload)
    generate_pdf(request.chainId, request.components, request.closingEquations, request.options, request.outputPath)
    return {"outputPath": request.outputPath, "fileSizeBytes": os.path.getsize(request.outputPath)}


def handle_generate_excel_report(payload: dict) -> dict:
    request = GenerateReportRequest.model_validate(payload)
    workbook = build_workbook(
        request.components, request.closingEquations, request.options.monteCarloRuns, request.options.randomSeed,
    )
    workbook.save(request.outputPath)
    return {"outputPath": request.outputPath, "fileSizeBytes": os.path.getsize(request.outputPath)}
