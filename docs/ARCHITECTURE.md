# Tolerance Stack-up Studio — Architecture

*(Living doc, updated each phase — started as "Phase 1 Architecture" but now covers Phases 1-5.)*

## 1. Repository layout

```
tolerance-stackup-studio/
├── settings.gradle.kts
├── build.gradle.kts
├── composeApp/                                  # KMP Compose Desktop frontend
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/...                # shared UI logic (future: web target)
│       ├── desktopMain/
│       │   ├── kotlin/com/architech/tolerancestudio/
│       │   │   ├── Main.kt                      # app entry, lifecycle, shutdown hook
│       │   │   ├── engine/
│       │   │   │   ├── EngineProcessManager.kt  # locate/extract/launch python exe
│       │   │   │   ├── EngineTransport.kt        # length-prefixed JSON over TCP socket
│       │   │   │   ├── EngineClient.kt           # typed request/response API
│       │   │   │   └── protocol/
│       │   │   │       ├── Envelope.kt
│       │   │   │       └── dto/
│       │   │   │           ├── ComponentDto.kt
│       │   │   │           └── StackupDto.kt
│       │   │   └── ui/                           # data grid, canvas, reports (Phase 3/4)
│       │   └── resources/
│       │       └── engine-dist/                  # frozen python exe copied here at build time
│       └── desktopTest/kotlin/...
├── engine/                                       # Python backend engine
│   ├── pyproject.toml
│   ├── engine.spec                               # PyInstaller build spec
│   └── src/tolerance_engine/
│       ├── __init__.py
│       ├── __main__.py                           # process entrypoint, port handshake
│       ├── server.py                             # socket accept loop, framing
│       ├── protocol.py                           # envelope encode/decode, dispatch table
│       ├── models.py                             # pydantic DTOs mirroring /schemas
│       ├── handlers.py                           # request handlers (stubbed in Phase 1)
│       ├── standards/                             # ISO 286 / ANSI B4.1 / ISO 2768 tables (Phase 2)
│       ├── expression_engine/                     # SymPy parser (Phase 2)
│       ├── statistics/                            # WC / RSS / Monte Carlo (Phase 2)
│       ├── optimization/                          # SciPy inverse solver (Phase 2)
│       └── reporting/                             # ReportLab / OpenPyXL (Phase 4)
├── schemas/                                       # source-of-truth JSON Schemas
│   ├── envelope.schema.json
│   ├── component.schema.json
│   ├── stackup_request.schema.json
│   └── stackup_response.schema.json
├── scripts/
│   ├── build_engine.sh                            # freeze engine/ with PyInstaller (macOS/Linux)
│   └── build_engine.ps1                           # same, Windows
└── docs/
    └── ARCHITECTURE.md
```

## 2. IPC decision: loopback TCP socket + length-prefixed JSON (not stdio, not JNI/CPython-embedding)

Three options were on the table:

| Option | Verdict |
|---|---|
| Embed CPython via JNI/C-API | Rejected for Phase 1. Requires bundling `libpython3.x` per-OS/per-arch, linking against the CPython C API from Kotlin/Java, and is fragile across macOS notarization / Windows DLL search paths. High payoff (in-process, no serialization) but high complexity — revisit only if socket IPC latency becomes a measured bottleneck. |
| stdin/stdout JSON-RPC | Rejected. NumPy/SciPy/SymPy routinely print deprecation warnings or C-level buffer output to stdout; any stray byte corrupts a stdio-framed protocol. Also awkward for the future streaming use case (Monte Carlo progress updates). |
| **Loopback TCP socket, length-prefixed JSON** | **Chosen.** The Python process still owns stdout/stderr for free-form logging (captured and logged by Kotlin, never parsed). The socket carries only protocol frames: a 4-byte big-endian length prefix + UTF-8 JSON payload. Works identically on Windows/macOS/Linux with zero extra native dependencies. Easy to swap the wire format for Protobuf later without touching the transport. |

**Startup handshake:** the frozen Python executable binds to `127.0.0.1:0` (OS picks a free port), then prints exactly one marker line to stdout before doing anything else:

```
TOLERANCE_ENGINE_READY {"port": 54231, "pid": 40522}
```

Kotlin's `EngineProcessManager` reads stdout line-by-line, ignores everything until it sees that marker, parses the port, and only then opens the socket. This keeps the protocol channel deterministic even though the process's stdout is otherwise "noisy."

**Framing:** `[4-byte big-endian length][UTF-8 JSON bytes]`, both directions. Length-prefixing (rather than newline-delimited JSON) is used because Monte Carlo responses can carry large histogram arrays and we don't want to depend on JSON strings never containing a literal newline.

**Envelope:** every message — request or response — is wrapped the same way (see `schemas/envelope.schema.json`), with `requestId` for correlation, `type` as a routing key, and `payload` as the type-specific body. This intentionally leaves room for a future `PROGRESS` response type (same `requestId`, multiple messages) for Monte Carlo progress bars, without changing the framing.

**Packaging:** `scripts/build_engine.sh` runs PyInstaller against `engine.spec` to produce a single-file executable, copied into `composeApp/src/desktopMain/resources/engine-dist/`. At runtime, `EngineProcessManager` extracts it to the OS app-data directory (not the read-only install dir) on first run, `chmod +x` on Unix, then launches it — so the end user never needs Python installed. This was actually built and run end-to-end in Phase 5 (see README's Phase 5 section) — one-file cold start measured 3.7-4.7s; one-dir wasn't benchmarked but should be faster if that matters for your launch-time UX.

## 3. Multi-gap note

`AnalyzeStackupRequest.closingEquations` is a *list*, not a single expression, specifically so that Z1/Z2/Z3 can share the same `components` array and be solved in one round trip — this is what the "Multi-Closing Dimension solver" requirement needs, and it's baked into the contract from Phase 1 rather than bolted on later.

## 4. Phase 2 gotchas worth knowing before touching expression_engine/ again

Two SymPy pitfalls cost real debugging time and are worth flagging so they
don't get reintroduced:

- **`evalf(subs=...)` needs Symbol-keyed dicts, not string-keyed ones**, once
  symbols are created with `real=True` (which `build_symbols()` does, so
  `sqrt`/trig simplify sensibly). A string-keyed substitution dict silently
  fails to substitute anything — no exception, `evalf()` just returns the
  expression unevaluated, which then blows up wherever the caller tries to
  `float()` it. Always go through `effective_nominal_substitutions(components,
  symbols)` in `parser.py`, never hand-roll a `{c.label: value}` dict.
- **A restricted `global_dict` for `parse_expr` needs `Symbol`, `Integer`,
  `Float`, and `Rational` in it**, or parsing fails on ordinary input (an
  unrecognized name, or even a plain numeric literal like `2` in `2*A*B`)
  with a confusing internal `NameError` instead of either succeeding or
  raising our own clear `ExpressionError`.

## 5. Phase 5: the appResourcesRootDir / getResourceAsStream distinction

Compose Desktop has TWO separate mechanisms for shipping extra files in a
native bundle, and they are easy to conflate:

- **`nativeDistributions { appResourcesRootDir = ... }`** copies files into a
  distinct directory inside the native app bundle, read at runtime via a
  special system property (`compose.application.resources.dir` or similar) —
  NOT via ordinary JVM classpath resource loading.
- **The ordinary Kotlin/JVM resources source set** (`src/desktopMain/resources/**`)
  gets bundled onto the classpath automatically by Gradle, and is read via
  `Class.getResourceAsStream(...)` — which is what `EngineProcessManager.kt`
  actually uses to load the frozen engine executable.

`composeApp/build.gradle.kts` originally set `appResourcesRootDir` to the
SAME directory the ordinary resources source set already covers. That's not
what `getResourceAsStream` reads from, so it was pure redundancy — copying
the (~85MB) frozen engine into the bundle a second time via a mechanism
nothing in this codebase actually reads. Removed; the ordinary resources
source set is sufficient and was confirmed to be by staging a real build (see
README's Phase 5 section) and checking the resulting path
(`engine-dist/linux-x64/tolerance-engine`) matches exactly what
`platformResourceDir()` constructs.

## 6. SLSQP is starting-point sensitive for the tolerance optimizer

Verified empirically while building the Phase 2 optimizer: starting SLSQP
exactly at a component's *current* tolerance value can converge to a
feasible-but-far-from-optimal point while still reporting `success: True`.
`optimize_tolerances()` now multi-starts from a small spread of candidate
points (bounds midpoint, both bound extremes, current value) and keeps the
best result that's actually feasible against the constraint — see
`optimization/tolerance_optimizer.py`.
