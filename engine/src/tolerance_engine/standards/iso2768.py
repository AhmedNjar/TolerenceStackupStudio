"""
ISO 2768-1 general tolerances for linear dimensions (the "don't dimension
every single feature" tolerance classes: f=fine, m=medium, c=coarse,
v=very coarse).

This is a compact, widely-reproduced table (unlike ISO 286's fundamental
deviations — see iso286.py's docstring for why that one is NOT hardcoded
here). Confidence in these specific numbers is reasonably high, but they are
still transcribed from memory rather than computed from a formula the way
ISO 286's IT grades are — cross-check against your own copy of ISO 2768-1
before relying on this for a safety-critical or contractually-toleranced
dimension. Angular tolerances (ISO 2768-1 also covers angles, in
degrees/minutes) are deliberately NOT included here; that table wasn't
reproduced with enough confidence to ship.
"""
from __future__ import annotations

from typing import Optional

# (lower_exclusive_mm, upper_inclusive_mm) -> {class: ± tolerance in mm}.
# `None` means that class has no defined tolerance for that size range in
# the standard (e.g. "f" isn't defined below 0.5mm or above 2000mm; "v"
# isn't defined below 3mm).
_TABLE: list[tuple[tuple[float, float], dict[str, Optional[float]]]] = [
    ((0.5, 3), {"f": 0.05, "m": 0.10, "c": 0.20, "v": None}),
    ((3, 6), {"f": 0.05, "m": 0.10, "c": 0.30, "v": 0.5}),
    ((6, 30), {"f": 0.10, "m": 0.20, "c": 0.50, "v": 1.0}),
    ((30, 120), {"f": 0.15, "m": 0.30, "c": 0.80, "v": 1.5}),
    ((120, 400), {"f": 0.20, "m": 0.50, "c": 1.20, "v": 2.5}),
    ((400, 1000), {"f": 0.30, "m": 0.80, "c": 2.00, "v": 4.0}),
    ((1000, 2000), {"f": 0.50, "m": 1.20, "c": 3.00, "v": 6.0}),
    ((2000, 4000), {"f": None, "m": 2.00, "c": 4.00, "v": 8.0}),
]

_VALID_CLASSES = {"f", "m", "c", "v"}


class GeneralToleranceError(ValueError):
    pass


def general_tolerance_mm(nominal_size_mm: float, tolerance_class: str) -> float:
    """Returns the ± tolerance in mm for the given class and nominal size."""
    tolerance_class = tolerance_class.lower()
    if tolerance_class not in _VALID_CLASSES:
        raise GeneralToleranceError(f"Unknown ISO 2768 class '{tolerance_class}' (expected one of f, m, c, v)")

    for (low, high), by_class in _TABLE:
        if low < nominal_size_mm <= high:
            value = by_class[tolerance_class]
            if value is None:
                raise GeneralToleranceError(
                    f"ISO 2768 class '{tolerance_class}' is not defined for size "
                    f"range ({low}, {high}]mm"
                )
            return value

    raise GeneralToleranceError(f"nominal size {nominal_size_mm}mm is outside the supported ISO 2768 range (0.5, 4000]mm")
