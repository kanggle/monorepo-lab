#!/usr/bin/env bash
# =============================================================================
# Start local development infrastructure
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "Starting local infrastructure..."

# Copy .env.example to .env if .env does not exist
if [ ! -f .env ]; then
  echo "No .env found — copying .env.example as default"
  cp .env.example .env
fi

docker compose up -d

echo ""
echo "Waiting for services to become healthy..."
docker compose ps

echo ""
echo "Infrastructure is starting. Check status with: docker compose ps"
echo ""
echo "Service endpoints:"
echo "  MySQL:    localhost:${MYSQL_PORT:-3306}"
echo "  Redis:    localhost:${REDIS_PORT:-6379}"
echo "  Kafka:    localhost:${KAFKA_EXTERNAL_PORT:-9093}"
echo "  Kafka UI: http://localhost:${KAFKA_UI_PORT:-8090}"
