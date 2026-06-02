# =============================================================================
# console-demo-down.ps1 — TASK-MONO-170
# Tear down the full console demo stack (all 6 compose projects + Traefik).
# Reverse order of console-demo-up.ps1. Volumes preserved by default.
#
# Usage:  pnpm console-demo:down   (or)   .\scripts\console-demo-down.ps1 [-Volumes]
#   -Volumes   also remove named volumes (wipes seeded data — full reset).
# =============================================================================
[CmdletBinding()]
param([switch]$Volumes)

$ErrorActionPreference = 'Continue'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$DownArgs = if ($Volumes) { @('down', '-v') } else { @('down') }

function Write-Phase($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

$projects = @(
    'projects/platform-console',
    'projects/erp-platform',
    'projects/finance-platform',
    'projects/scm-platform',
    'projects/wms-platform',
    'projects/global-account-platform'
)
foreach ($p in $projects) {
    Write-Phase "down: $p"
    docker compose --project-directory (Join-Path $RepoRoot $p) @DownArgs | Out-Host
}

Write-Phase 'down: Traefik'
pnpm --dir $RepoRoot traefik:down | Out-Host

Write-Host "`n[OK] console demo stack stopped." -ForegroundColor Green
if (-not $Volumes) { Write-Host '     (volumes preserved — re-run console-demo:up to resume; add -Volumes for a full data reset)' }
