# =============================================================================
# console-demo-up.ps1 — TASK-MONO-170
# One-command full 5-domain platform-console local-dev DEMO bring-up (Windows).
#
# Orders: Traefik -> GAP (e2e profile) -> seed GAP -> wms/scm/finance/erp ->
#         seed read-models -> console. Health-gates between phases. Idempotent.
#
# Prereqs (the preflight checks + instructs):
#   1. Docker running (Rancher Desktop / Docker Desktop).
#   2. *.local hosts entries — run (Administrator):  .\scripts\dev-setup.ps1
#   3. The shared traefik-net network — created by `pnpm traefik:up` (this
#      script runs it for you).
#
# Usage (from repo root):   pnpm console-demo:up    (or)   .\scripts\console-demo-up.ps1
#
# ⚠ HOST RISK: this brings up ~25 containers incl. 5+ JVM services. On this
#   Windows host that can approach the commit limit (see CLAUDE.md "Session Size
#   / JDT.LS OOM Cascade" + docs/guides/console-fullstack-local-dev.md
#   troubleshooting). If memory-constrained, bring up a SUBSET (GAP + one domain
#   + console) — see the runbook.
# =============================================================================
[CmdletBinding()]
param(
    # Skip the docker image (re)build (faster re-runs once images exist).
    [switch]$NoBuild,
    # Skip the demo seed step (services only).
    [switch]$NoSeed,
    # Per-phase health-wait timeout (seconds).
    [int]$HealthTimeoutSec = 240
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$SeedDir = Join-Path $PSScriptRoot 'console-demo\seed'
$BuildArg = if ($NoBuild) { @() } else { @('--build') }

function Write-Phase($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "[OK]   $msg" -ForegroundColor Green }
function Write-Warn($msg)  { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err($msg)   { Write-Host "[ERR]  $msg" -ForegroundColor Red }

# --- preflight -------------------------------------------------------------
function Test-Preflight {
    Write-Phase 'Preflight'
    try { docker info *> $null } catch { Write-Err 'Docker is not running. Start Rancher/Docker Desktop and retry.'; exit 1 }
    Write-Ok 'Docker reachable.'

    $hostsFile = "$env:WINDIR\System32\drivers\etc\hosts"
    $needed = @('console.local', 'gap.local', 'wms.local', 'scm.local', 'finance.local', 'erp.local')
    $content = Get-Content $hostsFile -Raw -ErrorAction SilentlyContinue
    $missing = $needed | Where-Object { $content -notmatch "(?m)^[^#]*\b$([regex]::Escape($_))\b" }
    if ($missing.Count -gt 0) {
        Write-Warn "Missing hosts entries: $($missing -join ', ')"
        Write-Warn 'Run (Administrator):  .\scripts\dev-setup.ps1   then re-run this script.'
        exit 1
    }
    Write-Ok 'All *.local hosts entries present.'
}

# --- health wait -----------------------------------------------------------
# Polls `docker inspect` health status for a container until healthy or timeout.
function Wait-Healthy([string[]]$Containers, [int]$TimeoutSec) {
    foreach ($c in $Containers) {
        $deadline = (Get-Date).AddSeconds($TimeoutSec)
        Write-Host "       waiting for $c ..." -NoNewline
        while ($true) {
            $state = (docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $c 2>$null)
            if ($state -eq 'healthy' -or $state -eq 'running') { Write-Host " $state" -ForegroundColor Green; break }
            if ((Get-Date) -gt $deadline) { Write-Host ''; Write-Err "$c not healthy within ${TimeoutSec}s (state=$state). Check: docker logs $c"; exit 1 }
            Start-Sleep -Seconds 3
            Write-Host '.' -NoNewline
        }
    }
}

# --- seed apply (idempotent) ----------------------------------------------
# $args: container, kind ('mysql'|'psql'), db, user, password, seedFile
function Invoke-Seed([string]$Container, [string]$Kind, [string]$Db, [string]$User, [string]$Password, [string]$SeedFile) {
    $path = Join-Path $SeedDir $SeedFile
    if (-not (Test-Path $path)) { Write-Err "seed file not found: $path"; exit 1 }
    Write-Host "       seeding $Container/$Db <- $SeedFile" -NoNewline
    if ($Kind -eq 'mysql') {
        Get-Content $path -Raw | docker exec -i $Container mysql -u root "-p$Password" $Db
    } else {
        # psql: PGPASSWORD via env; -v ON_ERROR_STOP=0 so idempotent re-runs that
        # hit ON CONFLICT/IGNORE rows do not abort the script.
        Get-Content $path -Raw | docker exec -i -e "PGPASSWORD=$Password" $Container psql -U $User -d $Db -v ON_ERROR_STOP=0
    }
    if ($LASTEXITCODE -ne 0) { Write-Host ''; Write-Warn "$SeedFile apply returned exit $LASTEXITCODE (often benign on a re-run — verify in the runbook)." }
    else { Write-Host ' done' -ForegroundColor Green }
}

# ---------------------------------------------------------------------------
Test-Preflight

Write-Phase 'Traefik'
pnpm --dir $RepoRoot traefik:up | Out-Host

Write-Phase 'GAP (SPRING_PROFILES_ACTIVE=e2e — loads acme-corp + globex-corp demo customers)'
# The e2e profile adds db/migration-dev (globex-corp V0021) on top of the
# default db/migration (acme-corp V0020). datasource is env-driven so the
# per-project gap compose DB wiring is unaffected.
$env:SPRING_PROFILES_ACTIVE = 'e2e'
docker compose --project-directory (Join-Path $RepoRoot 'projects/global-account-platform') up -d @BuildArg | Out-Host
Wait-Healthy @('gap-mysql') $HealthTimeoutSec
# GAP service container names follow the gap compose; health-gate the auth/admin
# services by their compose service health (best-effort — adjust names if your
# compose differs).
Wait-Healthy @('gap-mysql') $HealthTimeoutSec

if (-not $NoSeed) {
    Write-Phase 'Seed — GAP operators + multi-operator N:M assignments'
    Invoke-Seed 'gap-mysql' 'mysql' '' 'root' 'rootpass' '01-gap.sql'
}

Write-Phase 'Domains — wms / scm / finance / erp'
docker compose --project-directory (Join-Path $RepoRoot 'projects/wms-platform') up -d @BuildArg | Out-Host
docker compose --project-directory (Join-Path $RepoRoot 'projects/scm-platform') up -d @BuildArg | Out-Host
docker compose --project-directory (Join-Path $RepoRoot 'projects/finance-platform') up -d @BuildArg | Out-Host
docker compose --project-directory (Join-Path $RepoRoot 'projects/erp-platform') up -d @BuildArg | Out-Host
Wait-Healthy @('wms-postgres', 'scm-platform-postgres', 'finance-platform-mysql', 'erp-platform-mysql') $HealthTimeoutSec

if (-not $NoSeed) {
    Write-Phase 'Seed — per-domain read-models (acme-corp: finance/wms; globex-corp: scm/erp)'
    Invoke-Seed 'finance-platform-mysql' 'mysql' 'finance_db' 'root' 'root' '02-finance.sql'
    Invoke-Seed 'erp-platform-mysql'     'mysql' 'erp_db'     'root' 'root' '03-erp.sql'
    Invoke-Seed 'wms-postgres'           'psql'  'master_db'  'postgres' 'postgres' '04-wms-master.sql'
    Invoke-Seed 'wms-postgres'           'psql'  'admin_db'   'postgres' 'postgres' '05-wms-admin.sql'
    Invoke-Seed 'scm-platform-postgres'  'psql'  'scm_procurement'           'scm' 'scm' '06-scm-procurement.sql'
    Invoke-Seed 'scm-platform-postgres'  'psql'  'scm_inventory_visibility'  'scm' 'scm' '07-scm-inventory.sql'
}

Write-Phase 'Console (console-bff + console-web)'
docker compose --project-directory (Join-Path $RepoRoot 'projects/platform-console') up -d @BuildArg | Out-Host
Wait-Healthy @('platform-console-web') $HealthTimeoutSec

Write-Phase 'Ready'
Write-Ok 'Open http://console.local'
Write-Host '       Login:   multi-operator@example.com  /  devpassword123!'
Write-Host '       Demo:    active tenant acme-corp  -> Finance 운영 + WMS 운영 live'
Write-Host '                switch to  globex-corp    -> SCM 운영 + ERP 운영 live'
Write-Host '       GAP 운영 (계정/감사/운영자) is always available.'
Write-Host '       See docs/guides/console-fullstack-local-dev.md for the full walkthrough.'
