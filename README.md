# Tolerance Stack-up Studio

MITCalc-inspired 2D/3D dimensional chain & tolerance stack-up analysis tool.
KMP Compose Desktop frontend, embedded Python (SymPy/NumPy/SciPy) engine.

See `docs/ARCHITECTURE.md` for the Phase 1 architecture decisions (why a
loopback socket instead of stdio or JNI/CPython-embedding, the handshake
protocol, the repo layout).

## Running Phase 1 in dev mode (no PyInstaller needed yet)

The engine falls back to `python -m tolerance_engine` against source when no
frozen executable is staged — this is the fast loop for Phase 1-4.

```bash
# 1. Python engine deps
cd engine
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cd ..

# 2. Run the Compose Desktop smoke test (Main.kt: ping + one WC analysis)
./gradlew :composeApp:run
```

You should see the window report `Engine alive: true` followed by the
worst-case Z1 result for the sample shaft-housing gap (`B - D`).

## Producing the packaged (zero-Python-required) executable

```bash
./scripts/build_engine.sh     # macOS/Linux
./scripts/build_engine.ps1    # Windows
```

This freezes `engine/` with PyInstaller and stages the result under
`composeApp/src/desktopMain/resources/engine-dist/<os>-<arch>/`, which
`EngineProcessManager` prefers over the dev fallback whenever it's present.
Full native bundling (`jpackage`/Conveyor, code signing, installers) is
Phase 5 scope.

## What's stubbed vs. real

- **Real (Phase 1):** the full transport (socket framing, handshake, process
  lifecycle) and the JSON API contract (`/schemas`, Kotlin DTOs, Python
  pydantic models).
- **Real (Phase 2):** SymPy expression parser (restricted namespace, shared
  by every statistics module); the 3 pre-built templates (1D shaft-housing
  gap, 2D triangular vector loop via law of cosines, pin-in-hole assembly);
  Worst Case; RSS and Modified/Extended RSS with a documented default
  dynamic shift factor (override via `modifiedRssShiftFactor` — see
  `statistics/rss.py` docstring, this is a heuristic default, not one
  universally standardized formula); vectorized NumPy Monte Carlo (mean,
  stdDev, skewness, kurtosis, Cp/Cpk, yield/DPPM with a normal-fit fallback
  when the empirical run shows zero defects); variance-contribution Pareto
  ranking; the SciPy SLSQP inverse tolerance optimizer (reciprocal
  cost-tolerance objective, multi-start for robustness, verified against a
  real Monte Carlo run afterward); thermal expansion (applied inside every
  method via `Component.effective_nominal()`); multi-gap solving (multiple
  closing equations sharing one component list, in one request). All of
  engine/statistics, engine/optimization, and engine/expression_engine have
  a pytest suite (`engine/tests/`) — run it with:
  ```bash
  cd engine && pip install -e ".[dev]" && PYTHONPATH=src pytest tests/ -v
  ```
- **Stubbed (never fabricated):** ANSI B4.1 fit-class limit tables and ISO 286
  fundamental deviations for letters other than H/h/JS/js — see
  `engine/standards/*.py` module docstrings for why. ISO 281 (bearing dynamic
  load rating / L10 life) was left out of the standards lookup entirely — it's
  not a dimensional-fit standard, so it doesn't belong next to the others; a
  bearing-life calculator would be a genuinely different feature.
- **Not yet built:** 2D canvas DXF/SVG vector export for CAD integration —
  this was in your "Additional Specific Requirements" list but didn't have
  an obvious home in either the canvas (Phase 3) or reporting (Phase 4) work
  so far; happy to slot it into either one, just say where you'd like it.

## Phase 3 — Compose UI

Built: an editable component data grid (`ui/datagrid/`), an interactive 2D
vector-chain canvas (`ui/canvas/` — LINEAR components draw as arrows advancing
along the current heading, ANGULAR components rotate the heading for what
follows, tapping a segment or angle joint selects the matching grid row and
vice versa), a standard-fit lookup dialog wired to the new `GET_STANDARD_TABLE`
calls, a results panel, and `Main.kt` wiring it all together with engine
startup/shutdown.

**Important caveat:** unlike the Python engine (which has a real pytest suite
run in a clean venv every phase), this sandbox has no JVM/Gradle toolchain, so
none of the Kotlin/Compose code has actually been compiled. It's been written
and reviewed carefully against the Compose APIs I'm confident about, and one
real bug was caught and fixed on review (state declared with `mutableStateOf`
directly inside the `application {}` composable body, not wrapped in
`remember{}`, would have been silently reset to its initial value on every
recomposition — see `Main.kt`). Run `./gradlew :composeApp:run` on your end
before trusting this compiles; the one API I'm least certain about sight-unseen
is `DrawScope.drawText`/`rememberTextMeasurer` in `VectorChainCanvas.kt` (added
to Compose UI in the 1.4-1.5 era — should be present at the 1.6.2 Compose
plugin version this project pins, but I can't verify the exact signature
without a compiler).

Standards lookup (ISO 286 IT-grade formula, ISO 2768 general tolerances) has a
real pytest suite cross-checked against commonly-cited textbook reference
values (e.g. IT7 at 30mm ≈ 21µm) — see `engine/tests/test_standards.py`.

## Phase 4 — Data visualization and report generation

**In-app (Compose):** `ui/results/HistogramChart.kt` and `ParetoChart.kt` —
read-only bar charts (no drag/zoom, unlike the interactive vector canvas)
wired into the results panel whenever Monte Carlo / contribution data is
present.

**PDF report** (`engine/reporting/pdf_report.py`, ReportLab + matplotlib):
cover page, input parameters table, standard-compliance section (lists which
components reference a `standardFit`), one section per closing dimension
(equation, WC/RSS/Modified RSS, a Monte Carlo stats table, a histogram chart
image, a Pareto chart image, and a pass/fail line based on Cpk ≥ 1.0 against
the closing equation's spec limits — "not evaluated" if no spec limits are
set). matplotlib is forced onto the `Agg` backend since this runs in a
headless background process. 18 tests (`test_pdf_report.py` +
`test_excel_formula_printer.py` + `test_excel_export.py`, see below) —
including actually extracting text from a generated PDF with `pypdf` to
check the chain ID, component names, and PASS/FAIL verdicts really appear in
it, not just that a file got written.

**Excel export** (`engine/reporting/excel_export.py`): this is the one part
of Phase 4 worth reading closely before trusting it blindly, because "Excel
export with live formulas" is doing more than it sounds like. Three
DIFFERENT liveness guarantees exist in the same workbook:
- **Nominal**: fully live for any edit — direct expression substitution.
- **Worst Case / RSS / variance contribution**: live for tolerance and
  distribution-type edits (exact); for a NONLINEAR closing equation (e.g.
  the triangular vector-loop template), these become an approximation if you
  edit a NOMINAL value far from what it was at export time, because the
  sensitivity coefficients are frozen at export — Excel can't symbolically
  re-differentiate. Exact regardless of nominal edits for a purely linear
  equation. Every affected cell carries an Excel comment saying this.
- **Monte Carlo** (mean, stdDev, skewness, kurtosis, Cp/Cpk, yield%, DPPM,
  histogram + native chart): a static snapshot from the simulation run at
  export time. Re-export to refresh it.

The SymPy→Excel formula translation (`reporting/excel_formula_printer.py`)
walks the parsed expression TREE, not the original string, specifically so
it can't drift out of sync with what the engine's own math actually
evaluates. It has a specific documented gotcha: **Excel's `ATAN2(x,y)` takes
x before y; SymPy's (and Python's) `atan2(y,x)` takes y before x** — same
math, swapped argument order, silently wrong if you get it backwards. Tested
by actually evaluating the generated formula strings (with a small
Excel-semantics shim) and comparing against SymPy's own result, not by
eyeballing them.

The Excel export tests go one step further and actually recalculate the
generated workbook using the `formulas` PyPI package (a real, if imperfect,
Excel formula engine) and cross-check the results against the engine's own
`worst_case()`/`rss()`/`contribution_ranking()` output — including a test
that edits a tolerance cell in the saved file (simulating a user in Excel)
and confirms the WC formula actually picks up the change.

## Phase 5 — Integration, packaging, and native bundling

This phase is genuinely asymmetric between the two halves of the stack, and
it's worth being precise about which claims are backed by an actual test run
versus careful-but-unverified review — same policy as every phase before this.

**Fully verified — the PyInstaller freeze actually works:**
`pyinstaller engine.spec` was run for real in the sandbox this was built in
(Linux x86-64), producing an 88MB standalone executable. That executable was
then run in a subprocess environment with **no Python on `PATH` at all**
(`PATH=/usr/bin:/bin`, nothing else) and driven through the entire protocol —
`PING`, multi-method `ANALYZE_STACKUP`, `LIST_TEMPLATES`,
`GET_STANDARD_TABLE`, `OPTIMIZE_TOLERANCES`, and both `GENERATE_PDF_REPORT`
and `GENERATE_EXCEL_REPORT` (the two heaviest dependency chains — matplotlib,
ReportLab, openpyxl) — all passed. `scripts/build_engine.sh` was also run for
real end-to-end (not just read), and its OS/arch-detected output path
(`engine-dist/linux-x64/tolerance-engine`) was confirmed to match exactly
what `EngineProcessManager.kt`'s `platformResourceDir()` looks for — the
Phase 1 architecture's central promise ("end users need zero Python
installed") is no longer just a design intent, it's been exercised.

Measured cold-start time (one-file build re-extracts its ~85MB payload on
*every* launch, not just the first): 3.7-4.7s across 3 trials on the sandbox's
hardware. `EngineProcessManager`'s handshake timeout was bumped from 10s to
20s on the strength of that real number, to leave headroom for slower disks/
CPUs on actual end-user machines rather than trusting one dev machine's
measurement literally. One-dir packaging (faster launch, ships a folder
instead of one file) wasn't built or benchmarked here — worth comparing
head-to-head before deciding, now that there's a real one-file number to
compare it against.

One genuine bug this surfaced: the Gradle `nativeDistributions` block had
`appResourcesRootDir` pointed at the same directory as the ordinary Kotlin
resources source set. Those are two DIFFERENT Compose Desktop mechanisms —
`appResourcesRootDir` copies files into a separate native-bundle directory
read via a special runtime system property, while `EngineProcessManager`
actually reads the frozen engine via `getResourceAsStream()`, which the
*ordinary* `src/desktopMain/resources` source set already serves with zero
extra config. The redundant `appResourcesRootDir` line has been removed —
left in, it would've silently copied an 85MB file into the app bundle a
second time through a mechanism nothing reads from.

**Reviewed but NOT verified — needs your JVM/Gradle:**
- macOS and Windows builds of the frozen engine (this sandbox is Linux-only).
  The cross-platform logic in `build_engine.ps1` and
  `EngineProcessManager.kt`'s OS/arch detection is written carefully but
  genuinely untested on those platforms — budget real time for this.
- The actual `jpackage`-based native installers (`.dmg`/`.msi`/`.deb` via
  `./gradlew packageDistributionForCurrentOS`) — there's no JVM in this
  sandbox at all, so nothing about Gradle/Compose Desktop packaging has ever
  been compiled, let alone run, this entire project. Every Kotlin file has
  been written and reviewed carefully (and a couple of real bugs — the
  `EngineTransport` blocking-I/O issue in Phase 4, the `appResourcesRootDir`
  mismatch above — were caught by that review), but "reviewed carefully" and
  "compiled" are not the same claim, and only one of them is true here.

### Packaging checklist (do this on a real machine, per target OS)

```bash
# 1. Build and stage the frozen engine for THIS os/arch
./scripts/build_engine.sh      # macOS/Linux
./scripts/build_engine.ps1     # Windows

# 2. Sanity-check it standalone before trusting the Compose app with it
./composeApp/src/desktopMain/resources/engine-dist/<os>-<arch>/tolerance-engine
# should print a TOLERANCE_ENGINE_READY {...} line and then sit waiting for
# a connection — Ctrl+C to stop it.

# 3. Build the native installer for this OS
./gradlew packageDistributionForCurrentOS

# 4. Install the result and run it on a machine that does NOT have Python
#    installed, to actually prove the zero-dependency claim end to end —
#    the Python-engine half of this was proven in this sandbox; the full
#    packaged-Compose-app half hasn't been, and this step is what would.
```

Repeat per target OS — there's no cross-compilation here; a macOS `.dmg`
has to be built on macOS, a Windows `.msi` on Windows.

## Known simplifications, called out explicitly

- **Modified RSS shift factor default** is a documented heuristic (tapers
  1.5 → 1.0 as component count grows), not a specific named published
  formula — the literature doesn't agree on one. Pass
  `modifiedRssShiftFactor` explicitly if you have a company standard.
- **Tolerance optimizer** allocates each optimizable component a *symmetric*
  tolerance and uses an RSS/normal-approximation yield model inside the
  SLSQP loop (fast enough to be interactive); the result is then checked
  against a real Monte Carlo run so a normality mismatch is visible rather
  than hidden. Asymmetric tolerance allocation isn't explored.
- **Pin-in-hole template** is a 1D worst-direction projection, not a full
  MMC/true-position geometric analysis.
