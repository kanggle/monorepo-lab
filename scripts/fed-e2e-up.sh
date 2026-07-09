#!/usr/bin/env bash
# =============================================================================
# fed-e2e-up.sh — TASK-MONO-339
# Bring up the federation-hardening-e2e local stack, then PROVE it is complete.
# POSIX peer of fed-e2e-up.ps1.
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
#   bash scripts/fed-e2e-up.sh
#   BUILD=1 bash scripts/fed-e2e-up.sh          # rebuild images (needs boot jars)
#   NO_SEED=1 bash scripts/fed-e2e-up.sh        # skip seeds
#   HEALTH_TIMEOUT=600 bash scripts/fed-e2e-up.sh
#   ASSERT_ONLY=1 bash scripts/fed-e2e-up.sh    # only check an already-running stack
#
# Prerequisites: boot jars + console-web standalone build. See README.md.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Run from the compose directory and pass RELATIVE -f paths. Absolute POSIX paths
# get mangled into `C:\c\Users\…` when Git Bash hands them to the Windows docker
# binary; relative names sidestep the conversion entirely (and match how CI and
# the README invoke compose).
cd "$REPO_ROOT/tests/federation-hardening-e2e/docker"
BASE='docker-compose.federation-e2e.yml'
DEMO='docker-compose.federation-e2e.demo.yml'
FIXTURES='../fixtures'

# The compose files carry no top-level `name:`, so without -p the project name
# would default to the directory name ("docker") and this would create a SECOND
# parallel stack. scripts/console-demo-up.sh preflights on this exact name.
PROJ='federation-hardening-e2e'

BUILD="${BUILD:-0}"
NO_SEED="${NO_SEED:-0}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"
# Check an already-running stack without touching it. Useful on a demo host whose
# containers were created with extra local overlays: a bare `up -d` from base+demo
# alone would see config drift and recreate them, dropping the overlay settings.
ASSERT_ONLY="${ASSERT_ONLY:-0}"

phase() { printf '\n=== %s ===\n' "$1"; }
ok()    { printf '[OK]   %s\n' "$1"; }
err()   { printf '[ERR]  %s\n' "$1" >&2; }

compose() { docker compose -p "$PROJ" -f "$BASE" -f "$DEMO" "$@"; }

# Build args are opt-in: `up -d` already builds images that do not exist yet,
# whereas `--build` rebuilds every service at once and has OOM'd this host.
build_arg=(); [ "$BUILD" = "1" ] && build_arg=(--build)

wait_healthy() {
  local svc="$1" deadline=$(( $(date +%s) + HEALTH_TIMEOUT )) cid h
  printf '       %-34s' "$svc"
  while true; do
    cid="$(compose ps -q "$svc" 2>/dev/null | head -1)"
    if [ -n "$cid" ]; then
      # A service with no healthcheck reports its plain status instead.
      h="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo missing)"
      case "$h" in
        healthy|running) printf ' %s\n' "$h"; return 0 ;;
        exited|dead)     printf ' %s\n' "$h"; err "$svc died — docker logs $cid"; return 1 ;;
      esac
    fi
    if [ "$(date +%s)" -gt "$deadline" ]; then
      printf ' TIMEOUT\n'; err "$svc not healthy within ${HEALTH_TIMEOUT}s"; return 1
    fi
    sleep 4
  done
}

# --- the whole point of this script ------------------------------------------
# declared ⊄ running  =>  something the compose files promise is not there.
# The reverse direction is deliberately NOT checked: local overlays (gitignored)
# legitimately add containers to this project that base+demo never declare.
assert_complete() {
  local declared running missing
  declared="$(compose config --services | sort)"
  # --status running matters: `ps --services` alone also lists Exited containers,
  # which is exactly how a dead wms-admin-service reads as "present".
  running="$(compose ps --services --status running | sort)"
  missing="$(comm -23 <(printf '%s\n' "$declared") <(printf '%s\n' "$running"))"
  if [ -n "$missing" ]; then
    err 'declared in compose but NOT running:'
    printf '         - %s\n' $missing >&2
    err 'bring-up is INCOMPLETE. Do not treat this stack as the demo.'
    return 1
  fi
  ok "all $(printf '%s\n' "$declared" | wc -l | tr -d ' ') declared services are running"
}

phase 'Preflight'
docker info >/dev/null 2>&1 || { err 'Docker is not running.'; exit 1; }
ok "docker up — project '$PROJ'"

if [ "$ASSERT_ONLY" = "1" ]; then
  phase 'Completeness (assert-only — nothing started, nothing recreated)'
  assert_complete
  exit $?
fi

# Phase 1 is the ONLY place that names services, because seed.sql has to land in
# mysql before the domain producers boot and read it. Keep this list minimal:
# anything not needed by the seed belongs to the bare `up -d` in phase 2.
phase 'Phase 1 — IAM + datastores (seed prerequisite)'
compose up -d "${build_arg[@]}" mysql redis kafka auth-service account-service admin-service
wait_healthy admin-service

if [ "$NO_SEED" != "1" ]; then
  phase 'Seed — IAM (seed.sql)'
  compose exec -T mysql mysql -uroot -prootpass < "$FIXTURES/seed.sql"
  ok 'seed.sql applied.'
fi

# No service list here — on purpose. Adding a service to the compose file is all
# it takes for it to be part of the stack from now on.
phase 'Phase 2 — everything else declared by base + demo'
compose up -d "${build_arg[@]}"

phase 'Wait for health'
rc=0
for svc in $(compose config --services); do
  wait_healthy "$svc" || rc=1
done
[ "$rc" -eq 0 ] || { err 'one or more services are unhealthy.'; exit 1; }

if [ "$NO_SEED" != "1" ]; then
  # Phase 2.5 — the domain read-model seeds (TASK-MONO-162). The wms-admin and
  # scm-inv seeds were written for services the old README never started, so
  # they were never applied either.
  phase 'Seed — domain read models (phase 2.5)'
  compose exec -T mysql mysql -uroot -prootpass < "$FIXTURES/seed-domains.sql"
  compose exec -T wms-postgres psql -U master -d master_db < "$FIXTURES/seed-wms.sql" >/dev/null
  compose exec -T scm-postgres psql -U scm -d scm_procurement < "$FIXTURES/seed-scm.sql" >/dev/null
  compose exec -T wms-admin-postgres psql -U admin -d admin_db < "$FIXTURES/seed-wms-admin.sql" >/dev/null
  compose exec -T scm-inv-postgres psql -U scm -d scm_inventory_visibility < "$FIXTURES/seed-scm-inv.sql" >/dev/null
  ok 'domain seeds applied.'
fi

phase 'Completeness'
assert_complete

phase 'Ready'
ok 'Open http://localhost:3000'
echo '       Per-domain ops demo overlay: bash scripts/console-demo-up.sh'
