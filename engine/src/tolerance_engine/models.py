"""
Pydantic models mirroring /schemas/*.schema.json. These are the Python-side
half of the API contract; the Kotlin-side half is the kotlinx.serialization
@Serializable data classes under composeApp/.../engine/protocol/dto/.

Keep both in sync with the schema files by hand for Phase 1. A schema-driven
codegen step (e.g. datamodel-code-generator on the Python side, quicktype on
the Kotlin side) is a reasonable Phase 2/3 improvement once the contract
stabilizes — not worth the tooling overhead while the shape is still moving.
"""
from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


class ComponentKind(str, Enum):
    LINEAR = "LINEAR"
    ANGULAR = "ANGULAR"


class DistributionType(str, Enum):
    NORMAL_3S = "NORMAL_3S"
    NORMAL_6S = "NORMAL_6S"
    UNIFORM = "UNIFORM"
    TRIANGULAR = "TRIANGULAR"


class AnalysisMethod(str, Enum):
    WORST_CASE = "WORST_CASE"
    RSS = "RSS"
    MODIFIED_RSS = "MODIFIED_RSS"
    MONTE_CARLO = "MONTE_CARLO"


class StandardFit(BaseModel):
    standard: str
    designation: str


class Thermal(BaseModel):
    alpha: float
    deltaT: float


class Component(BaseModel):
    id: str
    label: str
    name: str = ""
    kind: ComponentKind
    # Single source of truth for this component's value. Units follow `kind`:
    # length units for LINEAR, degrees for ANGULAR. (Phase 1 shipped a
    # separate `angleDeg` field alongside this, which created two sources of
    # truth for the same quantity on angular components — removed in Phase 2.
    # Trig templates convert degrees -> radians inside the expression string
    # itself, e.g. cos(pi*A/180), so SymPy differentiates the conversion
    # correctly along with everything else.)
    nominal: float
    upperTol: float
    lowerTol: float
    distribution: DistributionType
    standardFit: Optional[StandardFit] = None
    thermal: Optional[Thermal] = None

    def effective_nominal(self) -> float:
        """L_eff = L0 * (1 + alpha * deltaT); returns L0 unchanged if no thermal data."""
        if self.thermal is None:
            return self.nominal
        return self.nominal * (1 + self.thermal.alpha * self.thermal.deltaT)


class SpecLimits(BaseModel):
    usl: float
    lsl: float


class ClosingEquation(BaseModel):
    id: str
    label: str
    expression: str
    specLimits: Optional[SpecLimits] = None


class AnalysisOptions(BaseModel):
    methods: list[AnalysisMethod]
    monteCarloRuns: int = Field(default=10000, ge=1000, le=100000)
    modifiedRssShiftFactor: Optional[float] = None
    randomSeed: Optional[int] = None


class AnalyzeStackupRequest(BaseModel):
    chainId: str
    components: list[Component]
    closingEquations: list[ClosingEquation]
    options: AnalysisOptions


class CostToleranceParams(BaseModel):
    """Reciprocal cost-curve params for one optimizable component:
    cost_i(T_i) = k / T_i^p. Components not listed here are held fixed at
    their given upperTol/lowerTol during optimization."""

    componentId: str
    costCoefficientK: float = Field(gt=0)
    costExponentP: float = Field(gt=0)
    minTol: float = Field(gt=0, description="smallest allowable symmetric tolerance")
    maxTol: float = Field(gt=0, description="largest allowable symmetric tolerance")


class OptimizeTolerancesRequest(BaseModel):
    chainId: str
    components: list[Component]
    closingEquation: ClosingEquation
    targetYieldPct: float = Field(gt=0, lt=100)
    optimizable: list[CostToleranceParams]
    monteCarloVerificationRuns: int = Field(default=20000, ge=1000, le=100000)
    randomSeed: Optional[int] = None

    def model_post_init(self, __context) -> None:
        if self.closingEquation.specLimits is None:
            raise ValueError("closingEquation.specLimits is required for tolerance optimization")


class GenerateReportRequest(BaseModel):
    chainId: str
    components: list[Component]
    closingEquations: list[ClosingEquation]
    options: AnalysisOptions
    outputPath: str
