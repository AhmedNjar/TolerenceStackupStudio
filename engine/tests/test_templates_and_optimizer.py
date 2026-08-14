import pytest

from tolerance_engine.expression_engine import templates as template_lib
from tolerance_engine.expression_engine.parser import ExpressionError
from tolerance_engine.models import Component, ComponentKind, CostToleranceParams, DistributionType
from tolerance_engine.optimization.tolerance_optimizer import optimize_tolerances


def test_all_templates_instantiate_and_parse():
    from tolerance_engine.expression_engine.parser import build_symbols, parse_expression

    for template in template_lib.list_templates():
        bindings = {role.role_id: role.label_hint for role in template.roles}
        expression = template_lib.instantiate(template.id, bindings)

        components = [
            Component(
                id=f"id-{role.label_hint}", label=role.label_hint,
                kind=ComponentKind.LINEAR if role.kind == "LINEAR" else ComponentKind.ANGULAR,
                nominal=10.0, upperTol=0.1, lowerTol=0.1, distribution=DistributionType.NORMAL_3S,
            )
            for role in template.roles
        ]
        symbols = build_symbols(components)
        parse_expression(expression, symbols)  # must not raise


def test_instantiate_template_missing_role_raises():
    with pytest.raises(ExpressionError):
        template_lib.instantiate("SHAFT_HOUSING_GAP_1D", {"bore": "B"})  # missing 'shaft'


def test_instantiate_unknown_template_raises():
    with pytest.raises(ExpressionError):
        template_lib.instantiate("NOT_A_REAL_TEMPLATE", {})


def test_optimizer_meets_target_yield_and_respects_bounds():
    components = [
        Component(id="c1", label="A", kind=ComponentKind.LINEAR, nominal=50.0,
                   upperTol=0.10, lowerTol=0.10, distribution=DistributionType.NORMAL_3S),
        Component(id="c2", label="B", kind=ComponentKind.LINEAR, nominal=20.0,
                   upperTol=0.05, lowerTol=0.05, distribution=DistributionType.NORMAL_3S),
    ]
    optimizable = [
        CostToleranceParams(componentId="c1", costCoefficientK=2.0, costExponentP=1.5, minTol=0.02, maxTol=0.15),
        CostToleranceParams(componentId="c2", costCoefficientK=1.0, costExponentP=1.5, minTol=0.02, maxTol=0.10),
    ]
    result = optimize_tolerances(
        expression="A - B", components=components, usl=30.3, lsl=29.7,
        target_yield_pct=99.99, optimizable=optimizable,
        monte_carlo_runs=20000, seed=7,
    )
    assert result["success"]
    assert result["predictedYieldPct"] >= 99.98
    assert result["monteCarloVerification"]["yieldPct"] >= 99.9
    for ot in result["optimizedTolerances"]:
        bound = next(o for o in optimizable if o.componentId == ot["componentId"])
        assert bound.minTol - 1e-6 <= ot["upperTol"] <= bound.maxTol + 1e-6


def test_optimizer_prefers_loosening_the_expensive_to_tighten_component():
    # c1 is much more expensive to hold tight (k=50) than c2 (k=1). A tight
    # spec window relative to the bounds forces a real tradeoff (both maxed
    # out would fail the yield target — see the probe in this test's history)
    # so the optimizer actually has to choose where to spend the tolerance
    # budget, rather than both components just saturating their bounds.
    components = [
        Component(id="c1", label="A", kind=ComponentKind.LINEAR, nominal=50.0,
                   upperTol=0.05, lowerTol=0.05, distribution=DistributionType.NORMAL_3S),
        Component(id="c2", label="B", kind=ComponentKind.LINEAR, nominal=20.0,
                   upperTol=0.05, lowerTol=0.05, distribution=DistributionType.NORMAL_3S),
    ]
    optimizable = [
        CostToleranceParams(componentId="c1", costCoefficientK=50.0, costExponentP=1.5, minTol=0.01, maxTol=0.15),
        CostToleranceParams(componentId="c2", costCoefficientK=1.0, costExponentP=1.5, minTol=0.01, maxTol=0.15),
    ]
    result = optimize_tolerances(
        expression="A - B", components=components, usl=30.12, lsl=29.88,
        target_yield_pct=99.99, optimizable=optimizable,
        monte_carlo_runs=20000, seed=3,
    )
    assert result["success"]
    by_id = {ot["componentId"]: ot["upperTol"] for ot in result["optimizedTolerances"]}
    # The economically correct allocation: it's expensive to hold c1 tight,
    # so the optimizer loosens c1 and tightens the cheap c2 to compensate,
    # minimizing total cost while still hitting the shared yield target.
    assert by_id["c1"] > by_id["c2"]
