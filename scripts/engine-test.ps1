[CmdletBinding()]
param(
    # Catch2 suites built by scripts/engine.ps1: OrcaSlicer's libslic3r_tests and
    # fff_print_tests, and the app facade's orca_engine_adapter_tests.
    [string[]] $Suite = @('libslic3r_tests', 'fff_print_tests', 'orca_engine_adapter_tests'),
    # Catch2 test spec, for example "[GCode]" or "~[Slow]".
    [string] $Filter = '',
    # adb serial when several devices are connected.
    [string] $Serial = ''
)

$ErrorActionPreference = 'Stop'
# "powershell -File" passes "a,b" as one string.
$Suite = @($Suite | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
foreach ($name in $Suite) {
    if ($name -notin 'libslic3r_tests', 'fff_print_tests', 'orca_engine_adapter_tests') {
        throw "Unknown suite $name"
    }
}
$repo = Split-Path -Parent $PSScriptRoot
$engineBuild = Join-Path $repo 'engine/build/engine-arm64'
$testData = Join-Path $repo 'upstream/OrcaSlicer/tests/data'
# Must match ENGINE_DEVICE_TEST_DIR in engine/CMakeLists.txt, which is compiled into TEST_DATA_DIR.
$deviceDir = '/data/local/tmp/orca-engine-tests'

. "$PSScriptRoot/android-tools.ps1"
$tools = Get-AndroidTools -Repo $repo
$adb = $tools.Adb
$strip = $tools.Strip
$deviceBinaries = Join-Path $engineBuild 'device'
$adbTarget = @()
if ($Serial) {
    $adbTarget = @('-s', $Serial)
}

function Invoke-Adb {
    param([Parameter(Mandatory)][string[]] $Arguments)
    & $adb @adbTarget @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

Invoke-Adb @('shell', "rm -rf $deviceDir/data $deviceDir/tmp $deviceDir/orca && mkdir -p $deviceDir/tmp $deviceDir/orca/data/system $deviceDir/orca/resources")
Invoke-Adb @('push', $testData, "$deviceDir/")

if ($Suite -contains 'orca_engine_adapter_tests') {
    # Same layout the app materializes: vendor bundles in data/system, runtime tables in resources.
    $orcaResources = Join-Path $repo 'upstream/OrcaSlicer/resources'
    foreach ($folder in 'info', 'flush') {
        Invoke-Adb @('push', (Join-Path $orcaResources $folder), "$deviceDir/orca/resources/")
    }
    foreach ($vendor in 'OrcaFilamentLibrary', 'Creality') {
        Invoke-Adb @('push', (Join-Path $orcaResources "profiles/$vendor.json"), "$deviceDir/orca/data/system/")
        Invoke-Adb @('push', (Join-Path $orcaResources "profiles/$vendor"), "$deviceDir/orca/data/system/")
    }
}

$failed = @()
foreach ($name in $Suite) {
    $binary = Join-Path $engineBuild "tests/$($name -replace '_tests$', '')/$name"
    if (-not (Test-Path -LiteralPath $binary)) {
        throw "$binary is missing; run scripts/engine.ps1 -Stage engine -Target $name"
    }
    # Debug info makes the suites ~900 MB; the unstripped build stays for symbolizing crashes.
    New-Item -ItemType Directory -Force -Path $deviceBinaries | Out-Null
    $stripped = Join-Path $deviceBinaries $name
    & $strip --strip-debug -o $stripped $binary
    if ($LASTEXITCODE -ne 0) {
        throw "llvm-strip failed for $binary"
    }
    Invoke-Adb @('push', $stripped, "$deviceDir/$name")
    Write-Output "=== $name $Filter"
    $spec = ''
    if ($Filter) {
        $spec = "'$Filter'"
    }
    # Boost.Filesystem temp paths default to /tmp, which Android does not have.
    & $adb @adbTarget shell "cd $deviceDir && chmod 755 $name && TMPDIR=$deviceDir/tmp ./$name $spec"
    if ($LASTEXITCODE -ne 0) {
        $failed += $name
    }
}

if ($failed.Count -gt 0) {
    throw "Failed on device: $($failed -join ', ')"
}
Write-Output "All selected upstream suites passed on the device"
