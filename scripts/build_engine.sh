#!/usr/bin/env bash
# Freezes engine/ into a standalone executable and stages it under
# composeApp/src/desktopMain/resources/engine-dist/<os>-<arch>/, matching
# the layout EngineProcessManager.locatePackagedExecutable() expects.
set -euo pipefail

cd "$(dirname "$0")/../engine"

python -m venv .build-venv
source .build-venv/bin/activate
pip install -e ".[dev]"

pyinstaller engine.spec --distpath dist --workpath build --noconfirm

case "$(uname -s)" in
  Darwin) OS="macos" ;;
  Linux)  OS="linux" ;;
  *)      echo "Run build_engine.ps1 on Windows" >&2; exit 1 ;;
esac

case "$(uname -m)" in
  arm64|aarch64) ARCH="arm64" ;;
  *)             ARCH="x64" ;;
esac

DEST="../desktopApp/src/main/resources/engine-dist/${OS}-${ARCH}"
mkdir -p "$DEST"
cp dist/tolerance-engine "$DEST/"

echo "Staged engine executable at $DEST/tolerance-engine"
deactivate
