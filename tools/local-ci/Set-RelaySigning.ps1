#Requires -Version 5.1
<#
Save the existing RelayHarbor release JKS path, alias and encrypted passwords.
Never regenerate the keystore: Android will refuse an in-place upgrade if the
signing certificate changes.
Password strings are Windows DPAPI encrypted for THIS user on THIS PC.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$KeystorePath,
    [Parameter(Mandatory=$true)][string]$Alias
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$resolved = (Resolve-Path -LiteralPath $KeystorePath -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
    throw 'Existing production signing JKS could not be found.'
}
$store = Read-Host 'Enter ORIGINAL keystore password (hidden)' -AsSecureString
$key = Read-Host 'Enter ORIGINAL key password (hidden)' -AsSecureString
$state = Join-Path $env:LOCALAPPDATA 'RelayHarbor-CI'
New-Item -ItemType Directory -Path $state -Force | Out-Null
$cfg = [ordered]@{
    KeystorePath = $resolved
    Alias = $Alias
    StorePasswordProtected = (ConvertFrom-SecureString $store)
    KeyPasswordProtected = (ConvertFrom-SecureString $key)
}
$file = Join-Path $state 'signing.json'
[System.IO.File]::WriteAllText($file, (ConvertTo-Json $cfg -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'Stored DPAPI-encrypted signing configuration for current Windows account.'
Write-Host "Config: $file"
Write-Host 'This file is deliberately OUTSIDE the Git repository.'
Write-Host 'Never publish or paste the keystore, passwords or this file.'
Write-Host 'The release runner compares the APK signing certificate to public v0.5.2 before publishing.'
