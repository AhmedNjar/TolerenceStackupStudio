# Implementation Plan - Fix Engine Startup Directory Error

The application fails to start the Python engine because `EngineProcessManager` incorrectly assumes the working directory is the project root when resolving the `engine/src` path. When running from within the `desktopApp` module, the relative path `engine/src` resolves to `desktopApp/engine/src`, which does not exist.

## Analysis
- The engine source code is located at the project root: `C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/engine/src`.
- The `EngineProcessManager` uses `File("engine/src").absoluteFile` as the working directory for the Python process.
- If the application is launched with the working directory set to `desktopApp`, this path resolution fails.
- Additionally, the "python" command might not be in the user's PATH, but a virtual environment (`.venv`) exists in the `engine` directory.

## Proposed Changes

### [desktopApp](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp)

#### [MODIFY] [EngineProcessManager.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/TolerenceStackupStudio/desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/engine/EngineProcessManager.kt)
- Add a helper method to robustly locate the project root directory by searching for `settings.gradle.kts` upwards from the current directory.
- Update `resolveProcessBuilder` to use the project root to construct the absolute path to `engine/src`.
- Update `resolveProcessBuilder` to prefer the `python` executable within the engine's `.venv` directory if it exists, improving reliability in development environments.

## Verification Plan

### Automated Tests
- Run `./gradlew :desktopApp:run` and verify that the engine starts successfully (the "Starting engine..." screen should disappear and show the main UI).

### Manual Verification
- Verify that the log output shows the engine starting from the correct absolute path.
- Verify that the venv python is used if present.
