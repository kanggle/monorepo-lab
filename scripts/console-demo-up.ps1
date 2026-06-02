# =============================================================================
# console-demo-up.ps1 — TASK-MONO-170
# Enable the per-domain ops DEMO on the federation-hardening-e2e stack (Windows).
#
# The federation-hardening-e2e harness already runs all 5 domains' producers +
# GAP + console-bff + console-web as CONTAINERS (the per-project `*:up` composes
# are infra-only; app services run via that harness or bootRun). This script
# adds — as an ADDITIVE overlay, leaving the CI base compose byte-unchanged —
# the two things the per-domain ops pages need beyond the BFF overview/health
# legs the base wires:
#   1. scm-gateway (the SCM ops page calls the gateway /api/v1/{procurement,
#      inventory-visibility}/** paths — the base runs the scm services directly,
#      no gateway).
#   2. console-web per-domain ops base URLs (the base leaves them unset → they
#      default to *.local, unreachable on the bridge net) → container DNS.
# Then it seeds the globex-corp per-domain rows (SCM PO + ERP masters) so the
# globex ops pages render non-empty (the base seeds acme finance + wms inventory
# + globex scm-inventory; this adds the globex SCM-PO + ERP delta).
#
# PREREQUISITE: the federation-hardening-e2e base stack must already be UP
#   (auth/account/admin/console-bff/console-web + the 5 producers + DBs). Bring
#   it up via the harness — see docs/guides/console-fullstack-local-dev.md.
#   This script DETECTS it and stops with guidance if absent.
#
# Usage (repo root):  pnpm console-demo:up   (or)   .\scripts\console-demo-up.ps1
# =============================================================================
[CmdletBinding()]
param([switch]$NoBuild, [switch]$NoSeed, [int]$HealthTimeoutSec = 180)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$SeedDir = Join-Path $PSScriptRoot 'console-demo\seed'
$DockerDir = Join-Path $RepoRoot 'tests\federation-hardening-e2e\docker'
$Base = Join-Path $DockerDir 'docker-compose.federation-e2e.yml'
$Demo = Join-Path $DockerDir 'docker-compose.federation-e2e.demo.yml'
$Proj = 'federation-hardening-e2e'

function Write-Phase($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Write-Ok($m)    { Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Err($m)   { Write-Host "[ERR]  $m" -ForegroundColor Red }

# --- preflight: base harness must be running ------------------------------
Write-Phase 'Preflight — federation-hardening-e2e base must be UP'
try { docker info *> $null } catch { Write-Err 'Docker is not running.'; exit 1 }
$authUp = docker ps --filter "name=federation-hardening-e2e-auth-service-1" --filter "status=running" --format '{{.Names}}'
if (-not $authUp) {
    Write-Err 'federation-hardening-e2e base stack is NOT running (auth-service absent).'
    Write-Host '       Bring the base harness up first (it builds + runs the 5 producers + GAP +'
    Write-Host '       console). See docs/guides/console-fullstack-local-dev.md § "Base harness".'
    exit 1
}
Write-Ok 'Base harness detected.'

# --- build the scm gateway jar (Dockerfile expects build/libs/*.jar) ------
if (-not $NoBuild) {
    Write-Phase 'Build scm gateway-service jar'
    & (Join-Path $RepoRoot 'gradlew.bat') ':projects:scm-platform:apps:gateway-service:bootJar' --no-daemon -q
    if ($LASTEXITCODE -ne 0) { Write-Err 'gateway bootJar failed.'; exit 1 }
    Write-Ok 'gateway-service.jar built.'
}

# --- bring up the overlay (scm-gateway + console-web ops env) -------------
Write-Phase 'Overlay — scm-gateway + console-web per-domain ops base URLs'
$buildArg = if ($NoBuild) { @() } else { @('--build') }
docker compose -p $Proj -f $Base -f $Demo up -d @buildArg scm-gateway console-web | Out-Host

# scm-gateway has a JWKS startup probe (fail-fast). If the dependency chain was
# disturbed by the recreate, the probe can miss its window — restart once.
Write-Host '       waiting for scm-gateway health ...' -NoNewline
$deadline = (Get-Date).AddSeconds($HealthTimeoutSec); $restarted = $false
while ($true) {
    $h = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$Proj-scm-gateway-1" 2>$null
    if ($h -eq 'healthy') { Write-Host ' healthy' -ForegroundColor Green; break }
    if (($h -eq 'exited' -or $h -eq 'unhealthy') -and -not $restarted) {
        Write-Host ' (probe missed window — restarting once)' -ForegroundColor Yellow
        docker start "$Proj-scm-gateway-1" *> $null; $restarted = $true; $deadline = (Get-Date).AddSeconds($HealthTimeoutSec)
    }
    if ((Get-Date) -gt $deadline) { Write-Host ''; Write-Err "scm-gateway not healthy. Check: docker logs $Proj-scm-gateway-1"; exit 1 }
    Start-Sleep 4; Write-Host '.' -NoNewline
}

# --- seed the globex-corp per-domain delta (SCM PO + ERP masters) ---------
if (-not $NoSeed) {
    Write-Phase 'Seed — globex-corp delta (SCM purchase-orders + ERP masters)'
    Get-Content (Join-Path $SeedDir '03-erp.sql') -Raw | docker exec -i "$Proj-mysql-1" mysql -uroot -prootpass erp_db 2>$null
    Get-Content (Join-Path $SeedDir '06-scm-procurement.sql') -Raw | docker exec -i "$Proj-scm-postgres-1" psql -U scm -d scm_procurement -v ON_ERROR_STOP=0 *> $null
    Write-Ok 'globex SCM-PO + ERP seeds applied (idempotent).'
}

Write-Phase 'Ready'
Write-Ok 'Open http://localhost:3000'
Write-Host '       Login:   multi-operator@example.com  /  devpassword123!'
Write-Host '       Demo:    active tenant acme-corp   -> Finance 운영 + WMS 운영 live'
Write-Host '                switch to  globex-corp     -> SCM 운영 + ERP 운영 live'
Write-Host '       GAP 운영 (계정/감사/운영자) always available. (Do NOT log in as'
Write-Host '       super-admin / acme-operator for the scm/erp screens — not entitled.)'
Write-Host '       Walkthrough: docs/guides/console-fullstack-local-dev.md'
