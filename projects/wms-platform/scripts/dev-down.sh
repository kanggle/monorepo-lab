#!/usr/bin/env bash
# =============================================================================
# Stop local development infrastructure and optionally remove volumes
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

if [ "${1:-}" = "--clean" ] || [ "${1:-}" = "-v" ]; then
  echo "Stopping infrastructure and removing volumes..."
  docker compose down -v
  echo "All containers and volumes removed."
else
  echo "Stopping infrastructure (volumes preserved)..."
  docker compose down
  echo "Containers stopped. Data volumes preserved."
  echo "Use '$0 --clean' to also remove volumes."
fi
