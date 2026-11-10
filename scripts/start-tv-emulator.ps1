param([switch]$ColdBoot)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:ANDROID_HOME = Join-Path $projectRoot '.tools/android-sdk'
$env:ANDROID_AVD_HOME = Join-Path $projectRoot '.tools/avd'
$emulator = Join-Path $env:ANDROID_HOME 'emulator/emulator.exe'
$avd = 'AnimeFlix_TV_API_36'
if (-not (Test-Path $emulator) -or -not (Test-Path (Join-Path $env:ANDROID_AVD_HOME "$avd.ini"))) {
    throw 'Falta el emulador o la imagen de Android TV. Consulta docs/emulador-tv.md.'
}
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$existing = @(& $adb devices 2>$null | Where-Object { $_ -match '^emulator-5554\s' })
if ($existing.Count -gt 0) {
    if ($existing[0] -match '\sdevice(?:\s|$)') {
        $runningName = @(& $adb -s emulator-5554 emu avd name 2>$null)
        if ($runningName -contains $avd) { Write-Output 'Android TV ya está abierto (emulator-5554).'; return }
    }
    throw 'El puerto emulator-5554 está ocupado o el emulador aún está arrancando. Espera a que termine antes de iniciarlo otra vez.'
}
$logFolder = Join-Path $projectRoot '.tools/emulator-logs'
New-Item -ItemType Directory -Force $logFolder | Out-Null
$arguments = @('-avd', $avd, '-port', '5554', '-memory', '3072', '-gpu', 'auto', '-no-boot-anim', '-netdelay', 'none', '-netspeed', 'full')
if ($ColdBoot) { $arguments += '-no-snapshot-load' }
# An interactive emulator window is intentional: this is the TV and virtual remote under test.
$process = Start-Process -FilePath $emulator -ArgumentList $arguments -PassThru -WindowStyle Normal `
    -RedirectStandardOutput (Join-Path $logFolder 'tv-output.log') -RedirectStandardError (Join-Path $logFolder 'tv-error.log')
Write-Output "Android TV iniciado (proceso $($process.Id)). ADB: emulator-5554."
