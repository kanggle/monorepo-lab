#!/usr/bin/env bash
# Tear down the worktree-isolated ephemeral observability stack.
#
# See: docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md § 2.3 D3
#      tasks/done/TASK-MONO-065-observability-stack-scaffolding.md

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$REPO_ROOT" ]; then
  echo "[down.sh] not inside a git worktree — nothing to tear down" >&2
  exit 0  # silent no-op outside worktree
fi

if command -v sha256sum >/dev/null 2>&1; then
  WORKTREE_HASH="$(printf '%s' "$REPO_ROOT" | sha256sum | head -c 8)"
else
  WORKTREE_HASH="$(printf '%s' "$REPO_ROOT" | shasum -a 256 | head -c 8)"
fi

PROJECT="wms-observability-${WORKTREE_HASH}"
COMPOSE_FILE="$REPO_ROOT/infra/observability/docker-compose.yml"

if ! docker info >/dev/null 2>&1; then
  echo "[down.sh] Docker daemon unreachable — assuming stack already gone" >&2
  exit 0
fi

# Idempotent — docker compose down silently succeeds when the project is absent.
docker compose -f "$COMPOSE_FILE" -p "$PROJECT" down -v --remove-orphans >/dev/null 2>&1 || true

rm -f "$REPO_ROOT/.observability/ports.env"
rmdir "$REPO_ROOT/.observability" 2>/dev/null || true

echo "[down.sh] project $PROJECT torn down (WORKTREE_HASH=$WORKTREE_HASH)"
