param([string[]]$Tasks = @(':app-mobile:assembleDebug', ':app-tv:assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $env:JAVA_HOME) {
        $localJdk = Get-ChildItem (Join-Path $projectRoot '.tools/java') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    }
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    }
    if (-not $env:JAVA_HOME) { throw 'Configura JAVA_HOME con la ruta de JDK 17.' }
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle'
    if (Test-Path '.tools/android-sdk/platforms/android-35/android.jar') {
        $env:ANDROID_HOME = Join-Path $projectRoot '.tools/android-sdk'
    }
    & .\gradlew.bat @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE" }
} finally { Pop-Location }
