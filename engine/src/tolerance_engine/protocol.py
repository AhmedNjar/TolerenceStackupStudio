"""
Routes an inbound envelope's `type` to a handler function and wraps the
handler's return value back into a response envelope. Handlers themselves
live in handlers.py and stay free of socket/framing concerns.
"""
from __future__ import annotations

from typing import Any, Callable

from tolerance_engine import handlers

_HANDLERS: dict[str, Callable[[dict], dict]] = {
    "PING": handlers.handle_ping,
    "ANALYZE_STACKUP": handlers.handle_analyze_stackup,
    "OPTIMIZE_TOLERANCES": handlers.handle_optimize_tolerances,
    "GET_STANDARD_TABLE": handlers.handle_get_standard_table,
    "LIST_TEMPLATES": handlers.handle_list_templates,
    "INSTANTIATE_TEMPLATE": handlers.handle_instantiate_template,
    "GENERATE_PDF_REPORT": handlers.handle_generate_pdf_report,
    "GENERATE_EXCEL_REPORT": handlers.handle_generate_excel_report,
}

_RESULT_TYPE_SUFFIX = {
    "PING": "PONG",
    "ANALYZE_STACKUP": "ANALYZE_STACKUP_RESULT",
    "OPTIMIZE_TOLERANCES": "OPTIMIZE_TOLERANCES_RESULT",
    "GET_STANDARD_TABLE": "GET_STANDARD_TABLE_RESULT",
    "LIST_TEMPLATES": "LIST_TEMPLATES_RESULT",
    "INSTANTIATE_TEMPLATE": "INSTANTIATE_TEMPLATE_RESULT",
    "GENERATE_PDF_REPORT": "GENERATE_PDF_REPORT_RESULT",
    "GENERATE_EXCEL_REPORT": "GENERATE_EXCEL_REPORT_RESULT",
}


def dispatch(envelope: dict) -> dict[str, Any]:
    request_type = envelope.get("type")
    handler = _HANDLERS.get(request_type)
    if handler is None:
        return {
            "requestId": envelope.get("requestId"),
            "type": "ERROR",
            "success": False,
            "error": {"code": "UNKNOWN_TYPE", "message": f"No handler for '{request_type}'"},
            "payload": {},
        }

    result_payload = handler(envelope.get("payload", {}))
    return {
        "requestId": envelope.get("requestId"),
        "type": _RESULT_TYPE_SUFFIX[request_type],
        "success": True,
        "error": None,
        "payload": result_payload,
    }
