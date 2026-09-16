[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$lockPath = Join-Path $workspaceRoot 'upstream/orca.lock.json'
$lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
$sourcePath = Join-Path $workspaceRoot $lock.sourceDirectory
$safeSourcePath = $sourcePath.Replace('\', '/')

if (-not (Test-Path -LiteralPath (Join-Path $sourcePath '.git'))) {
    throw "OrcaSlicer submodule is missing at $sourcePath"
}

$headOutput = & git -c "safe.directory=$safeSourcePath" -C $sourcePath rev-parse HEAD
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read the OrcaSlicer submodule revision'
}
$head = ($headOutput | Select-Object -First 1).Trim()

if ($head -ne $lock.commit) {
    throw "OrcaSlicer revision mismatch. Expected $($lock.commit), found $head"
}

$changes = & git -c "safe.directory=$safeSourcePath" -C $sourcePath status --porcelain
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect the OrcaSlicer submodule worktree'
}
if ($changes) {
    throw 'OrcaSlicer submodule contains local changes'
}

# Dependency versions are not duplicated here: engine/deps builds OrcaSlicer's
# own recipes from this checkout, so the commit and a clean tree pin them.
Write-Output "OrcaSlicer $($lock.release) verified at $head with a clean worktree"
