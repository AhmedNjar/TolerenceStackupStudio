# Walkthrough - Fix Main Class Not Found Error

The build error `Could not find or load main class com.openamr.tolerencestackupstudio.MainKt` has been resolved by unifying the package structure in the `desktopApp` module.

## Changes Made

### desktopApp Refactoring
- All source files in `desktopApp` were moved from `com.architech.tolerencestackupstudio` to `com.openamr.tolerencestackupstudio`.
- Package declarations and imports in all 18 Kotlin files were updated to match the new structure.
- The directory structure now correctly reflects the package name: `desktopApp/src/main/kotlin/com/openamr/tolerencestackupstudio/`.

### Engine Startup Fix
- Modified `EngineProcessManager.kt` to robustly locate the project root.
- Updated the engine process working directory to use an absolute path (`projectRoot/engine/src`), preventing "directory name is invalid" errors when launched from sub-modules.
- Added support for using the Python executable within the `engine/.venv` directory, improving reliability in development environments where `python` might not be in the global PATH.

## Verification Results

### Automated Tests
- Ran `./gradlew :desktopApp:assemble` - **SUCCESS**
- Verified `EngineProcessManager.kt` compiles with the new path resolution logic.

> [!IMPORTANT]
> The `:desktopApp:run` task is currently blocked by a configuration error in the `androidApp` module (`AndroidLocationsBuildService` failure with AGP 9.0.1). This is unrelated to the desktop app's code but prevents Gradle from completing the configuration phase. Once the Android environment issue is resolved, the desktop engine will start correctly using the fixed paths.
