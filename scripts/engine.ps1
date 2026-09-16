[CmdletBinding()]
param(
    # deps: Android dependency prefix; engine: libslic3r and upstream tests; all: both.
    [ValidateSet('deps', 'engine', 'all')]
    [string] $Stage = 'all',
    # Optional engine targets, for example libslic3r or fff_print_tests.
    [string[]] $Target = @()
)

$ErrorActionPreference = 'Stop'
# "powershell -File" passes "a,b" as one string.
$Target = @($Target | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
$repo = (Split-Path -Parent $PSScriptRoot).Replace('\', '/')
$toolchain = "$repo/engine/cmake/android-arm64.toolchain.cmake"
$depsBuild = "$repo/engine/build/deps-arm64"

# Portable MSYS2 provides bash, make, and m4 for the autotools dependencies
# (GMP, MPFR). It lives in the build tree and is never installed system-wide.
$msysArchiveUrl = 'https://repo.msys2.org/distrib/x86_64/msys2-base-x86_64-20260611.tar.xz'
$msysArchiveSha256 = 'a2d047e8ee213c3c6a49a8de427eb1069df12207c0422ff1b3cbb5c905c34221'
$msysPackages = 'make m4 diffutils gcc autoconf automake libtool texinfo'
$toolsDir = "$repo/engine/build/tools"
$msysBash = "$toolsDir/msys64/usr/bin/bash.exe"
$msysReady = "$toolsDir/msys64/.project-slicer-ready"

function Invoke-CMake {
    param([Parameter(Mandatory)][string[]] $Arguments)
    & cmake @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "cmake $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Invoke-Msys2 {
    param([Parameter(Mandatory)][string] $Command)
    $saved = @{ MSYSTEM = $env:MSYSTEM; CHERE_INVOKING = $env:CHERE_INVOKING; MSYS2_PATH_TYPE = $env:MSYS2_PATH_TYPE }
    $env:MSYSTEM = 'MSYS'
    $env:CHERE_INVOKING = '1'
    $env:MSYS2_PATH_TYPE = 'minimal'
    try {
        & $msysBash -lc $Command | Out-Host
        return $LASTEXITCODE
    } finally {
        foreach ($name in $saved.Keys) {
            Set-Item -Path "env:$name" -Value $saved[$name]
        }
    }
}

function Initialize-Msys2 {
    if (Test-Path -LiteralPath $msysReady) {
        return
    }
    if (-not (Test-Path -LiteralPath $msysBash)) {
        $archive = "$repo/engine/build/downloads/msys2/$(Split-Path -Leaf $msysArchiveUrl)"
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $archive), $toolsDir | Out-Null
        if (-not (Test-Path -LiteralPath $archive)) {
            Invoke-WebRequest -Uri $msysArchiveUrl -OutFile $archive -UseBasicParsing
        }
        $hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $msysArchiveSha256) {
            throw "MSYS2 archive hash mismatch: $hash"
        }
        & "$env:SystemRoot\System32\tar.exe" -xf $archive -C $toolsDir
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to extract MSYS2'
        }
    }
    # The first login initializes the pacman keyring. A core update ends the
    # shell, so the update runs twice as MSYS2 documents for scripted setups.
    [void](Invoke-Msys2 'true')
    [void](Invoke-Msys2 'pacman -Syuu --noconfirm')
    [void](Invoke-Msys2 'pacman -Syuu --noconfirm')
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        if ((Invoke-Msys2 "pacman -S --needed --noconfirm $msysPackages") -eq 0) {
            break
        }
    }
    if ((Invoke-Msys2 'command -v make && command -v m4 && command -v autoreconf && command -v gcc') -ne 0) {
        throw 'MSYS2 build tools are not available'
    }
    New-Item -ItemType File -Force -Path $msysReady | Out-Null
}

$cmakeVersion = (& cmake --version | Select-Object -First 1) -replace '^cmake version\s+', ''
if ([version]($cmakeVersion -replace '-.*$', '') -lt [version]'3.25') {
    throw "CMake 3.25 or newer is required; found $cmakeVersion"
}

if ($Stage -in 'deps', 'all') {
    Initialize-Msys2
    Invoke-CMake @(
        '-S', "$repo/engine/deps", '-B', $depsBuild, '-G', 'Ninja',
        "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
        "-DENGINE_POSIX_SHELL=$msysBash"
    )
    # Every dependency already builds in parallel; keep the superbuild sequential.
    Invoke-CMake @('--build', $depsBuild, '--', '-j1')
}

if ($Stage -in 'engine', 'all') {
    # engine/CMakePresets.json is shared with the Gradle build of :slicing:native.
    Push-Location "$repo/engine"
    try {
        Invoke-CMake @('--preset', 'android-arm64')
        $build = @('--build', '--preset', 'android-arm64')
        if ($Target.Count -gt 0) {
            $build += @('--target') + $Target
        }
        Invoke-CMake $build
    } finally {
        Pop-Location
    }
}
