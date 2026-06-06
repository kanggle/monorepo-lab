# dev-setup.ps1 — register *.local hostnames in C:\Windows\System32\drivers\etc\hosts
#
# Idempotent: re-running is safe. Adds entries only if missing.
# See docs/adr/ADR-MONO-001-port-prefix-scaling.md for the rationale.
#
# Usage (in PowerShell, Run as Administrator):
#   .\scripts\dev-setup.ps1
#
# Requires Administrator privileges to edit the hosts file.

$ErrorActionPreference = 'Stop'

$HostsFile = "$env:WINDIR\System32\drivers\etc\hosts"
$MarkerBegin = "# BEGIN monorepo-lab dev hosts (TASK-MONO-022)"
$MarkerEnd = "# END monorepo-lab dev hosts"

$Hosts = @(
    'ecommerce.local',
    'wms.local',
    'iam.local',
    'fan-platform.local',
    'scm.local',
    'erp.local',
    'finance.local',
    'console.local',
    'console-bff.local'
)

# Confirm running as Administrator
$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "[ERROR] This script must be run as Administrator." -ForegroundColor Red
    Write-Host "[INFO]  Right-click PowerShell -> 'Run as Administrator', then re-run." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $HostsFile)) {
    Write-Host "[ERROR] hosts file not found: $HostsFile" -ForegroundColor Red
    exit 1
}

$content = Get-Content $HostsFile -Raw

if ($content -match [regex]::Escape($MarkerBegin)) {
    Write-Host "[OK] $HostsFile already contains monorepo-lab dev hosts block." -ForegroundColor Green
    Write-Host "[OK] Verifying entries..." -ForegroundColor Green
    $missing = 0
    foreach ($entry in $Hosts) {
        # match a line that contains the host AND is not a comment
        if ($content -notmatch "(?m)^[^#]*\b$([regex]::Escape($entry))\b") {
            Write-Host "[WARN] $entry not present in active entries — re-run after manual review." -ForegroundColor Yellow
            $missing++
        }
    }
    if ($missing -eq 0) {
        Write-Host "[OK] All $($Hosts.Count) hostnames mapped. Nothing to do." -ForegroundColor Green
    }
    exit 0
}

Write-Host "[INFO] Appending $($Hosts.Count) dev hostnames to $HostsFile ..." -ForegroundColor Cyan

$lines = @()
$lines += ''
$lines += $MarkerBegin
foreach ($entry in $Hosts) {
    $lines += "127.0.0.1  $entry"
}
$lines += $MarkerEnd

Add-Content -Path $HostsFile -Value $lines -Encoding ASCII

Write-Host "[OK] Added entries:" -ForegroundColor Green
foreach ($entry in $Hosts) {
    Write-Host "       127.0.0.1  $entry"
}
Write-Host ""
Write-Host "[NEXT] Start Traefik: pnpm traefik:up" -ForegroundColor Cyan
Write-Host "       Then bring up your project(s) and access via http://<project>.local/"
