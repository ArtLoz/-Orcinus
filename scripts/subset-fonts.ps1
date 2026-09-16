[CmdletBinding()]
param()

# Builds the app's UI font from Inter, cut down to the Latin and Cyrillic
# characters the app needs, and writes it into :core:designsystem.
#
# OrcaSlicer draws its UI with HarmonyOS Sans SC, whose license forbids any
# modification, subsetting included, and is not a free software license. Inter
# is the closest free match: SIL Open Font License 1.1 without a Reserved Font
# Name, so a subset may be shipped under the same name. The release archive is
# pinned by hash; fontTools lives in a virtual environment under engine/build.

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repo 'core/designsystem/src/main/res/font'
$venv = Join-Path $repo 'engine/build/tools/fonttools'
$fontToolsRequirement = 'fonttools==4.65.0 --hash=sha256:3060b8c1fc2329fa20265b7c138614143ea7c1624e26c5c180c76aeb74deae6f'

$interRelease = 'v4.1'
$interUrl = "https://github.com/rsms/inter/releases/download/$interRelease/Inter-4.1.zip"
$interSha256 = '9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e'
# Read by scripts/notices/update_notices.py for the font's license text.
$interArchive = Join-Path $repo 'engine/build/downloads/Inter/Inter-4.1.zip'

# Basic Latin, Latin-1, Latin Extended-A, Cyrillic, general punctuation, currency,
# letterlike symbols (№), arrows, and the math operators used in dimensions.
$unicodes = 'U+0020-007E,U+00A0-00FF,U+0100-017F,U+0400-045F,U+0490-0491,U+2000-206F,U+20A0-20BF,U+2100-214F,U+2190-21FF,U+2212,U+2248,U+2260-2265'

function Invoke-Checked {
    param([Parameter(Mandatory)][string] $File, [string[]] $Arguments = @())
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$File $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Test-Sha256 {
    param([Parameter(Mandatory)][string] $Path, [Parameter(Mandatory)][string] $Expected)
    (Test-Path -LiteralPath $Path) -and (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -eq $Expected.ToUpperInvariant()
}

if (-not (Test-Sha256 $interArchive $interSha256)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $interArchive) | Out-Null
    Invoke-WebRequest -Uri $interUrl -OutFile $interArchive -UseBasicParsing
    if (-not (Test-Sha256 $interArchive $interSha256)) {
        throw "Inter $interRelease archive does not match the pinned SHA256"
    }
}

$python = (Get-Command python, py -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $python) {
    throw 'Python 3 is required'
}
$venvPython = Join-Path $venv 'Scripts/python.exe'
if (-not (Test-Path -LiteralPath $venvPython)) {
    Invoke-Checked $python @('-m', 'venv', $venv)
}
$requirements = Join-Path $venv 'requirements.txt'
Set-Content -LiteralPath $requirements -Value $fontToolsRequirement -Encoding ascii
Invoke-Checked $venvPython @('-m', 'pip', 'install', '--quiet', '--require-hashes', '-r', $requirements)

$staging = Join-Path $repo 'engine/build/tools/inter'
Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $staging, $output | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($interArchive)
try {
    foreach ($name in @('Inter-Regular.ttf', 'Inter-Bold.ttf')) {
        $entry = $zip.GetEntry("extras/ttf/$name")
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $staging $name), $true)
    }
} finally {
    $zip.Dispose()
}

foreach ($style in @(@{ Source = 'Inter-Regular.ttf'; Target = 'inter_regular.ttf' },
                     @{ Source = 'Inter-Bold.ttf'; Target = 'inter_bold.ttf' })) {
    $target = Join-Path $output $style.Target
    Invoke-Checked $venvPython @(
        '-m', 'fontTools.subset', (Join-Path $staging $style.Source),
        "--unicodes=$unicodes",
        '--layout-features=*',
        '--no-hinting',
        '--desubroutinize',
        "--output-file=$target"
    )
    '{0,-24} {1,8:N0} bytes' -f $style.Target, (Get-Item -LiteralPath $target).Length
}
