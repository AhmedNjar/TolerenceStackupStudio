"""
Shared distribution handling for the statistics package.

Two different representations are needed and they are NOT the same thing:

  * RSS (analytic) needs a symmetric-equivalent sigma per component, because
    the closed-form RSS formula assumes each contributor is a zero-mean
    random variable with some sigma. Asymmetric tolerances (+0.021/-0.000)
    are handled by splitting them into a *mean shift* (how far the
    distribution's center sits from nominal) plus a *symmetric half-range*
    that sigma is derived from.

  * Monte Carlo does NOT need that approximation — it samples the actual
    bounded/asymmetric distribution directly (e.g. a triangular distribution
    with its true left/mode/right), which is more accurate than routing
    through a symmetric-sigma equivalent. Both paths are implemented here
    so they share one definition of what each DistributionType means.
"""
from __future__ import annotations

import numpy as np

from tolerance_engine.models import Component, DistributionType

# Divisor applied to the symmetric half-range to get sigma, per distribution
# family, under the assumption that the stated tolerance band corresponds to
# the distribution's natural full spread (e.g. NORMAL_3S: tolerance == ±3σ).
_NORMAL_SIGMA_DIVISOR = {
    DistributionType.NORMAL_3S: 3.0,
    DistributionType.NORMAL_6S: 6.0,
}


class ComponentStats:
    """Symmetric-equivalent stats for one component, for the RSS/analytic path."""

    __slots__ = ("center", "mean_shift", "half_range", "sigma")

    def __init__(self, center: float, mean_shift: float, half_range: float, sigma: float):
        self.center = center            # effective (thermal-adjusted) nominal
        self.mean_shift = mean_shift    # distribution mean = center + mean_shift
        self.half_range = half_range    # (upperTol + lowerTol) / 2
        self.sigma = sigma


def component_stats(component: Component) -> ComponentStats:
    center = component.effective_nominal()
    half_range = (component.upperTol + component.lowerTol) / 2.0
    mean_shift = (component.upperTol - component.lowerTol) / 2.0

    if component.distribution in _NORMAL_SIGMA_DIVISOR:
        sigma = half_range / _NORMAL_SIGMA_DIVISOR[component.distribution]
    elif component.distribution == DistributionType.UNIFORM:
        sigma = half_range / np.sqrt(3.0)
    elif component.distribution == DistributionType.TRIANGULAR:
        sigma = half_range / np.sqrt(6.0)
    else:  # pragma: no cover - guarded by pydantic enum validation upstream
        raise ValueError(f"Unhandled distribution type: {component.distribution}")

    return ComponentStats(center=center, mean_shift=mean_shift, half_range=half_range, sigma=sigma)


def sample_component(component: Component, n: int, rng: np.random.Generator) -> np.ndarray:
    """Draws n samples from the component's *actual* bounded distribution
    (not the symmetric-sigma approximation used by RSS)."""
    center = component.effective_nominal()
    low = center - component.lowerTol
    high = center + component.upperTol

    if component.distribution == DistributionType.UNIFORM:
        return rng.uniform(low, high, size=n)

    if component.distribution == DistributionType.TRIANGULAR:
        # Peak (mode) assumed at nominal, per standard tolerance-stack convention.
        mode = np.clip(center, low, high)
        return rng.triangular(low, mode, high, size=n)

    if component.distribution in _NORMAL_SIGMA_DIVISOR:
        stats = component_stats(component)
        return rng.normal(loc=stats.center + stats.mean_shift, scale=stats.sigma, size=n)

    raise ValueError(f"Unhandled distribution type: {component.distribution}")  # pragma: no cover
