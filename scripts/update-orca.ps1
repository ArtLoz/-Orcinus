[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string] $Ref
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$lockPath = Join-Path $workspaceRoot 'upstream/orca.lock.json'
$lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
$sourcePath = Join-Path $workspaceRoot $lock.sourceDirectory
$safeSourcePath = $sourcePath.Replace('\', '/')

if (-not (Test-Path -LiteralPath (Join-Path $sourcePath '.git'))) {
    throw "Initialize the OrcaSlicer submodule before updating: $sourcePath"
}

$changes = & git -c "safe.directory=$safeSourcePath" -C $sourcePath status --porcelain
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect the OrcaSlicer submodule worktree'
}
if ($changes) {
    throw 'Refusing to update an OrcaSlicer worktree with local changes'
}

& git -c "safe.directory=$safeSourcePath" -C $sourcePath fetch --depth 1 origin $Ref
if ($LASTEXITCODE -ne 0) {
    throw "Unable to fetch OrcaSlicer ref $Ref"
}

& git -c "safe.directory=$safeSourcePath" -C $sourcePath checkout --detach FETCH_HEAD
if ($LASTEXITCODE -ne 0) {
    throw "Unable to check out OrcaSlicer ref $Ref"
}

$commitOutput = & git -c "safe.directory=$safeSourcePath" -C $sourcePath rev-parse HEAD
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read the updated OrcaSlicer revision'
}
$newCommit = ($commitOutput | Select-Object -First 1).Trim()
$lock.release = $Ref
$lock.commit = $newCommit
$lock | ConvertTo-Json | Set-Content -LiteralPath $lockPath -Encoding utf8

Write-Output "OrcaSlicer updated to $Ref at $newCommit"
Write-Output 'Run the full build and regression fixtures, then stage the lock file and submodule pointer.'
