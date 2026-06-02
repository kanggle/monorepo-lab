#!/usr/bin/env bash
# =============================================================================
# console-demo-down.sh — TASK-MONO-170
# Tear down the full console demo stack (6 compose projects + Traefik).
# POSIX peer of console-demo-down.ps1. Reverse order. Volumes preserved unless
# VOLUMES=1.
#
# Usage:  pnpm console-demo:down   (or)   ./scripts/console-demo-down.sh
# Flags:  VOLUMES=1  also removes named volumes (full data reset).
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

down_args=( down ); [ "${VOLUMES:-0}" = "1" ] && down_args=( down -v )

phase() { printf '\n=== %s ===\n' "$1"; }

for p in \
  projects/platform-console \
  projects/erp-platform \
  projects/finance-platform \
  projects/scm-platform \
  projects/wms-platform \
  projects/global-account-platform; do
  phase "down: $p"
  docker compose --project-directory "$REPO_ROOT/$p" "${down_args[@]}" || true
done

phase 'down: Traefik'
pnpm --dir "$REPO_ROOT" traefik:down || true

printf '\n[OK] console demo stack stopped.\n'
[ "${VOLUMES:-0}" = "1" ] || printf '     (volumes preserved; VOLUMES=1 for a full data reset)\n'
