param([string]$Serial = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    & "$PSScriptRoot/build.ps1" -Tasks @(':app-mobile:assembleDebug')
    $metadata = Get-Content 'app-mobile/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    $artifact = $metadata.elements[0]
    $adb = Join-Path $projectRoot '.tools/android-sdk/platform-tools/adb.exe'
    if (-not (Test-Path $adb)) {
        if (-not $env:ANDROID_HOME) { throw 'No se ha encontrado ADB. Configura ANDROID_HOME.' }
        $adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
    }
    if (-not $Serial) {
        $devices = @(& $adb devices | ForEach-Object { if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] } })
        if ($devices.Count -ne 1) { throw 'Conecta un dispositivo autorizado o especifica -Serial.' }
        $Serial = $devices[0]
    }
    $apk = Join-Path 'app-mobile/build/outputs/apk/debug' $artifact.outputFile
    & $adb -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Android no ha aceptado la instalación.' }
    $installed = (& $adb -s $Serial shell dumpsys package $metadata.applicationId) -join "`n"
    if ($installed -notmatch ('versionCode=' + [regex]::Escape([string]$artifact.versionCode) + '\s') -or
        $installed -notmatch ('versionName=' + [regex]::Escape($artifact.versionName) + '(?:\s|$)')) {
        throw 'La versión instalada no coincide con el APK generado.'
    }
    Write-Output "Instalación verificada: $($metadata.applicationId) $($artifact.versionName) ($($artifact.versionCode))"
} finally { Pop-Location }
