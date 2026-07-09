# =============================================================================
# fed-e2e-up.ps1 — TASK-MONO-339
# Bring up the federation-hardening-e2e local stack, then PROVE it is complete.
# Windows peer of fed-e2e-up.sh.
#
# Why this exists: the bring-up procedure used to live in README.md as a
# hand-written list of services to `up -d`. A hand-maintained list drifts from
# the compose file and nothing notices, because the procedure still ends in
# "success". Five services were declared but never named — victoriatraces among
# them — so seven services exported OTLP spans to a host that did not resolve
# for 36 hours and wrote 17.1GB of stack traces to their container logs.
#
# So: phase 2 no longer enumerates. It runs a bare `up -d`, which starts every
# service the compose files declare. The run then asserts that each declared
# service actually has a RUNNING container, and fails loudly otherwise.
#
# Usage (repo root):
#   .\scripts\fed-e2e-up.ps1
#   .\scripts\fed-e2e-up.ps1 -Build          # rebuild images (needs boot jars)
#   .\scripts\fed-e2e-up.ps1 -NoSeed
#   .\scripts\fed-e2e-up.ps1 -HealthTimeoutSec 600
#   .\scripts\fed-e2e-up.ps1 -AssertOnly     # only check an already-running stack
#
# Prerequisites: boot jars + console-web standalone build. See README.md.
# =============================================================================
[CmdletBinding()]
# -AssertOnly checks an already-running stack without touching it. Useful on a
# demo host whose containers were created with extra local overlays: a bare
# `up -d` from base+demo alone would see config drift and recreate them,
# dropping the overlay settings.
param([switch]$Build, [switch]$NoSeed, [switch]$AssertOnly, [int]$HealthTimeoutSec = 300)

$ErrorActionPreference = 'Stop'
$RepoRoot  = Split-Path -Parent $PSScriptRoot
$DockerDir = Join-Path $RepoRoot 'tests\federation-hardening-e2e\docker'
$Fixtures  = Join-Path $RepoRoot 'tests\federation-hardening-e2e\fixtures'
$Base      = Join-Path $DockerDir 'docker-compose.federation-e2e.yml'
$Demo      = Join-Path $DockerDir 'docker-compose.federation-e2e.demo.yml'

# The compose files carry no top-level `name:`, so without -p the project name
# would default to the directory name ("docker") and this would create a SECOND
# parallel stack. scripts\console-demo-up.ps1 preflights on this exact name.
$Proj = 'federation-hardening-e2e'

function Write-Phase($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Write-Ok($m)    { Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Err($m)   { Write-Host "[ERR]  $m" -ForegroundColor Red }

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)]$Args)
    & docker compose -p $Proj -f $Base -f $Demo @Args
}

# Build args are opt-in: `up -d` already builds images that do not exist yet,
# whereas `--build` rebuilds every service at once and has OOM'd this host.
$buildArg = if ($Build) { @('--build') } else { @() }

function Wait-Healthy {
    param([string]$Svc)
    $deadline = (Get-Date).AddSeconds($HealthTimeoutSec)
    Write-Host ('       {0,-34}' -f $Svc) -NoNewline
    while ($true) {
        $cid = (Invoke-Compose ps -q $Svc | Select-Object -First 1)
        if ($cid) {
            # A service with no healthcheck reports its plain status instead.
            $h = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $cid 2>$null
            if ($h -eq 'healthy' -or $h -eq 'running') { Write-Host " $h" -ForegroundColor Green; return $true }
            if ($h -eq 'exited' -or $h -eq 'dead') {
                Write-Host " $h" -ForegroundColor Red
                Write-Err "$Svc died — docker logs $cid"; return $false
            }
        }
        if ((Get-Date) -gt $deadline) {
            Write-Host ' TIMEOUT' -ForegroundColor Red
            Write-Err "$Svc not healthy within ${HealthTimeoutSec}s"; return $false
        }
        Start-Sleep -Seconds 4
    }
}

# --- the whole point of this script ------------------------------------------
# declared not-subset-of running => something the compose files promise is absent.
# The reverse direction is deliberately NOT checked: local overlays (gitignored)
# legitimately add containers to this project that base+demo never declare.
function Assert-Complete {
    $declared = @(Invoke-Compose config --services | Sort-Object)
    # --status running matters: `ps --services` alone also lists Exited containers,
    # which is exactly how a dead wms-admin-service reads as "present".
    $running  = @(Invoke-Compose ps --services --status running | Sort-Object)
    # SideIndicator '<=' means present in -ReferenceObject ($declared) only.
    $missing = @(Compare-Object -ReferenceObject $declared -DifferenceObject $running |
                 Where-Object { $_.SideIndicator -eq '<=' } |
                 ForEach-Object { $_.InputObject })
    if ($missing.Count -gt 0) {
        Write-Err 'declared in compose but NOT running:'
        $missing | ForEach-Object { Write-Host "         - $_" -ForegroundColor Red }
        Write-Err 'bring-up is INCOMPLETE. Do not treat this stack as the demo.'
        exit 1
    }
    Write-Ok "all $($declared.Count) declared services are running"
}

Write-Phase 'Preflight'
# Do NOT use `try { docker info *> $null } catch`: in Windows PowerShell 5.1 a
# native exe's stderr becomes ErrorRecords, which $ErrorActionPreference='Stop'
# turns into a terminating error even when docker exited 0. Gate on the exit code.
& docker version --format '{{.Server.Version}}' > $null 2>$null
if ($LASTEXITCODE -ne 0) { Write-Err 'Docker is not running.'; exit 1 }
Write-Ok "docker up — project '$Proj'"

if ($AssertOnly) {
    Write-Phase 'Completeness (assert-only — nothing started, nothing recreated)'
    Assert-Complete
    exit 0
}

# Phase 1 is the ONLY place that names services, because seed.sql has to land in
# mysql before the domain producers boot and read it. Keep this list minimal:
# anything not needed by the seed belongs to the bare `up -d` in phase 2.
Write-Phase 'Phase 1 — IAM + datastores (seed prerequisite)'
Invoke-Compose up -d @buildArg mysql redis kafka auth-service account-service admin-service | Out-Host
if (-not (Wait-Healthy 'admin-service')) { exit 1 }

if (-not $NoSeed) {
    Write-Phase 'Seed — IAM (seed.sql)'
    Get-Content (Join-Path $Fixtures 'seed.sql') -Raw | & docker compose -p $Proj -f $Base -f $Demo exec -T mysql mysql -uroot -prootpass
    Write-Ok 'seed.sql applied.'
}

# No service list here — on purpose. Adding a service to the compose file is all
# it takes for it to be part of the stack from now on.
Write-Phase 'Phase 2 — everything else declared by base + demo'
Invoke-Compose up -d @buildArg | Out-Host

Write-Phase 'Wait for health'
$allOk = $true
foreach ($svc in (Invoke-Compose config --services)) {
    if (-not (Wait-Healthy $svc)) { $allOk = $false }
}
if (-not $allOk) { Write-Err 'one or more services are unhealthy.'; exit 1 }

if (-not $NoSeed) {
    # Phase 2.5 — the domain read-model seeds (TASK-MONO-162). The wms-admin and
    # scm-inv seeds were written for services the old README never started, so
    # they were never applied either.
    Write-Phase 'Seed — domain read models (phase 2.5)'
    Get-Content (Join-Path $Fixtures 'seed-domains.sql') -Raw | & docker compose -p $Proj -f $Base -f $Demo exec -T mysql mysql -uroot -prootpass
    Get-Content (Join-Path $Fixtures 'seed-wms.sql') -Raw | & docker compose -p $Proj -f $Base -f $Demo exec -T wms-postgres psql -U master -d master_db | Out-Null
    Get-Content (Join-Path $Fixtures 'seed-scm.sql') -Raw | & docker compose -p $Proj -f $Base -f $Demo exec -T scm-postgres psql -U scm -d scm_procurement | Out-Null
    Get-Content (Join-Path $Fixtures 'seed-wms-admin.sql') -Raw | & docker compose -p $Proj -f $Base -f $Demo exec -T wms-admin-postgres psql -U admin -d admin_db | Out-Null
    Get-Content (Join-Path $Fixtures 'seed-scm-inv.sql') -Raw | & docker compose -p $Proj -f $Base -f $Demo exec -T scm-inv-postgres psql -U scm -d scm_inventory_visibility | Out-Null
    Write-Ok 'domain seeds applied.'
}

Write-Phase 'Completeness'
Assert-Complete

Write-Phase 'Ready'
Write-Ok 'Open http://localhost:3000'
Write-Host '       Per-domain ops demo overlay: .\scripts\console-demo-up.ps1'
