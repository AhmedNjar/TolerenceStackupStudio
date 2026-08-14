# Implementation Plan - Switch to Additive Tolerance Limits

This plan changes the tolerance input behavior from "Upper/Lower magnitudes" to "Upper/Lower deviations". Both fields will now be additive by default.

## User Review Required

> [!WARNING]
> This is a **breaking change** for the math logic. Previously, the "Lower" column was subtracted (`Nominal - Lower`). Now, both columns will be added (`Nominal + Upper` and `Nominal + Lower`).
> - Old: `10 + 0.1 / -0.1` was entered as `Upper=0.1, Lower=0.1`.
> - New: `10 + 0.1 / -0.1` must be entered as `Upper=0.1, Lower=-0.1`.

## Proposed Changes

### [Engine (Python)](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/engine/src/tolerance_engine)

#### [MODIFY] [worst_case.py](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/engine/src/tolerance_engine/statistics/worst_case.py)
- Change logic to find min/max range using additive offsets.
- For a positive sensitivity ($d/dx \ge 0$):
    - contribution to max = $d/dx \cdot \max(Upper, Lower)$
    - contribution to min = $d/dx \cdot \min(Upper, Lower)$
- This robustly handles cases where the user puts the "larger" offset in either box.

#### [MODIFY] [distributions.py](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/engine/src/tolerance_engine/statistics/distributions.py)
- Update `component_stats` for RSS path:
    - `low_limit = min(Upper, Lower)`
    - `high_limit = max(Upper, Lower)`
    - `half_range = (high_limit - low_limit) / 2`
    - `mean_shift = (high_limit + low_limit) / 2`
- Update `sample_component` for Monte Carlo path:
    - `low = center + min(Upper, Lower)`
    - `high = center + max(Upper, Lower)`

---

### [desktopApp (Kotlin)](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp)

#### [MODIFY] [ComponentDataGrid.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/datagrid/ComponentDataGrid.kt)
- Rename header columns from `+Upper` and `-Lower` to `Upper Tol` and `Lower Tol` (or similar clear additive names).

#### [MODIFY] [VectorChainCanvas.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/canvas/VectorChainCanvas.kt)
- Update the label formatting logic.
- Instead of hardcoding `+` and `-`, format the tolerance as a combined string with signs:
    - e.g., `10.000 +0.100/-0.200`
- Use a helper function `formatSigned(value)` to ensure `+` is shown for positive numbers.

## Verification Plan

### Automated Tests
- Build and run the project.

### Manual Verification
1. Add a component: Nominal=10, Upper=0.1, Lower=-0.2.
2. Verify Vector Chain text shows `10.000 +0.100/-0.200`.
3. Run analysis.
4. Verify Worst Case range is $[9.8, 10.1]$.
5. Try Nominal=10, Upper=-0.05, Lower=-0.15 (both negative).
6. Verify Vector Chain shows `10.000 -0.050/-0.150`.
7. Verify Worst Case range is $[9.85, 9.95]$.
