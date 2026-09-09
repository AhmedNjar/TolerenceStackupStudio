# Freezes engine/ into a standalone .exe and stages it under
# composeApp/src/desktopMain/resources/engine-dist/windows-x64/,
# matching the layout EngineProcessManager.locatePackagedExecutable() expects.
$ErrorActionPreference = "Stop"

Set-Location "$PSScriptRoot/../engine"

python -m venv .build-venv
. .build-venv/Scripts/Activate.ps1
pip install -e ".[dev]"

pyinstaller engine.spec --distpath dist --workpath build --noconfirm

$dest = "../desktopApp/src/main/resources/engine-dist/windows-x64"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item "dist/tolerance-engine.exe" -Destination $dest

Write-Host "Staged engine executable at $dest/tolerance-engine.exe"
deactivate
