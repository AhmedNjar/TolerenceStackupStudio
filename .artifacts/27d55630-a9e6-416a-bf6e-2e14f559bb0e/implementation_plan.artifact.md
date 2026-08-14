# Implementation Plan - Add Closing Equations Editor

The current UI lacks a way for users to define and edit "Closing Equations" (the resultant dimensions $Z$ being analyzed). The Python engine already expects these equations and uses them to calculate Worst Case, RSS, and Monte Carlo results.

## Analysis
- **Engine Expectation**: The engine expects `AnalyzeStackupRequestDto` to contain a list of `ClosingEquationDto` objects.
- **Closing Equation Structure**:
    - `label`: A human-readable name (e.g., "Z1", "Gap").
    - `expression`: A mathematical formula using component labels (e.g., `A + B - D`).
    - `specLimits`: Upper and Lower Specification Limits (USL/LSL) used for yield calculations and pass/fail analysis.
- **UI Gap**: The main screen has a grid for components but no editor for closing equations.

## Proposed Changes

### [desktopApp](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp)

#### [MODIFY] [StackupViewModel.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/StackupViewModel.kt)
- Add `addClosingEquation`, `removeClosingEquation`, and `updateClosingEquation` methods to manage the `closingEquations` list.

#### [NEW] [ClosingEquationDataGrid.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/datagrid/ClosingEquationDataGrid.kt)
- Create a new data grid component similar to `ComponentDataGrid` for managing closing equations.
- Fields to include: Label, Equation (Expression), LSL, and USL.

#### [MODIFY] [main.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/main.kt)
- Update the layout to include the `ClosingEquationDataGrid` below the component grid.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew :desktopApp:assemble` to ensure no syntax errors.
- Run the app using `./gradlew :desktopApp:run` and verify:
    1. A new grid for "Closing Equations" appears.
    2. Equations can be added, removed, and edited.
    3. Running an analysis uses the user-defined equations and displays results in the Results panel.

### Manual Verification
- Add components with labels `A` and `B`.
- Add a closing equation with label `Z` and expression `A - B`.
- Set USL and LSL for `Z`.
- Run analysis and verify that the results for `Z` reflect the nominal values and tolerances of `A` and `B`.
