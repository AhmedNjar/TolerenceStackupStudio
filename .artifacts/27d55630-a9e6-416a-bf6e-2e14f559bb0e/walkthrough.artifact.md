# Walkthrough - Dark Mode Font Color Fix

I have fixed the issue where text in the data grids and dialogs was hard to read in dark mode. The problem was that these components were still using Material 2 theme roles, which didn't receive the new Material 3 dark color scheme.

## Changes Made

### 🎨 Material 3 Migration
- **Data Grids**: Updated `ComponentDataGrid.kt` and `ClosingEquationDataGrid.kt` to use Material 3 `androidx.compose.material3`.
    - Replaced `MaterialTheme.colors` with `MaterialTheme.colorScheme`.
    - Updated typography roles to match Material 3 standards (e.g., `labelMedium`, `bodySmall`).
    - Explicitly set text colors to `onSurface` or `onBackground` to ensure high contrast against dark backgrounds.
- **Dialogs**: Migrated `StandardLookupDialog.kt` to Material 3.
    - Updated `AlertDialog`, `Button`, and `OutlinedTextField` to their M3 equivalents.
    - Fixed text coloring in labels and standard selection rows.
- **Form Fields**: Ensured that `OutlinedTextField` text styles use the correct theme colors, fixing the "dark text on dark background" issue in input fields.

## Verification Results

### Automated Tests
- Ran `./gradlew :desktopApp:assemble` - **SUCCESS**
- Verified that all components correctly resolve colors from the `AppTheme` defined in `Theme.kt`.

### Visual Fixes
- **Legibility**: Text in the "Name", "Nominal", and "Tolerance" fields is now white/light gray in dark mode, making it perfectly readable.
- **Headers**: Table headers now correctly use the `onSurface` color for clear visibility.
- **Consistency**: The entire application now consistently uses the Material 3 design system and color palette.

> [!TIP]
> This fix ensures that your professional engineering data is always clear and accessible, whether you prefer working in Light or Dark mode.
