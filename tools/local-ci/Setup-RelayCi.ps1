#Requires -Version 5.1
<#
One-time Windows workstation bootstrap for RelayHarbor local CI/CD.
Run from an ordinary PowerShell window; -InstallPackages may request elevation.
Requires Android SDK Platform 35 and build-tools 35.0.0 installed via Android Studio.
#>
[CmdletBinding()]
param(
    [switch]$InstallPackages,
    [switch]$InstallTask
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$state = Join-Path $env:LOCALAPPDATA 'RelayHarbor-CI'
$toolRoot = Join-Path $state 'tools'
$gradleHome = Join-Path $toolRoot 'gradle-8.11.1'
New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null

if ($InstallPackages) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw 'winget.exe was not found. Install Windows App Installer first.'
    }
    foreach ($id in @('Git.Git','GitHub.cli','EclipseAdoptium.Temurin.17.JDK',
        'Python.Python.3.12','Google.AndroidStudio')) {
        Write-Host "Installing/checking $id via winget..."
        & winget install --id $id --exact --source winget --accept-package-agreements --accept-source-agreements
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "$id may need manual installation. Restart PowerShell after winget installs."
        }
    }
}
foreach ($tool in @('git','gh','python','java')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool is not in PATH. Run: powershell -File tools/local-ci/Setup-RelayCi.ps1 -InstallPackages; then open a NEW shell."
    }
}
$javaVersion = (& java -version 2>&1 | Out-String)
if ($javaVersion -notmatch '(?i)(?:version |openjdk )["]?17[.\s"]') {
    throw "Use JDK 17, not the currently selected Java. Set JAVA_HOME and PATH for the CI user."
}
$pythonVersion = (& python --version 2>&1 | Out-String)
if ($pythonVersion -notmatch 'Python 3\.(1[2-9]|[2-9][0-9])\.') {
    throw "Python 3.12+ is required for the Relay server. Currently: $pythonVersion"
}

if (-not (Test-Path (Join-Path $gradleHome 'bin\gradle.bat'))) {
    Write-Host 'Downloading verified Gradle 8.11.1 distribution from Gradle.org...'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $zip = Join-Path $toolRoot 'gradle-8.11.1-bin.zip'
    $shaFile = "$zip.sha256"
    Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -OutFile $zip -UseBasicParsing
    Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256' -OutFile $shaFile -UseBasicParsing
    $expected = ((Get-Content $shaFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        Remove-Item $zip -Force
        throw 'Gradle archive SHA256 does not match the official checksum!'
    }
    Expand-Archive -LiteralPath $zip -DestinationPath $toolRoot -Force
    Remove-Item $zip,$shaFile -Force
}
$gradle = Join-Path $gradleHome 'bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw 'Gradle installation failed.' }
Write-Host "Gradle: $gradle"

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else {
    Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
if (-not (Test-Path (Join-Path $sdk 'platforms\android-35'))) {
    throw "Install Android SDK Platform 35 using Android Studio SDK Manager. SDK path: $sdk"
}
if (-not (Test-Path (Join-Path $sdk 'build-tools\35.0.0'))) {
    throw "Install Android SDK Build-Tools 35.0.0 via Android Studio SDK Manager."
}
$env:ANDROID_HOME = $sdk

& gh auth status -h github.com 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Log in with: gh auth login -h github.com -p https -w'
}
$venv = Join-Path $state 'venv'
$venvPython = Join-Path $venv 'Scripts\python.exe'
if (-not (Test-Path $venvPython)) {
    & python -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw 'Python virtual environment creation failed' }
}
& $venvPython -m pip install --disable-pip-version-check -r (Join-Path $root 'relay\requirements.txt') 'pytest==8.3.5'
if ($LASTEXITCODE -ne 0) { throw 'Relay Python dependencies installation failed' }

Push-Location $root
try {
    & python scripts/check_public_contract.py
    if ($LASTEXITCODE -ne 0) { throw 'Public update contract verification failed' }
    & python scripts/privacy_preflight.py
    if ($LASTEXITCODE -ne 0) { throw 'Public privacy preflight failed' }
    & gh label create 'ci:ready' -R 'automationseDev/RelayHarbor-Public' --color '1D76DB' --description 'Trusted PR approved for local CI and conditional merge' --force | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create ci:ready label. Check GitHub CLI authorization.' }
} finally { Pop-Location }

if ($InstallTask) {
    $runner = Join-Path $root 'tools\local-ci\Run-RelayCi.ps1'
    $arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $runner + '" -Mode Pr -Merge'
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments -WorkingDirectory $root
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Minutes 10) -RepetitionDuration (New-TimeSpan -Days 365)
    $principal = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
    Register-ScheduledTask -TaskName 'RelayHarborLocalCI' -Action $action -Trigger $trigger -Principal $principal -Description 'Windows-local CI validates trusted PRs and merges only after successful Android+Relay tests' -Force | Out-Null
    Write-Host 'Installed: Task Scheduler > RelayHarborLocalCI (every 10 minutes, user logged in)'
}
Write-Host 'Local CI ready. Manual test: powershell -File tools/local-ci/Run-RelayCi.ps1 -Mode Pr -Merge'
Write-Host 'Next PR: gh pr edit NUMBER -R automationseDev/RelayHarbor-Public --add-label ci:ready'
Write-Host 'Release signing: powershell -File tools/local-ci/Set-RelaySigning.ps1 -KeystorePath C:\secure\original-release.jks -Alias relayharbor'
