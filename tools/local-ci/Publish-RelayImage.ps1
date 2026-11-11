#Requires -Version 5.1
<#
Optional GHCR image publisher without GitHub Actions. Requires Docker Desktop
with Linux containers/buildx, and gh auth token with write:packages permission.
The existing NAS script can then pull stable by digest with SQLite backup.
#>
[CmdletBinding()]
param(
    [switch]$Stable,
    [string]$Repo = 'automationseDev/RelayHarbor-Public'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
foreach ($tool in @('docker','git','gh','python')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool missing; Docker Desktop is optional but required for GHCR local builds."
    }
}
Push-Location $root
try {
    & python scripts/check_public_contract.py
    if ($LASTEXITCODE -ne 0) { throw 'Public release contract failed' }
    & python scripts/privacy_preflight.py
    if ($LASTEXITCODE -ne 0) { throw 'Privacy preflight failed' }
    & docker info --format '{{.OSType}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop engine not running' }
    if ($Stable) {
        $branch = (& git branch --show-current).Trim()
        if ($branch -ne 'main') { throw 'Stable image must be published from main' }
        if ((& git status --porcelain | Out-String).Trim()) {
            throw 'Stable image requires a clean Git worktree'
        }
        & git fetch origin main
        if ($LASTEXITCODE -ne 0) { throw 'Cannot fetch origin/main' }
        if ((& git rev-parse HEAD).Trim() -ne (& git rev-parse origin/main).Trim()) {
            throw 'Stable image must be built from the exact current remote main commit'
        }
    }
    $version = (Get-Content 'app\build.gradle.kts' -Raw |
        Select-String 'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"').Matches.Groups[1].Value
    if (-not $version) { throw 'Cannot extract Android versionName' }
    $image = 'ghcr.io/automationsedev/relayharbor-public-relay'
    $short = (& git rev-parse --short=12 HEAD).Trim()
    $tags = @('--tag',"$image:sha-$short")
    if ($Stable) {
        $tags += @('--tag',"$image:v$version",'--tag',"$image:stable")
    } else { $tags += @('--tag',"$image:edge") }
    $token = (& gh auth token | Out-String).Trim()
    if (-not $token) { throw 'GitHub CLI is not authenticated' }
    try {
        $token | & docker login ghcr.io --username automationseDev --password-stdin | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'GHCR login failed. Run gh auth refresh -s write:packages or use an authorized PAT.'
        }
    } finally { $token = $null }
    $args = @('buildx','build','--platform','linux/amd64,linux/arm64',
        '--file','relay/Dockerfile','--push') + $tags + @('relay')
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw 'GHCR multi-platform build/push failed' }
    Write-Host "Published $image ($version) on channel $(if ($Stable) {'stable'} else {'edge'})"
} finally { Pop-Location }
