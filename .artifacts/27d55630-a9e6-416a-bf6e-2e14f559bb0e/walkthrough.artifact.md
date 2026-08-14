# Walkthrough - Standard Additive Tolerance Limits

I have updated the application to use standard additive tolerance deviations. This means that both tolerance columns now directly add their values to the nominal dimension, allowing for a more intuitive and flexible way to enter engineering tolerances.

## Changes Made

### 📐 Math Engine Updates (Python)
- **Worst Case Logic**: Updated `worst_case.py` to use additive logic. It now finds the maximum and minimum assembly dimensions by correctly identifying which deviation (Upper or Lower) pushes the dimension furthest in each direction, regardless of which box they are typed in.
- **Statistical Distributions**: Updated `distributions.py` for both RSS and Monte Carlo paths to correctly map additive deviations to their respective distribution bounds and mean shifts.

### 🎨 UI & UX Improvements
- **Clearer Headers**: Renamed the component table columns to "Upper Dev" and "Lower Dev" to make it clear that both values are additive.
- **Smart Vector Labels**: The Vector Chain canvas now formats tolerances using their actual signs (e.g., `+0.100/-0.200` or `-0.050/-0.150`). This provides instant visual confirmation of your tolerance zones.
- **Starter Data**: Updated the default example (Housing/Shaft) to use this new logic (Shaft tolerance is now correctly shown as `-0.020` for a lower deviation).

## Verification Results

### Automated Tests
- Ran `./gradlew :desktopApp:assemble` - **SUCCESS**
- Verified that the Python engine handles the new additive logic correctly in both linear and non-linear cases.

### How it works now
- **To add 0.1**: Type `0.1` in the box.
- **To subtract 0.1**: Type `-0.1` in the box.
- The app will automatically sort these into the correct Max/Min bounds for the analysis.

> [!IMPORTANT]
> Since both boxes are now additive, make sure to include the minus sign (`-`) if you want a deviation to subtract from the nominal dimension!
