#Requires -Version 5.1
<#
RelayHarbor local CI: GitHub Actions / hosted runners are not used.
Modes:
  -Mode Pr -Merge : build/test owner PRs bearing "ci:ready" then squash-merge
  -Mode Release   : build/test/sign an already-version-bumped main, verify
                    certificate against v0.5.2 and publish GitHub Release
Windows PowerShell 5.1; run as the same interactive Windows user as gh auth.
#>
[CmdletBinding()]
param(
    [ValidateSet('Pr','Release')][string]$Mode = 'Pr',
    [switch]$Merge,
    [string]$Repo = 'automationseDev/RelayHarbor-Public'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$State = Join-Path $env:LOCALAPPDATA 'RelayHarbor-CI'
$ToolGradle = Join-Path $State 'tools\gradle-8.11.1\bin\gradle.bat'
$LogDir = Join-Path $State 'logs'
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
$Mutex = New-Object System.Threading.Mutex($false, 'Local\RelayHarborLocalCi')
if (-not $Mutex.WaitOne(0)) {
    Write-Host 'Another RelayHarbor local CI run is active. Skipping.'
    exit 0
}
$LogFile = Join-Path $LogDir ("ci-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
$Transcribing = $false

function Assert-Exit([string]$Description) {
    if ($LASTEXITCODE -ne 0) { throw "$Description failed (exit code $LASTEXITCODE)" }
}
function Invoke-GhJson([string[]]$Arguments) {
    $result = & gh @Arguments 2>&1
    Assert-Exit ('gh ' + ($Arguments -join ' '))
    $text = ($result | Out-String).Trim()
    if (-not $text) { return $null }
    return ($text | ConvertFrom-Json)
}
function Git([string[]]$Arguments) {
    & git @Arguments
    Assert-Exit ('git ' + ($Arguments -join ' '))
}
function Check-Tools {
    foreach ($name in @('git','gh','python','java')) {
        if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
            throw "$name not found. Run Setup-RelayCi.ps1 first."
        }
    }
    if (-not (Test-Path -LiteralPath $ToolGradle -PathType Leaf)) {
        throw "Gradle 8.11.1 missing: $ToolGradle. Run Setup-RelayCi.ps1."
    }
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else {
        Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    }
    if (-not (Test-Path (Join-Path $sdk 'platforms\android-35'))) {
        throw "Android SDK 35 not found under $sdk. Install SDK Platform 35 in Android Studio."
    }
    if (-not (Test-Path (Join-Path $sdk 'build-tools\35.0.0'))) {
        throw "Android build tools 35.0.0 missing under $sdk."
    }
    $env:ANDROID_HOME = $sdk
    & gh auth status -h github.com 2>&1 | Out-Null
    Assert-Exit 'gh auth status'
    return $sdk
}
function Build-Check([string]$Path, [switch]$Signed) {
    Push-Location $Path
    try {
        & python scripts/check_public_contract.py
        Assert-Exit 'public update contract'
        & python scripts/privacy_preflight.py
        Assert-Exit 'privacy preflight'
        & python -m pytest -q relay/test_server.py relay/test_connections.py relay/test_rss.py
        Assert-Exit 'Relay API tests'
        if ($Signed) {
            & $ToolGradle --no-daemon :app:assembleRelease :app:testReleaseUnitTest
            Assert-Exit 'signed release Android build and unit tests'
        } else {
            & $ToolGradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
            Assert-Exit 'Android debug build and unit tests'
        }
    } finally {
        Pop-Location
    }
}
function Send-Status([string]$Sha, [string]$StateValue, [string]$Description) {
    $args = @('api','-X','POST',"repos/$Repo/statuses/$Sha",
        '-f',"state=$StateValue",
        '-f','context=relayharbor/local-ci',
        '-f',"description=$Description")
    & gh @args 2>&1 | Out-Null
    Assert-Exit 'GitHub status update'
}
function Require-Pr([object]$Pr) {
    $labels = @($Pr.labels | ForEach-Object { $_.name })
    return (-not $Pr.isDraft -and $Pr.baseRefName -eq 'main' -and
        $Pr.headRepositoryOwner.login -eq 'automationseDev' -and
        $Pr.author.login -eq 'automationseDev' -and
        -not $Pr.isCrossRepository -and $labels -contains 'ci:ready')
}
function Run-Pr {
    Push-Location $Root
    try {
        Git @('fetch','origin','main','--prune')
        $prs = @(Invoke-GhJson @('pr','list','-R',$Repo,'--state','open',
            '--limit','100','--json',
            'number,headRefOid,headRefName,headRepositoryOwner,author,baseRefName,isDraft,isCrossRepository,labels'))
        $eligible = @($prs | Where-Object { $_ -and (Require-Pr $_) })
        if ($eligible.Count -eq 0) { Write-Host 'No approved ci:ready PR found.'; return }
        foreach ($pr in $eligible) {
            $id = [int]$pr.number
            $sha = [string]$pr.headRefOid
            $short = $sha.Substring(0,12)
            Write-Host "Processing PR #$id @ $short"
            $work = Join-Path $State ("pr-" + $id + "-" + $short)
            $attached = $false
            try {
                Send-Status $sha 'pending' "Windows local CI is building PR #$id"
                Git @('fetch','origin',"refs/pull/$id/head")
                $fetched = (& git rev-parse FETCH_HEAD).Trim()
                Assert-Exit 'git rev-parse FETCH_HEAD'
                if ($fetched -ne $sha) { throw "PR head changed during fetch; retry next cycle" }
                if (Test-Path $work) { throw "Old worktree remains: $work" }
                Git @('worktree','add','--detach',$work,$sha)
                $attached = $true
                Push-Location $work
                try {
                    & git merge --no-commit --no-ff origin/main
                    Assert-Exit 'merge simulation'
                } finally { Pop-Location }
                Build-Check $work
                # Never merge stale code: recheck the full permission/label/commit gate.
                $now = Invoke-GhJson @('pr','view',"$id",'-R',$Repo,
                    '--json','number,headRefOid,headRefName,headRepositoryOwner,author,baseRefName,isDraft,isCrossRepository,labels,state')
                if ($now.state -ne 'OPEN' -or -not (Require-Pr $now) -or
                    $now.headRefOid -ne $sha) {
                    throw "PR changed or approval label removed while testing"
                }
                Send-Status $sha 'success' "Windows local CI: Android and Relay tests passed"
                if ($Merge) {
                    # This does not bypass GitHub branch rules or required reviews.
                    & gh pr merge "$id" -R $Repo --squash --delete-branch --match-head-commit $sha
                    Assert-Exit "merge PR #$id"
                    Write-Host "Merged PR #$id safely."
                } else { Write-Host "PR #$id passed; -Merge not supplied." }
            } catch {
                $msg = $_.Exception.Message
                Write-Warning "PR #$id failed: $msg"
                try { Send-Status $sha 'failure' "Local CI failed; see Windows machine logs" }
                catch { Write-Warning 'Unable to report PR status' }
                # Continue testing other PRs; failed PRs remain open.
            } finally {
                if ($attached) {
                    & git worktree remove --force $work 2>&1 | Out-Null
                    & git worktree prune 2>&1 | Out-Null
                }
            }
        }
    } finally { Pop-Location }
}
function Read-SigningConfig {
    $file = Join-Path $State 'signing.json'
    if (-not (Test-Path $file)) {
        throw 'Signing configuration missing. Run Set-RelaySigning.ps1 with the ORIGINAL production JKS.'
    }
    $cfg = Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not (Test-Path -LiteralPath $cfg.KeystorePath -PathType Leaf)) {
        throw 'The original production keystore file is missing.'
    }
    $store = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
        (ConvertTo-SecureString $cfg.StorePasswordProtected))
    $key = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
        (ConvertTo-SecureString $cfg.KeyPasswordProtected))
    try {
        $env:RELAYHARBOR_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($store)
        $env:RELAYHARBOR_KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($key)
        $env:RELAYHARBOR_KEYSTORE_PATH = [string]$cfg.KeystorePath
        $env:RELAYHARBOR_KEY_ALIAS = [string]$cfg.Alias
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($store)
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($key)
    }
}
function Get-SignerDigest([string]$Apk, [string]$Sdk) {
    $signer = Join-Path $Sdk 'build-tools\35.0.0\apksigner.bat'
    $out = (& $signer verify --print-certs $Apk 2>&1 | Out-String)
    Assert-Exit 'apksigner verify'
    if ($out -notmatch '(?i)Signer #1 certificate SHA-256 digest:\s*([0-9a-f:]+)') {
        throw 'Could not parse APK signer SHA-256 digest'
    }
    return ($Matches[1].Replace(':','').ToLowerInvariant())
}
function Run-Release([string]$Sdk) {
    Push-Location $Root
    try {
        Git @('switch','main')
        if ((& git status --porcelain | Out-String).Trim()) {
            throw 'Working directory has local modifications. Release aborted.'
        }
        Git @('fetch','origin','main','--tags')
        Git @('pull','--ff-only','origin','main')
        $gradleFile = Get-Content 'app/build.gradle.kts' -Raw
        if ($gradleFile -notmatch 'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"') {
            throw 'versionName is not a valid semantic version'
        }
        $ver = $Matches[1]
        $tag = "v$ver"
        if ($gradleFile -notmatch 'versionCode\s*=\s*(\d+)') { throw 'versionCode missing' }
        $code = [int]$Matches[1]
        if ($code -le 7) { throw 'Bump versionCode above 7 for the next release.' }
        $already = & gh release view $tag -R $Repo --json tagName 2>$null
        if ($LASTEXITCODE -eq 0) { throw "$tag release already exists" }
        $existingTag = (& git tag -l $tag | Out-String).Trim()
        if ($existingTag) { throw "$tag git tag already exists" }
        Read-SigningConfig
        try {
            Build-Check $Root -Signed
            $apk = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
            if (-not (Test-Path -LiteralPath $apk)) { throw 'Signed release APK not produced' }
            $baselineDir = Join-Path $State 'baseline'
            New-Item -ItemType Directory -Path $baselineDir -Force | Out-Null
            $baseline = Join-Path $baselineDir 'RelayHarbor-v0.5.2.apk'
            if (-not (Test-Path $baseline)) {
                & gh release download 'v0.5.2' -R $Repo -p 'RelayHarbor-v0.5.2.apk' -D $baselineDir
                Assert-Exit 'download original public signed APK'
            }
            $oldCert = Get-SignerDigest $baseline $Sdk
            $newCert = Get-SignerDigest $apk $Sdk
            if ($oldCert -ne $newCert) {
                throw 'SIGNING CERTIFICATE MISMATCH: do NOT distribute and do NOT uninstall old app.'
            }
            Write-Host "Signing certificate matches v0.5.2 ($oldCert)"
            $output = Join-Path $State ("RelayHarbor-" + $tag + '.apk')
            Copy-Item $apk $output -Force
            # Dry-run never creates a GitHub release: the following are publish operations.
            Git @('tag','-a',$tag,'-m',"RelayHarbor $tag")
            Git @('push','origin',"refs/tags/$tag")
            & gh release create $tag $output -R $Repo --verify-tag --title "RelayHarbor $tag" --generate-notes
            Assert-Exit "publish release $tag"
            Write-Host "Published $tag. Existing app can update in-place."
        } finally {
            foreach ($name in @('RELAYHARBOR_KEYSTORE_PATH','RELAYHARBOR_STORE_PASSWORD',
                'RELAYHARBOR_KEY_ALIAS','RELAYHARBOR_KEY_PASSWORD')) {
                [Environment]::SetEnvironmentVariable($name, $null, 'Process')
            }
        }
    } finally { Pop-Location }
}
try {
    Start-Transcript -Path $LogFile -Append | Out-Null
    $Transcribing = $true
    $sdk = Check-Tools
    if ($Mode -eq 'Pr') { Run-Pr } else { Run-Release $sdk }
    Write-Host "Finished. Log: $LogFile"
} catch {
    Write-Error $_.Exception.Message
    exit 1
} finally {
    if ($Transcribing) { Stop-Transcript | Out-Null }
    $Mutex.ReleaseMutex()
    $Mutex.Dispose()
}
