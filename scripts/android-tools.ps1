# Locates adb and the NDK's llvm-strip for the device scripts.
# Dot-source it: . "$PSScriptRoot/android-tools.ps1"

function Get-AndroidTools {
    param([Parameter(Mandatory)][string] $Repo)

    $sdk = $env:ANDROID_HOME
    if (-not $sdk) {
        $line = Get-Content -LiteralPath (Join-Path $Repo 'local.properties') | Where-Object { $_ -match '^sdk\.dir=' }
        $sdk = ($line -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
    }
    $ndkVersion = [regex]::Match(
        (Get-Content -LiteralPath (Join-Path $Repo 'engine/cmake/android-arm64.toolchain.cmake') -Raw),
        'set\(ORCINUS_NDK_VERSION\s+([0-9.]+)\)'
    ).Groups[1].Value
    [pscustomobject]@{
        Adb   = Join-Path $sdk 'platform-tools/adb.exe'
        Strip = Join-Path $sdk "ndk/$ndkVersion/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe"
    }
}
