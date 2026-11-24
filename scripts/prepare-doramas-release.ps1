param([string]$Destination = 'dist/kdoramas-release')

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $build = 'app-mobile/build/outputs/apk/doramas'
    $metadata = Get-Content "$build/output-metadata.json" -Raw | ConvertFrom-Json
    $version = $metadata.elements | Select-Object -First 1
    if ($metadata.applicationId -ne 'com.animeflix.mobile.doramas' -or -not $version) {
        throw 'Compila :app-mobile:assembleDoramas antes de preparar la entrega.'
    }

    $apk = Join-Path $build $version.outputFile
    $badging = & '.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump badging $apk
    if ($LASTEXITCODE -ne 0 -or -not $badging -or $badging -match '^application-debuggable') {
        throw 'La APK debe ser válida y no depurable.'
    }
    New-Item -ItemType Directory -Force $Destination | Out-Null
    Copy-Item -LiteralPath $apk -Destination (Join-Path $Destination 'KDoramasFlix.apk')
    @{ artifacts = @(@{
        applicationId = $metadata.applicationId
        file = 'KDoramasFlix.apk'
        versionCode = $version.versionCode
        versionName = $version.versionName
        sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        debuggable = $false
    }) } | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $Destination 'release-manifest.json') -Encoding utf8
    Write-Output "APK y manifiesto preparados en $Destination."
} finally {
    Pop-Location
}
