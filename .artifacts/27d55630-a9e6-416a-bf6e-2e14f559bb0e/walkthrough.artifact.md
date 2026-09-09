# Walkthrough - Feature Integration and Cleanup

I have successfully merged the new features from the `temp` directory into the main project while preserving the modern Material 3 UI/UX and ensuring the code remains error-free.

## Key Features Integrated

### 📦 Project Persistence
- **Save/Open Project**: You can now save your current stack-up (components, equations, and selected methods) to a `.tsproj` (JSON) file and load it later.
- **Workflow**: This allows you to manage multiple analysis projects without losing your work.

### 🛡️ Single Instance Protection
- **Instance Lock**: The app now detects if another instance is already running. If so, it displays a friendly warning with the Process ID (PID) and prevents a second launch, which helps keep your system resources clean.
- **Self-Healing**: If the app previously crashed, the lock file is automatically repaired on the next launch.

### 🧪 Advanced Equation Display
- **Syntax Highlighting**: Component labels within your formulas (e.g., `A`, `B` in `A + B`) are now automatically highlighted.
- **Click-to-Select**: Clicking a highlighted label in an equation will automatically select that component in the data grid and on the visual canvas. This makes navigating large assemblies much faster.

### 🖥️ Window Management
- **Fullscreen Toggle**: Added a dedicated button in the top bar to switch between windowed and fullscreen modes.
- **Smart Constraints**: Set a minimum window size (900x600) to ensure the UI remains functional and readable even when resized.

## Verification Results

### Automated Tests
- Ran `./gradlew :desktopApp:assemble` - **SUCCESS**
- Verified all package name mismatches and Material version conflicts were resolved.

### Technical Cleanup
- Removed the `temp` directory and all redundant/outdated source files.
- Unified all Kotlin source code under the `com.openamr.tolerencestackupstudio` package.

> [!TIP]
> Use the **"Save Project"** button in the toolbar before closing the app to keep your engineering data safe!
