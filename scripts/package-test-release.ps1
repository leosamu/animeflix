param([string]$Tag = 'v0.9.0-tv-preview.1')
$ErrorActionPreference = 'Stop'
if ($Tag -notmatch '^v\d+\.\d+\.\d+-tv-preview\.\d+$') { throw 'Etiqueta de prerelease no válida.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $env:JAVA_HOME) {
        $jdk = Get-ChildItem .tools/java -Directory -Filter 'jdk-17*' | Select-Object -First 1
        $env:JAVA_HOME = $jdk.FullName
    }
    $aapt = Join-Path $projectRoot '.tools/android-sdk/build-tools/35.0.0/aapt.exe'
    $signer = Join-Path $projectRoot '.tools/android-sdk/build-tools/35.0.0/apksigner.bat'
    $notes = Join-Path $projectRoot "docs/releases/$Tag.md"
    if (-not (Test-Path $notes)) { throw 'Faltan las notas de la entrega.' }
    $releaseDir = Join-Path $projectRoot "dist/$Tag"
    if (Test-Path $releaseDir) { throw 'Ya existe esta entrega local. No se sobrescriben entregas anteriores.' }

    $artifacts = @()
    $signingReports = @()
    foreach ($module in @('app-mobile', 'app-tv')) {
        $metadata = Get-Content "$module/build/outputs/apk/release/output-metadata.json" -Raw | ConvertFrom-Json
        $element = $metadata.elements[0]
        $apk = Join-Path $projectRoot "$module/build/outputs/apk/release/$($element.outputFile)"
        $badging = (& $aapt dump badging $apk) -join "`n"
        if ($LASTEXITCODE -ne 0 -or $badging -match '(?m)^application-debuggable') { throw "APK inválido o depurable: $module" }
        $signature = @(& $signer verify --verbose --print-certs $apk)
        if ($LASTEXITCODE -ne 0) { throw "Firma no válida: $module" }
        $fingerprint = ($signature | Select-String '^Signer #1 certificate SHA-256 digest: (.+)$').Matches.Groups[1].Value
        if (-not $fingerprint) { throw "No se pudo leer el certificado: $module" }
        $artifactName = if ($module -eq 'app-tv') { 'AnimeFlix-TV.apk' } else { 'AnimeFlix-Mobile.apk' }
        $artifacts += [pscustomobject]@{
            file = $artifactName; source = $apk; applicationId = $metadata.applicationId
            versionName = $element.versionName; versionCode = $element.versionCode
            bytes = (Get-Item $apk).Length; sha256 = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
            certificateSha256 = $fingerprint; debuggable = $false; buildType = 'release'
        }
        $signingReports += "=== $artifactName ==="
        $signingReports += $signature
    }
    if (@($artifacts.certificateSha256 | Sort-Object -Unique).Count -ne 1) { throw 'Los APK tienen firmas distintas.' }
    $configFile = '.tools/android-sdk/licenses/android-sdk-license'
    if (-not (Test-Path $configFile)) { throw 'SDK incompleto.' }
    $generated = Get-Content core/data/build/generated/source/buildConfig/release/com/animeflix/data/BuildConfig.java -Raw
    if ($generated -match 'SUPABASE_(URL|PUBLISHABLE_KEY) = ""') { throw 'La entrega no tiene Supabase configurado.' }
    $unitTests = 0
    foreach ($report in Get-ChildItem core/data/build/test-results/testDebugUnitTest/TEST-*.xml) {
        [xml]$test = Get-Content $report.FullName
        if ([int]$test.testsuite.failures -ne 0 -or [int]$test.testsuite.errors -ne 0) { throw 'Hay pruebas unitarias fallidas.' }
        $unitTests += [int]$test.testsuite.tests
    }
    if ($unitTests -lt 1) { throw 'Faltan los resultados de pruebas unitarias.' }
    $instrumentedTests = 0
    foreach ($report in Get-ChildItem app-mobile/build/outputs/androidTest-results/connected/qa -Recurse -Filter 'TEST-*.xml') {
        [xml]$test = Get-Content $report.FullName
        if ([int]$test.testsuite.failures -ne 0 -or [int]$test.testsuite.errors -ne 0) { throw 'Hay pruebas instrumentadas fallidas.' }
        $instrumentedTests += [int]$test.testsuite.tests
    }
    if ($instrumentedTests -lt 1) { throw 'Faltan resultados instrumentados correctos.' }
    foreach ($module in @('app-mobile', 'app-tv')) {
        [xml]$lint = Get-Content "$module/build/reports/lint-results-release.xml"
        if (@($lint.issues.issue | Where-Object { $_.severity -in @('Error', 'Fatal') }).Count -gt 0) { throw "Lint contiene errores: $module" }
    }

    New-Item -ItemType Directory -Path $releaseDir | Out-Null
    foreach ($artifact in $artifacts) { Copy-Item -LiteralPath $artifact.source -Destination (Join-Path $releaseDir $artifact.file) }
    Copy-Item -LiteralPath $notes -Destination (Join-Path $releaseDir 'RELEASE-NOTES.md')
    $signingReports | Set-Content (Join-Path $releaseDir 'SIGNING.txt') -Encoding utf8
    $sourceFiles = @(& git ls-files --cached --others --exclude-standard | Sort-Object -Unique)
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo enumerar el código.' }
    if (@($sourceFiles | Where-Object { $_ -match '(^|/)(\.git|\.tools|dist)(/|$)|(^|/)(local\.properties|secrets\.properties|key\.properties)$|\.(jks|keystore)$' -or ($_ -match '(^|/)\.env($|\.)' -and $_ -notmatch '(^|/)\.env\.example$') }).Count -gt 0) {
        throw 'El listado de fuentes contiene archivos locales excluidos.'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $sourceName = "AnimeFlix-$Tag-source.zip"
    $sourceZip = Join-Path $releaseDir $sourceName
    $archive = [IO.Compression.ZipFile]::Open($sourceZip, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($file in $sourceFiles) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $projectRoot $file), $file.Replace('\', '/')) | Out-Null
        }
    } finally { $archive.Dispose() }
    $headCommit = (& git rev-parse HEAD).Trim()
    $sourceStatus = @(& git status --porcelain)
    $manifest = [ordered]@{
        tag = $Tag; preparedUtc = [DateTime]::UtcNow.ToString('o'); published = $false
        sourceBaseCommit = $headCommit; sourceHasUncommittedChanges = ($sourceStatus.Count -gt 0)
        sourceArchive = $sourceName; sourceArchiveSha256 = (Get-FileHash $sourceZip -Algorithm SHA256).Hash.ToLower()
        signing = 'Explicit local test signing, not a production store signing key'
        supabaseProject = 'tgbcswlcvbnwnfchmtpt'
        checks = @{ unitTestsPassed = $unitTests; instrumentedQaTestsPassed = $instrumentedTests; instrumentedDevice = 'Android TV API 36 emulator'; mobileReleaseLintErrors = 0; tvReleaseLintErrors = 0; apkSignaturesVerified = $true }
        artifacts = @($artifacts | Select-Object file,applicationId,versionName,versionCode,bytes,sha256,certificateSha256,debuggable,buildType)
        limitations = @('Real TV playback not yet validated', 'Corrupted video observed in emulator; cause undetermined', 'Password recovery end-to-end validation pending')
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $releaseDir 'release-manifest.json') -Encoding utf8
    Get-ChildItem $releaseDir -File | Sort-Object Name | ForEach-Object {
        '{0}  {1}' -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name
    } | Set-Content (Join-Path $releaseDir 'SHA256SUMS.txt') -Encoding ascii
    $bundle = Join-Path $projectRoot "dist/AnimeFlix-$Tag.zip"
    if (Test-Path $bundle) { throw 'Ya existe el ZIP de esta entrega.' }
    Compress-Archive -Path (Join-Path $releaseDir '*') -DestinationPath $bundle
    Write-Output "Entrega preparada: $releaseDir"
    Write-Output "Paquete: $bundle"
    $artifacts | Select-Object file,applicationId,versionName,versionCode,sha256
} finally { Pop-Location }
