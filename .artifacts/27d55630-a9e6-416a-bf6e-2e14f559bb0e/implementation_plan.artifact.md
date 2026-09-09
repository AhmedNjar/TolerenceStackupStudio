# Implementation Plan - Move and Merge Temp Files

This plan outlines the integration of valuable new features found in the `temp` directory (Single Instance Lock, Project Saving, and Equation Highlighting) while preserving the modern Material 3 UI/UX and path fixes recently implemented.

## User Review Required

> [!WARNING]
> I have identified that many files in the `temp` directory are older versions using Material 2. I will **not** overwrite the current modern files with these older versions. Instead, I will surgically extract the new features and merge them into the current codebase.

## Proposed Changes

### [New Components]

#### [NEW] [SingleInstanceLock.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/SingleInstanceLock.kt)
- Move from `temp` and fix package to `com.openamr.tolerencestackupstudio`.
- This utility prevents multiple app instances and provides a PID-aware warning.

#### [NEW] [ProjectFileDto.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/project/ProjectFileDto.kt)
- Move from `temp/project` and fix package.
- Defines the `.tsproj` JSON format for saving/loading work.

#### [NEW] [EquationDisplay.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/EquationDisplay.kt)
- Move from `temp/ui` and fix package.
- **Enhancement**: Migrate to Material 3 and ensure clickable labels select components in the shared state.

---

### [Logic Updates]

#### [MODIFY] [StackupViewModel.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/StackupViewModel.kt)
- Integrate `toProjectFile()` and `loadProject(ProjectFileDto)` methods.
- Add `nextAvailableClosingLabel()` logic to ensure unique auto-labels for equations.

#### [MODIFY] [main.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/main.kt)
- **Startup**: Add `SingleInstanceLock.acquire()` check before launching the Compose app.
- **UI**: Add `ProjectControls` (Save/Open Project) to the `EditorScreen` toolbar.
- **Window**: Add Fullscreen toggle button and set a minimum window dimension (900x600).
- **Cleanup**: Ensure `SingleInstanceLock.release()` and explicit process cleanup on close.

---

### [UI Enhancement]

#### [MODIFY] [ClosingEquationDataGrid.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/ui/datagrid/ClosingEquationDataGrid.kt)
- Integrate `EquationDisplay` into the equation rows to provide live syntax highlighting and interactivity.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew :desktopApp:assemble`.
- Verify no package mismatch errors.

### Manual Verification
1. **Instance Lock**: Try to launch the app twice; verify the warning dialog appears.
2. **Persistence**: Add components/equations, Save Project to a `.tsproj` file, clear everything, and Load Project. Verify state is restored.
3. **Interactivity**: Type a formula (e.g., `A + B`); verify labels `A` and `B` are highlighted and clicking them selects the row in the component grid.
4. **Fullscreen**: Test the new Fullscreen toggle.
