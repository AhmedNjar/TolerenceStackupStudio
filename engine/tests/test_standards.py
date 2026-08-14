import pytest

from tolerance_engine.standards import iso2768, iso286
from tolerance_engine.standards.iso286 import StandardLookupError
from tolerance_engine.standards.iso2768 import GeneralToleranceError


def test_it_grade_width_known_reference_value():
    # IT7 at 30mm nominal (18-30mm range, D=sqrt(18*30)=23.24mm) is a
    # commonly-cited textbook reference point: i = 0.45*23.24^(1/3) + 0.001*23.24
    # ≈ 1.31 + 0.023 ≈ 1.31um... multiplied by IT7's factor of 16 -> ~21um.
    # This checks the formula lands in the right ballpark of the widely
    # published IT7-at-30mm value of 21 micrometers (0.021mm).
    width_um = iso286.it_grade_width_um(30.0, 7)
    assert width_um == pytest.approx(21.0, rel=0.1)


def test_it_grade_width_increases_with_grade_number():
    widths = [iso286.it_grade_width_um(30.0, g) for g in (5, 6, 7, 8, 9, 10)]
    assert widths == sorted(widths)  # coarser grade -> wider tolerance zone


def test_it_grade_width_increases_with_size():
    small = iso286.it_grade_width_um(5.0, 7)
    large = iso286.it_grade_width_um(400.0, 7)
    assert large > small


def test_hole_basis_H_zone_entirely_above_nominal():
    result = iso286.fit_tolerance_mm(30.0, "H7")
    assert result["lowerTol"] == 0.0
    assert result["upperTol"] > 0.0
    assert result["exact"] is True


def test_shaft_basis_h_zone_entirely_below_nominal():
    result = iso286.fit_tolerance_mm(30.0, "h6")
    assert result["upperTol"] == 0.0
    assert result["lowerTol"] > 0.0
    assert result["exact"] is True


def test_js_is_symmetric():
    result = iso286.fit_tolerance_mm(30.0, "js6")
    assert result["upperTol"] == pytest.approx(result["lowerTol"])
    assert result["upperTol"] > 0.0


def test_unsupported_deviation_letter_raises_rather_than_guessing():
    # 'g' is a real ISO 286 letter but this module deliberately doesn't
    # fabricate its fundamental deviation from memory — must raise, not
    # silently return some plausible-looking number.
    with pytest.raises(StandardLookupError):
        iso286.fit_tolerance_mm(30.0, "g6")


def test_unsupported_grade_raises():
    with pytest.raises(StandardLookupError):
        iso286.it_grade_width_um(30.0, 2)  # IT2 out of supported range


def test_size_outside_range_raises():
    with pytest.raises(StandardLookupError):
        iso286.it_grade_width_um(600.0, 7)


def test_iso2768_medium_class_known_reference_value():
    # ISO 2768-m, 6-30mm range -> ±0.2mm is a widely-cited reference value.
    assert iso2768.general_tolerance_mm(20.0, "m") == pytest.approx(0.2)


def test_iso2768_coarser_class_is_wider():
    f = iso2768.general_tolerance_mm(50.0, "f")
    m = iso2768.general_tolerance_mm(50.0, "m")
    c = iso2768.general_tolerance_mm(50.0, "c")
    v = iso2768.general_tolerance_mm(50.0, "v")
    assert f < m < c < v


def test_iso2768_undefined_class_for_size_raises():
    with pytest.raises(GeneralToleranceError):
        iso2768.general_tolerance_mm(1.0, "v")  # v not defined below 3mm
