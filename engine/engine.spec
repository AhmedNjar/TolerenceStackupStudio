# PyInstaller spec — freezes tolerance_engine into a standalone executable
# with zero external Python dependency for the end user.
#
#   pyinstaller engine.spec
#
# Output lands in engine/dist/tolerance-engine(.exe), which
# scripts/build_engine.sh then copies into
# composeApp/src/desktopMain/resources/engine-dist/<os>-<arch>/
#
# MEASURED cold-start time (Linux x86-64, one-file build, this sandbox's
# hardware): 3.7-4.7s from process launch to the READY handshake, across 3
# trials. One-file re-extracts its payload on every single launch (not just
# the first), so this cost is paid every time the app starts, not once. That
# may be noticeable for an interactive desktop app where EngineProcessManager
# blocks the UI on this handshake before anything is usable. One-dir would
# avoid the re-extraction cost but wasn't built/benchmarked here — worth
# comparing head-to-head on real target hardware before deciding.
#
# VERIFIED (Phase 5, Linux x86-64): this spec was actually built with
# `pyinstaller engine.spec` and the resulting executable was run in an
# environment with NO Python on PATH at all, then exercised through the
# full protocol — PING, multi-method ANALYZE_STACKUP, LIST_TEMPLATES,
# GET_STANDARD_TABLE, OPTIMIZE_TOLERANCES, and both GENERATE_PDF_REPORT and
# GENERATE_EXCEL_REPORT (the two heaviest dependency chains: matplotlib,
# reportlab, openpyxl) — all passed. No hiddenimports were needed here;
# PyInstaller's built-in hooks for numpy/scipy/matplotlib/pydantic/
# openpyxl/reportlab handled everything automatically. The build does print
# a `Hidden import "scipy.special._cdflib" not found` warning — that
# originates from PyInstaller's OWN bundled scipy hook (not from anything in
# this spec; it persists identically whether or not that module is listed
# here) and is a known-harmless artifact of that hook referencing a
# scipy-internal module that's been renamed/removed in newer scipy — the
# full functional test above passed with it present, so it's left alone
# rather than "fixed" in a way that wouldn't actually change anything.
# NOT yet verified: macOS and Windows builds (this sandbox is Linux-only) —
# the cross-platform logic in scripts/build_engine.sh/.ps1 and
# EngineProcessManager.kt's platform detection is written carefully but
# unverified on those OSes; budget time to test both before shipping.

# -*- mode: python ; coding: utf-8 -*-

block_cipher = None

a = Analysis(
    ["src/tolerance_engine/__main__.py"],
    pathex=["src"],
    binaries=[],
    datas=[],
    hiddenimports=[],
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name="tolerance-engine",
    console=True,          # keep a console/pipe so stdout/stderr are capturable by Kotlin
    onefile=True,
    strip=False,
    upx=False,             # UPX has caused false-positive AV flags on frozen Python exes before
    debug=False,
)
