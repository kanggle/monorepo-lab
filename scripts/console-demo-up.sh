#!/usr/bin/env bash
# =============================================================================
# console-demo-up.sh — TASK-MONO-170
# Enable the per-domain ops DEMO on the federation-hardening-e2e stack (POSIX).
# POSIX peer of console-demo-up.ps1.
#
# The fed-e2e harness already runs all 5 domains' producers + GAP + console as
# containers. This adds — as an ADDITIVE overlay (CI base compose byte-unchanged)
# — scm-gateway + console-web per-domain ops base URLs, then seeds the
# globex-corp SCM-PO + ERP delta so the globex ops pages render non-empty.
#
# PREREQUISITE: the federation-hardening-e2e base stack must already be UP.
#   See docs/guides/console-fullstack-local-dev.md § "Base harness". This script
#   detects it and stops with guidance if absent.
#
# Usage:  pnpm console-demo:up   (or)   ./scripts/console-demo-up.sh
# Flags:  NO_BUILD=1  NO_SEED=1  HEALTH_TIMEOUT=180
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SEED_DIR="$SCRIPT_DIR/console-demo/seed"
DOCKER_DIR="$REPO_ROOT/tests/federation-hardening-e2e/docker"
BASE="$DOCKER_DIR/docker-compose.federation-e2e.yml"
DEMO="$DOCKER_DIR/docker-compose.federation-e2e.demo.yml"
PROJ='federation-hardening-e2e'
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

phase() { printf '\n=== %s ===\n' "$1"; }
ok()    { printf '[OK]   %s\n' "$1"; }
err()   { printf '[ERR]  %s\n' "$1" >&2; }

phase 'Preflight — federation-hardening-e2e base must be UP'
docker info >/dev/null 2>&1 || { err 'Docker is not running.'; exit 1; }
if [ -z "$(docker ps --filter "name=${PROJ}-auth-service-1" --filter "status=running" --format '{{.Names}}')" ]; then
  err "federation-hardening-e2e base stack is NOT running (auth-service absent)."
  echo '       Bring the base harness up first. See docs/guides/console-fullstack-local-dev.md § "Base harness".'
  exit 1
fi
ok 'Base harness detected.'

if [ "${NO_BUILD:-0}" != "1" ]; then
  phase 'Build scm gateway-service jar'
  "$REPO_ROOT/gradlew" :projects:scm-platform:apps:gateway-service:bootJar --no-daemon -q
  ok 'gateway-service.jar built.'
fi

phase 'Overlay — scm-gateway + console-web per-domain ops base URLs'
build_arg=( --build ); [ "${NO_BUILD:-0}" = "1" ] && build_arg=()
docker compose -p "$PROJ" -f "$BASE" -f "$DEMO" up -d "${build_arg[@]}" scm-gateway console-web

printf '       waiting for scm-gateway health ...'
deadline=$(( $(date +%s) + HEALTH_TIMEOUT )); restarted=0
while true; do
  h="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${PROJ}-scm-gateway-1" 2>/dev/null || echo missing)"
  if [ "$h" = healthy ]; then printf ' healthy\n'; break; fi
  if { [ "$h" = exited ] || [ "$h" = unhealthy ]; } && [ "$restarted" = 0 ]; then
    printf ' (probe missed window — restarting once)\n'; docker start "${PROJ}-scm-gateway-1" >/dev/null; restarted=1; deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
  fi
  if [ "$(date +%s)" -gt "$deadline" ]; then printf '\n'; err "scm-gateway not healthy. Check: docker logs ${PROJ}-scm-gateway-1"; exit 1; fi
  sleep 4; printf '.'
done

if [ "${NO_SEED:-0}" != "1" ]; then
  phase 'Seed — globex-corp delta (SCM purchase-orders + ERP masters)'
  docker exec -i "${PROJ}-mysql-1" mysql -uroot -prootpass erp_db < "$SEED_DIR/03-erp.sql" 2>/dev/null || true
  docker exec -i "${PROJ}-scm-postgres-1" psql -U scm -d scm_procurement -v ON_ERROR_STOP=0 < "$SEED_DIR/06-scm-procurement.sql" >/dev/null 2>&1 || true
  ok 'globex SCM-PO + ERP seeds applied (idempotent).'
fi

phase 'Ready'
ok 'Open http://localhost:3000'
echo '       Login:   multi-operator@example.com  /  devpassword123!'
echo '       Demo:    acme-corp -> Finance/WMS 운영 ; switch globex-corp -> SCM/ERP 운영 ; GAP always.'
echo '       (Do NOT use super-admin / acme-operator for scm/erp — not entitled.)'
echo '       Walkthrough: docs/guides/console-fullstack-local-dev.md'
