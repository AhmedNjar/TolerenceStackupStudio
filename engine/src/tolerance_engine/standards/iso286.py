"""
ISO 286 (Geometrical Product Specification — ISO Code System for
Tolerances on Linear Sizes).

Two genuinely different kinds of data live in ISO 286, and this module is
honest about which one it can compute vs. which one it would have to
hardcode from memory:

1. IT GRADE WIDTH (the size of the tolerance zone, e.g. "IT7") is defined by
   a closed-form formula in ISO 286-1, not a hardcoded table — the standard's
   own published tables are themselves computed from this formula and then
   rounded. That formula is implemented here directly (`it_grade_width_um`),
   so its correctness doesn't depend on transcription accuracy.

2. FUNDAMENTAL DEVIATION (where that zone sits relative to the basic size,
   e.g. the "g" in "g6") is a large, mostly-empirical table spanning ~13 size
   ranges x ~25 letters in the full standard. Three positions are exact by
   definition and implemented exactly: H (holes, deviation = 0), h (shafts,
   deviation = 0), and JS/js (symmetric, deviation = ±IT/2). For every other
   letter (f, g, k, n, p, s, ...), this module deliberately does NOT return
   a number — reproducing that table from memory risks silent, hard-to-spot
   transcription errors in numbers a real design might depend on. Callers
   asking for a non-exact letter get an explicit UNSUPPORTED_DEVIATION_LETTER
   error instead of a guessed value. Populate those from your organization's
   validated copy of ISO 286-1 if/when you need them — see
   FUNDAMENTAL_DEVIATION_TODO below for where that data would plug in.
"""
from __future__ import annotations

import math
from typing import Any

# (lower_exclusive_mm, upper_inclusive_mm) — the standard ISO 286-1 size
# ranges up to 500mm. The very first range technically starts "above 0"; the
# geometric-mean formula is degenerate at 0, so this uses the range's own
# upper bound as D for that one range specifically (a common practical
# convention, called out here rather than left silently unexplained).
_SIZE_RANGES_MM: list[tuple[float, float]] = [
    (0, 3), (3, 6), (6, 10), (10, 18), (18, 30), (30, 50), (50, 80),
    (80, 120), (120, 180), (180, 250), (250, 315), (315, 400), (400, 500),
]

# IT grade -> multiplier of the tolerance unit i. ISO 286-1 defines IT5-IT18;
# only IT5-IT16 are implemented (the practically relevant range for fits —
# IT01-IT4 are gauge-block-level precision, IT17-IT18 are very coarse, both
# rare for the kind of mechanical fits this app targets).
_IT_GRADE_MULTIPLIERS: dict[int, float] = {
    5: 7, 6: 10, 7: 16, 8: 25, 9: 40, 10: 64,
    11: 100, 12: 160, 13: 250, 14: 400, 15: 640, 16: 1000,
}

# Deviation letters this module computes exactly, by definition (no table
# lookup needed): H/h are always 0; JS/js are always symmetric about the
# basic size at ±IT/2. Everything else is intentionally unsupported — see
# module docstring.
_EXACT_ZERO_LETTERS = {"H", "h"}
_EXACT_SYMMETRIC_LETTERS = {"JS", "js"}


class StandardLookupError(ValueError):
    pass


def _size_range_for(basic_size_mm: float) -> tuple[float, float]:
    for low, high in _SIZE_RANGES_MM:
        if low < basic_size_mm <= high:
            return low, high
    raise StandardLookupError(
        f"basicSize {basic_size_mm}mm is outside the supported ISO 286 range (0, 500]mm"
    )


def _geometric_mean_diameter(size_range: tuple[float, float]) -> float:
    low, high = size_range
    if low == 0:
        return float(high)  # see _SIZE_RANGES_MM comment
    return math.sqrt(low * high)


def it_grade_width_um(basic_size_mm: float, grade: int) -> float:
    """Tolerance zone width for the given IT grade, in micrometers, per the
    ISO 286-1 formula i = 0.45*D^(1/3) + 0.001*D (D = geometric mean of the
    size range, mm), IT_n = multiplier(n) * i."""
    if grade not in _IT_GRADE_MULTIPLIERS:
        raise StandardLookupError(
            f"IT grade {grade} is not supported (supported: "
            f"{sorted(_IT_GRADE_MULTIPLIERS)}). IT01-IT4 and IT17-IT18 are out of "
            "scope — see module docstring."
        )
    d = _geometric_mean_diameter(_size_range_for(basic_size_mm))
    i = 0.45 * (d ** (1 / 3)) + 0.001 * d
    return _IT_GRADE_MULTIPLIERS[grade] * i


def fit_tolerance_mm(basic_size_mm: float, designation: str) -> dict[str, Any]:
    """designation like 'H7', 'g6', 'js6'. Returns upperTol/lowerTol in mm
    (both as positive-or-negative deviations from basic size, matching this
    app's Component.upperTol (>=0) / lowerTol (>=0, stored as a positive
    magnitude) convention) plus which deviation letters are exact vs. would
    need external data."""
    letter, grade_str = _split_designation(designation)
    grade = int(grade_str)

    it_width_mm = it_grade_width_um(basic_size_mm, grade) / 1000.0

    if letter in _EXACT_ZERO_LETTERS:
        is_hole = letter.isupper()
        if is_hole:  # H: zone entirely above basic size
            return {"upperTol": it_width_mm, "lowerTol": 0.0, "exact": True}
        else:  # h: zone entirely below basic size
            return {"upperTol": 0.0, "lowerTol": it_width_mm, "exact": True}

    if letter in _EXACT_SYMMETRIC_LETTERS:
        half = it_width_mm / 2.0
        return {"upperTol": half, "lowerTol": half, "exact": True}

    raise StandardLookupError(
        f"Deviation letter '{letter}' is not computed by this module — only H, h, "
        "JS, js are exact-by-definition and implemented. Other letters (f, g, k, n, "
        "p, s, ...) require ISO 286-1's fundamental deviation table, which this "
        "module deliberately does not hardcode from memory (see standards/iso286.py "
        "module docstring) to avoid silently shipping a possibly-wrong number in an "
        "authoritative-looking standards lookup."
    )


def _split_designation(designation: str) -> tuple[str, str]:
    letter = "".join(ch for ch in designation if ch.isalpha())
    grade = "".join(ch for ch in designation if ch.isdigit())
    if not letter or not grade:
        raise StandardLookupError(f"Could not parse fit designation '{designation}' (expected e.g. 'H7', 'g6')")
    return letter, grade
