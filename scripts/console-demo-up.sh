#!/usr/bin/env bash
# =============================================================================
# console-demo-up.sh — TASK-MONO-170
# One-command full 5-domain platform-console local-dev DEMO bring-up (POSIX).
# POSIX peer of console-demo-up.ps1 (the Windows-primary script). Same ordering:
# Traefik -> GAP (e2e profile) -> seed GAP -> wms/scm/finance/erp -> seed
# read-models -> console. Health-gates between phases. Idempotent.
#
# Prereqs: Docker running; *.local hosts entries (./scripts/dev-setup.sh);
#          traefik-net (this script runs `pnpm traefik:up`).
# Usage:   pnpm console-demo:up   (or)   ./scripts/console-demo-up.sh
# Flags:   NO_BUILD=1  NO_SEED=1  HEALTH_TIMEOUT=240  (env vars)
#
# ⚠ HOST RISK: ~25 containers incl. 5+ JVMs. See
#   docs/guides/console-fullstack-local-dev.md for the subset-bring-up fallback.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SEED_DIR="$SCRIPT_DIR/console-demo/seed"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"

build_arg=( --build ); [ "${NO_BUILD:-0}" = "1" ] && build_arg=()

phase() { printf '\n=== %s ===\n' "$1"; }
ok()    { printf '[OK]   %s\n' "$1"; }
warn()  { printf '[WARN] %s\n' "$1"; }
err()   { printf '[ERR]  %s\n' "$1" >&2; }

dc() { docker compose --project-directory "$REPO_ROOT/$1" "${@:2}"; }

preflight() {
  phase 'Preflight'
  docker info >/dev/null 2>&1 || { err 'Docker is not running.'; exit 1; }
  ok 'Docker reachable.'
}

wait_healthy() {
  local timeout="$1"; shift
  for c in "$@"; do
    local deadline=$(( $(date +%s) + timeout ))
    printf '       waiting for %s ...' "$c"
    while true; do
      local state
      state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$c" 2>/dev/null || echo missing)"
      if [ "$state" = healthy ] || [ "$state" = running ]; then printf ' %s\n' "$state"; break; fi
      if [ "$(date +%s)" -gt "$deadline" ]; then printf '\n'; err "$c not healthy within ${timeout}s (state=$state). Check: docker logs $c"; exit 1; fi
      sleep 3; printf '.'
    done
  done
}

# args: container kind(mysql|psql) db user password seedfile
seed() {
  local container="$1" kind="$2" db="$3" user="$4" pw="$5" file="$6"
  local path="$SEED_DIR/$file"
  [ -f "$path" ] || { err "seed file not found: $path"; exit 1; }
  printf '       seeding %s/%s <- %s\n' "$container" "$db" "$file"
  if [ "$kind" = mysql ]; then
    docker exec -i "$container" mysql -u root "-p${pw}" "$db" < "$path" || warn "$file apply returned non-zero (often benign on re-run)."
  else
    docker exec -i -e "PGPASSWORD=$pw" "$container" psql -U "$user" -d "$db" -v ON_ERROR_STOP=0 < "$path" || warn "$file apply returned non-zero (often benign on re-run)."
  fi
}

preflight

phase 'Traefik'
pnpm --dir "$REPO_ROOT" traefik:up

phase 'GAP (SPRING_PROFILES_ACTIVE=e2e — acme-corp + globex-corp demo customers)'
SPRING_PROFILES_ACTIVE=e2e dc projects/global-account-platform up -d "${build_arg[@]}"
wait_healthy "$HEALTH_TIMEOUT" gap-mysql

if [ "${NO_SEED:-0}" != "1" ]; then
  phase 'Seed — GAP operators + multi-operator N:M assignments'
  seed gap-mysql mysql '' root rootpass 01-gap.sql
fi

phase 'Domains — wms / scm / finance / erp'
dc projects/wms-platform     up -d "${build_arg[@]}"
dc projects/scm-platform     up -d "${build_arg[@]}"
dc projects/finance-platform up -d "${build_arg[@]}"
dc projects/erp-platform     up -d "${build_arg[@]}"
wait_healthy "$HEALTH_TIMEOUT" wms-postgres scm-platform-postgres finance-platform-mysql erp-platform-mysql

if [ "${NO_SEED:-0}" != "1" ]; then
  phase 'Seed — per-domain read-models'
  seed finance-platform-mysql mysql finance_db root root 02-finance.sql
  seed erp-platform-mysql     mysql erp_db     root root 03-erp.sql
  seed wms-postgres           psql  master_db  postgres postgres 04-wms-master.sql
  seed wms-postgres           psql  admin_db   postgres postgres 05-wms-admin.sql
  seed scm-platform-postgres  psql  scm_procurement          scm scm 06-scm-procurement.sql
  seed scm-platform-postgres  psql  scm_inventory_visibility scm scm 07-scm-inventory.sql
fi

phase 'Console (console-bff + console-web)'
dc projects/platform-console up -d "${build_arg[@]}"
wait_healthy "$HEALTH_TIMEOUT" platform-console-web

phase 'Ready'
ok 'Open http://console.local'
echo '       Login:   multi-operator@example.com  /  devpassword123!'
echo '       Demo:    acme-corp -> Finance/WMS 운영 ; switch globex-corp -> SCM/ERP 운영 ; GAP always.'
echo '       Walkthrough: docs/guides/console-fullstack-local-dev.md'
