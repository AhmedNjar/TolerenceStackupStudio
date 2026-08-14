# Implementation Plan - Fix Vector Chain Label Overlaps

When multiple short components are placed in a sequence, their labels often overlap because they are all rendered at the same vertical offset. This is especially problematic for 1D/linear stack-ups where components are perfectly aligned.

## Analysis
- **Current Behavior**: Every linear component label is offset by a fixed distance (24px) in the perpendicular direction (usually "up" for horizontal chains).
- **Issue**: If the component length is shorter than the label width, the labels for consecutive components will collide.
- **Proposed Solution**: Stagger the labels by alternating their position above and below the chain segments. Component 1 will be above, Component 2 below, Component 3 above, and so on.

## Proposed Changes

### [desktopApp](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp)

#### [MODIFY] [VectorChainCanvas.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/canvas/VectorChainCanvas.kt)
- Update `buildElements` to alternate the label offset direction for `LINEAR` components.
- Use a counter to track the sequence of linear components and flip the perpendicular offset multiplier based on whether the count is even or odd.
- Ensure that `AngleJoint` labels are also positioned to avoid conflict with staggered segment labels.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew :desktopApp:assemble`.

### Manual Verification
- Add 4 components of the same short length (e.g., 10mm).
- Verify in the UI that labels alternate between being above and below the line.
- Verify that the labels no longer overlap.
