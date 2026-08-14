"""
ANSI B4.1 (Preferred Limits and Fits for Cylindrical Parts) — RC/LC/LT/LN/FN
fit classes.

Not populated. This standard's data (per-class, per-size-range limits in
inches) is at least as extensive as ISO 286's fundamental deviation table
and wasn't reproduced with enough confidence to ship — same reasoning as
iso286.py's non-H/h/JS deviation letters. Wire real class/size-range data in
here from a validated source (ANSI/ASME B4.1-1967(R2009) or a licensed
reference table) when needed; `lookup()` already has the shape callers
expect so that's a drop-in data addition, not a contract change.
"""
from __future__ import annotations

from typing import Any


class AnsiFitError(ValueError):
    pass


def lookup(basic_size_in: float, fit_class: str) -> dict[str, Any]:
    raise AnsiFitError(
        "ANSI B4.1 fit tables are not implemented — this standard's per-class, "
        "per-size-range limit data was not reproduced from memory with enough "
        "confidence to ship in an authoritative standards lookup. Populate "
        "standards/ansi_b4_1.py from a validated source (ANSI/ASME B4.1) to enable it."
    )
