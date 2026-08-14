"""
Inverse tolerance optimization: given a target yield, find the individual
component tolerances that minimize total manufacturing cost.

  minimize   sum_i  k_i / T_i^p_i
  subject to P(LSL <= Z <= USL) >= target_yield      (T_i in [minTol_i, maxTol_i])

The constraint inside the optimizer loop uses the fast RSS/normal-
approximation model (statistics/rss.py's underlying math), not a full Monte
Carlo — running 20k-100k-sample Monte Carlo on every SLSQP iteration would
make this too slow to be interactive. Each optimizable component is
allocated a *symmetric* tolerance (upperTol = lowerTol = T_i); asymmetric
allocation is a further degree of freedom this Phase-2 optimizer doesn't
explore. Once SLSQP converges, the optimized tolerances are plugged back
into a real Monte Carlo run (statistics/monte_carlo.py) so the reported
result is verified against simulation, not just the analytic proxy — if the
stack's skewness pulls the two yield numbers apart, both are surfaced so
that gap is visible rather than hidden.
"""
from __future__ import annotations

import copy
from typing import Any

import numpy as np
from scipy import stats as sp_stats
from scipy.optimize import minimize

from tolerance_engine.expression_engine.parser import build_symbols, parse_expression, partial_derivatives
from tolerance_engine.models import Component, CostToleranceParams, DistributionType
from tolerance_engine.statistics.monte_carlo import monte_carlo

_SIGMA_DIVISOR = {
    DistributionType.NORMAL_3S: 3.0,
    DistributionType.NORMAL_6S: 6.0,
    DistributionType.UNIFORM: np.sqrt(3.0),
    DistributionType.TRIANGULAR: np.sqrt(6.0),
}


def _sigma_for_symmetric_tolerance(distribution: DistributionType, tol: float) -> float:
    return tol / _SIGMA_DIVISOR[distribution]


def optimize_tolerances(
    expression: str,
    components: list[Component],
    usl: float,
    lsl: float,
    target_yield_pct: float,
    optimizable: list[CostToleranceParams],
    monte_carlo_runs: int,
    seed: int | None,
) -> dict[str, Any]:
    symbols = build_symbols(components)
    expr = parse_expression(expression, symbols)
    derivatives = partial_derivatives(expr, symbols, components)

    by_id = {c.id: c for c in components}
    opt_ids = [o.componentId for o in optimizable]
    fixed_components = [c for c in components if c.id not in opt_ids]

    # Nominal (fixed by construction — tolerance allocation doesn't move the
    # nominal build point) and the mean-shift contribution from fixed
    # (possibly asymmetric) components only; optimizable components are
    # allocated symmetrically, so they contribute zero mean shift.
    nominal = float(expr.evalf(subs={symbols[c.label]: c.effective_nominal() for c in components}))
    fixed_mean_shift = sum(
        derivatives[c.label] * ((c.upperTol - c.lowerTol) / 2.0) for c in fixed_components
    )
    fixed_variance = sum(
        (derivatives[c.label] * (((c.upperTol + c.lowerTol) / 2.0) / _SIGMA_DIVISOR[c.distribution])) ** 2
        for c in fixed_components
    )

    def sigma_z(tolerances: np.ndarray) -> float:
        variance = fixed_variance
        for opt, t in zip(optimizable, tolerances):
            comp = by_id[opt.componentId]
            sigma_i = _sigma_for_symmetric_tolerance(comp.distribution, t)
            variance += (derivatives[comp.label] * sigma_i) ** 2
        return float(np.sqrt(variance))

    def predicted_yield_pct(tolerances: np.ndarray) -> float:
        mean_z = nominal + fixed_mean_shift
        s = sigma_z(tolerances)
        if s == 0:
            return 100.0 if lsl <= mean_z <= usl else 0.0
        return 100.0 * float(sp_stats.norm.cdf(usl, mean_z, s) - sp_stats.norm.cdf(lsl, mean_z, s))

    def cost(tolerances: np.ndarray) -> float:
        return float(sum(o.costCoefficientK / (t ** o.costExponentP) for o, t in zip(optimizable, tolerances)))

    def yield_constraint(tolerances: np.ndarray) -> float:
        return predicted_yield_pct(tolerances) - target_yield_pct

    bounds = [(o.minTol, o.maxTol) for o in optimizable]

    # SLSQP's result is starting-point sensitive for this problem (verified
    # empirically: starting exactly at the components' current tolerances can
    # converge to a poor local point while still reporting success=True — a
    # silently-suboptimal "success" is worse than a slower correct answer).
    # Multi-start from a small spread of candidate starting points and keep
    # the best FEASIBLE result (constraint actually satisfied), rather than
    # trusting the first result SLSQP reports as converged.
    candidate_starts = [
        np.array([(lo + hi) / 2.0 for lo, hi in bounds]),
        np.array([lo for lo, _ in bounds]),
        np.array([hi for _, hi in bounds]),
        np.array([np.clip(by_id[o.componentId].upperTol or o.minTol, o.minTol, o.maxTol) for o in optimizable]),
    ]

    _FEASIBILITY_SLACK = 1e-6
    best_result = None
    for x0 in candidate_starts:
        candidate = minimize(
            cost,
            x0,
            method="SLSQP",
            bounds=bounds,
            constraints=[{"type": "ineq", "fun": yield_constraint}],
            options={"maxiter": 300, "ftol": 1e-12},
        )
        feasible = candidate.success and yield_constraint(candidate.x) >= -_FEASIBILITY_SLACK
        if feasible and (best_result is None or candidate.fun < best_result.fun):
            best_result = candidate

    if best_result is None:
        # No starting point found a feasible point — the target yield is
        # likely unreachable within the given tolerance bounds. Report the
        # closest attempt rather than silently returning a bogus "success".
        best_result = min(
            (
                minimize(cost, x0, method="SLSQP", bounds=bounds,
                          constraints=[{"type": "ineq", "fun": yield_constraint}],
                          options={"maxiter": 300, "ftol": 1e-12})
                for x0 in candidate_starts
            ),
            key=lambda r: -yield_constraint(r.x),  # least-negative constraint violation
        )
        best_result.success = False
        best_result.message = (
            "No candidate starting point reached the target yield within the given "
            "tolerance bounds — the target may be infeasible with these min/max tolerances. "
            f"{best_result.message}"
        )

    result = best_result

    optimized_tolerances = [
        {"componentId": o.componentId, "upperTol": float(t), "lowerTol": float(t)}
        for o, t in zip(optimizable, result.x)
    ]

    # Verify against a real Monte Carlo run using the optimized tolerances.
    verified_components = copy.deepcopy(components)
    tol_by_id = {ot["componentId"]: ot["upperTol"] for ot in optimized_tolerances}
    for c in verified_components:
        if c.id in tol_by_id:
            c.upperTol = tol_by_id[c.id]
            c.lowerTol = tol_by_id[c.id]

    from tolerance_engine.models import SpecLimits  # local import avoids a cycle at module load

    mc_verification = monte_carlo(
        expression, verified_components, monte_carlo_runs, SpecLimits(usl=usl, lsl=lsl), seed
    )

    return {
        "success": bool(result.success),
        "message": str(result.message),
        "iterations": int(result.nit),
        "optimizedTolerances": optimized_tolerances,
        "totalCost": float(result.fun),
        "predictedYieldPct": predicted_yield_pct(result.x),
        "monteCarloVerification": {
            "yieldPct": mc_verification["yieldPct"],
            "cp": mc_verification["cp"],
            "cpk": mc_verification["cpk"],
            "skewness": mc_verification["skewness"],
            "kurtosis": mc_verification["kurtosis"],
            "runs": mc_verification["runs"],
        },
    }
