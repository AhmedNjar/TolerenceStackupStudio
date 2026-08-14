# Implementation Plan - UI/UX Overhaul & Navigation

This plan outlines a complete redesign of the application's user interface to improve aesthetics, usability, and responsiveness, including a new multi-screen navigation flow.

## User Review Required

> [!IMPORTANT]
> The UI will switch from a single-screen layout to a two-screen layout: **Editor** and **Results**. Navigation to Results will happen automatically after a successful analysis.

> [!TIP]
> We will introduce a **Dark/Light mode** toggle and modernize the styling using Material 3 principles (large rounded corners, refined typography, and subtle animations).

## Proposed Changes

### [desktopApp](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp)

#### [NEW] [Theme.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/theme/Theme.kt)
- Define a modern color palette for both light and dark modes.
- Configure `Typography` with a clean, professional font stack.
- Set `Shapes` with modern rounded corners (12dp - 16dp).

#### [MODIFY] [StackupViewModel.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/StackupViewModel.kt)
- Add `currentScreen` state (enum: `EDITOR`, `RESULTS`).
- Update `runAnalysis()` to automatically switch to `RESULTS` screen upon success.
- Add a `backToEditor()` method.

#### [MODIFY] [main.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/main.kt)
- Implement `AnimatedContent` to transition between `EditorScreen` and `ResultsScreen`.
- Add a top app bar with the application title and a **Dark Mode toggle**.
- Extract `EditorScreen` and `ResultsScreen` into dedicated composables.

#### [MODIFY] [ResultsPanel.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/results/ResultsPanel.kt)
- Redesign as a full-screen experience.
- Add a "Back to Editor" button.
- Improve chart aesthetics (gradients, smoother lines).

#### [REFINEMENT] Data Grids
- Clean up the table headers and cell padding.
- Use card-based layouts for sections to create visual depth.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew :desktopApp:assemble`.

### Manual Verification
1. **Theme Test**: Toggle between Dark and Light mode; verify all text remains legible.
2. **Navigation Test**: Click "Run Analysis" -> verify automatic transition to Results screen.
3. **Transition Test**: Verify that the screen change is animated (e.g., slide or fade).
4. **UX Test**: Verify that the new layout feels less "crowded" on various window sizes.
