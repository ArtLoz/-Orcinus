[CmdletBinding()]
param(
    # Case names from scripts/golden/cases.json; all cases by default.
    [string[]] $Model = @(),
    # adb serial when several devices are connected.
    [string] $Serial = ''
)

# Slices the same models with the same profiles on a device, through the app's
# engine facade, and with the official desktop OrcaSlicer build of the pinned
# release, then compares the G-code (scripts/golden/golden.py).
# Needs: orca_engine_slice built by scripts/engine.ps1, Python 3, a device.

$ErrorActionPreference = 'Stop'
# "powershell -File" passes "a,b" as one string.
$Model = @($Model | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
$repo = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/android-tools.ps1"
$tools = Get-AndroidTools -Repo $repo
$adbTarget = @()
if ($Serial) {
    $adbTarget = @('-s', $Serial)
}

# Official Windows build of the release pinned in upstream/orca.lock.json.
$referenceRelease = 'v2.4.2'
$referenceUrl = 'https://github.com/OrcaSlicer/OrcaSlicer/releases/download/v2.4.2/OrcaSlicer_Windows_V2.4.2_x64_portable.zip'
$referenceSha256 = 'feba3009dfb9d268779cca5758a1a5bc3b7d0722bf8fa48d5c57340de975d6be'

$lock = Get-Content -LiteralPath (Join-Path $repo 'upstream/orca.lock.json') -Raw | ConvertFrom-Json
if ($lock.release -ne $referenceRelease) {
    throw "upstream is $($lock.release), the desktop reference is ${referenceRelease}: update the reference in scripts/golden.ps1"
}

$work = Join-Path $repo 'engine/build/golden'
$golden = Join-Path $PSScriptRoot 'golden/golden.py'
$cases = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'golden/cases.json') -Raw | ConvertFrom-Json
if ($Model.Count -eq 0) {
    $Model = @($cases.models.PSObject.Properties.Name)
}
$deviceDir = '/data/local/tmp/orca-golden'
$slicer = Join-Path $repo 'engine/build/engine-arm64/tests/golden/orca_engine_slice'
if (-not (Test-Path -LiteralPath $slicer)) {
    throw "$slicer is missing; run scripts/engine.ps1 -Stage engine -Target orca_engine_slice"
}

function Invoke-Checked {
    param([Parameter(Mandatory)][string] $File, [string[]] $Arguments = @())
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$File $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Invoke-Adb {
    param([Parameter(Mandatory)][string[]] $Arguments)
    Invoke-Checked $tools.Adb (@($adbTarget) + $Arguments)
}

function Initialize-Reference {
    $directory = Join-Path $repo "engine/build/tools/OrcaSlicer-$referenceRelease"
    $executable = Join-Path $directory 'orca-slicer.exe'
    if (Test-Path -LiteralPath $executable) {
        return $executable
    }
    $archive = Join-Path $repo "engine/build/downloads/$(Split-Path -Leaf $referenceUrl)"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $archive), $directory | Out-Null
    if (-not (Test-Path -LiteralPath $archive)) {
        Invoke-WebRequest -Uri $referenceUrl -OutFile "$archive.part" -UseBasicParsing
        Move-Item -LiteralPath "$archive.part" -Destination $archive
    }
    $hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $referenceSha256) {
        throw "OrcaSlicer archive hash mismatch: $hash"
    }
    Invoke-Checked "$env:SystemRoot\System32\tar.exe" @('-xf', $archive, '-C', $directory)
    return $executable
}

$python = (Get-Command python, py -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $python) {
    throw 'Python 3 is required'
}
$desktop = Initialize-Reference
Invoke-Checked $python @($golden, 'prepare', '--out', $work)

# Same layout the app materializes: vendor bundles in data/system, runtime tables in resources.
$orcaResources = Join-Path $repo 'upstream/OrcaSlicer/resources'
Invoke-Adb @('shell', "rm -rf $deviceDir && mkdir -p $deviceDir/orca/data/system $deviceDir/orca/resources $deviceDir/tmp $deviceDir/out")
foreach ($folder in 'info', 'flush') {
    Invoke-Adb @('push', (Join-Path $orcaResources $folder), "$deviceDir/orca/resources/")
}
foreach ($vendor in $cases.profiles.vendors) {
    Invoke-Adb @('push', (Join-Path $orcaResources "profiles/$vendor.json"), "$deviceDir/orca/data/system/")
    Invoke-Adb @('push', (Join-Path $orcaResources "profiles/$vendor"), "$deviceDir/orca/data/system/")
}
Invoke-Adb @('push', (Join-Path $work 'models'), "$deviceDir/")
$stripped = Join-Path $work 'orca_engine_slice'
Invoke-Checked $tools.Strip @('--strip-debug', '-o', $stripped, $slicer)
Invoke-Adb @('push', $stripped, "$deviceDir/orca_engine_slice")
Invoke-Adb @('shell', "chmod 755 $deviceDir/orca_engine_slice")

$profiles = $cases.profiles
New-Item -ItemType Directory -Force -Path (Join-Path $work 'android') | Out-Null
foreach ($name in $Model) {
    Write-Output "=== $name"
    $desktopOut = Join-Path $work "desktop/$name"
    Remove-Item -LiteralPath $desktopOut -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $desktopOut | Out-Null
    # --arrange 0 keeps the model where prepare placed it. The desktop build is a
    # GUI executable, so Start-Process waits for it and returns its exit code.
    $arguments = @(
        '--datadir', "`"$work\datadir`"",
        '--load-settings', "`"$work\profiles\machine.json;$work\profiles\process.json`"",
        '--load-filaments', "`"$work\profiles\filament.json`"",
        '--arrange', '0', '--slice', '0', '--outputdir', "`"$desktopOut`"",
        "`"$work\models\$name.stl`""
    )
    $process = Start-Process -FilePath $desktop -ArgumentList $arguments -Wait -PassThru -NoNewWindow
    if ($process.ExitCode -ne 0) {
        throw "Desktop OrcaSlicer failed on $name with exit code $($process.ExitCode)"
    }
    Invoke-Adb @('shell', "cd $deviceDir && ./orca_engine_slice $deviceDir/orca/data $deviceDir/orca/resources $deviceDir/tmp models/$name.stl out/$name.gcode '$($profiles.printer)' '$($profiles.filament)' '$($profiles.process)'")
    Invoke-Adb @('pull', "$deviceDir/out/$name.gcode", (Join-Path $work "android/$name.gcode"))
}

& $python $golden compare --out $work --models @Model
if ($LASTEXITCODE -ne 0) {
    throw 'G-code differs from desktop OrcaSlicer beyond the tolerances'
}
