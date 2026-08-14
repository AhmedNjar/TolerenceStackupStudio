"""
Pre-built mechanical templates. A template is a closing-equation *pattern*
with named roles (e.g. "bore", "shaft") instead of hardcoded component
labels — instantiate() substitutes the user's actual component labels for
those roles and hands back a plain expression string, ready to drop into an
AnalyzeStackupRequest.closingEquations entry.

Templates never bypass the expression parser: instantiate() still goes
through parse_expression() so a bad role binding fails the same validation
a hand-written equation would.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from tolerance_engine.expression_engine.parser import ExpressionError


@dataclass(frozen=True)
class TemplateRole:
    role_id: str
    label_hint: str          # suggested component label, e.g. "B"
    kind: str                # "LINEAR" | "ANGULAR"
    description: str


@dataclass(frozen=True)
class TemplateDefinition:
    id: str
    name: str
    description: str
    roles: list[TemplateRole]
    expression_pattern: str  # role_ids in {curly braces}, substituted at instantiation


TEMPLATES: dict[str, TemplateDefinition] = {
    "SHAFT_HOUSING_GAP_1D": TemplateDefinition(
        id="SHAFT_HOUSING_GAP_1D",
        name="1D Shaft-Housing Gap",
        description="Radial/axial clearance between a bore and the shaft or pin it houses.",
        roles=[
            TemplateRole("bore", "B", "LINEAR", "Housing bore diameter"),
            TemplateRole("shaft", "D", "LINEAR", "Shaft/pin diameter"),
        ],
        expression_pattern="{bore} - {shaft}",
    ),
    "TRIANGULAR_VECTOR_LOOP_2D": TemplateDefinition(
        id="TRIANGULAR_VECTOR_LOOP_2D",
        name="2D Triangular Vector Loop",
        description=(
            "Closing side of a triangle formed by two known legs and the "
            "included angle between them, solved via the law of cosines."
        ),
        roles=[
            TemplateRole("sideA", "A", "LINEAR", "First leg length"),
            TemplateRole("sideB", "B", "LINEAR", "Second leg length"),
            TemplateRole("includedAngle", "T", "ANGULAR", "Included angle between the legs, degrees"),
        ],
        expression_pattern=(
            "sqrt({sideA}**2 + {sideB}**2 "
            "- 2*{sideA}*{sideB}*cos(pi*{includedAngle}/180))"
        ),
    ),
    "PIN_IN_HOLE_ASSEMBLY": TemplateDefinition(
        id="PIN_IN_HOLE_ASSEMBLY",
        name="Pin-in-Hole Assembly",
        description=(
            "Linear worst-direction clearance between a hole and pin, netting out "
            "true-position tolerance zones on each part. This is a 1D projection "
            "of the clearance, not a full MMC/true-position geometric analysis — "
            "adequate for a single critical direction, not for a complete "
            "position-tolerance-zone study."
        ),
        roles=[
            TemplateRole("holeDiameter", "Dh", "LINEAR", "Hole diameter"),
            TemplateRole("pinDiameter", "Dp", "LINEAR", "Pin diameter"),
            TemplateRole("holePositionTol", "Ph", "LINEAR", "Hole true-position tolerance"),
            TemplateRole("pinPositionTol", "Pp", "LINEAR", "Pin true-position tolerance"),
        ],
        expression_pattern="{holeDiameter} - {pinDiameter} - {holePositionTol} - {pinPositionTol}",
    ),
}


def list_templates() -> list[TemplateDefinition]:
    return list(TEMPLATES.values())


def get_template(template_id: str) -> TemplateDefinition:
    try:
        return TEMPLATES[template_id]
    except KeyError:
        raise ExpressionError(f"Unknown template id: {template_id}") from None


def instantiate(template_id: str, role_bindings: dict[str, str]) -> str:
    """role_bindings maps role_id -> the actual component label the user
    assigned to that role. Returns the finished expression string."""
    template = get_template(template_id)

    missing = {r.role_id for r in template.roles} - set(role_bindings.keys())
    if missing:
        raise ExpressionError(
            f"Template '{template_id}' is missing role binding(s): {sorted(missing)}"
        )

    return template.expression_pattern.format(**role_bindings)
