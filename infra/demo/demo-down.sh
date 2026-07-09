#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-down.sh — 통합 데모 전체 종료 (TASK-MONO-336)
# =============================================================================
# 프로파일과 무관하게 떠 있을 수 있는 모든 프로젝트 스택을 역순으로 내린다.
# 기본은 traefik 네트워크/라우터도 함께 내린다(KEEP_TRAEFIK=1 이면 유지).
#
# 사용법:
#   bash infra/demo/demo-down.sh
#   KEEP_TRAEFIK=1 bash infra/demo/demo-down.sh   # traefik-net 유지(다른 스택과 공유 시)
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KEEP_TRAEFIK="${KEEP_TRAEFIK:-0}"

declare -A COMPOSE=(
  [iam]="projects/iam-platform/docker-compose.yml"
  [ecommerce]="projects/ecommerce-microservices-platform/docker-compose.yml"
  [wms]="projects/wms-platform/docker-compose.yml"
  [scm]="projects/scm-platform/docker-compose.yml"
  [fan]="projects/fan-platform/docker-compose.yml"
  [finance]="projects/finance-platform/docker-compose.yml"
  [erp]="projects/erp-platform/docker-compose.yml"
  [console]="projects/platform-console/docker-compose.yml"
)

# full 기동 순서의 역순
DOWN_ORDER=(console fan ecommerce erp finance scm wms iam)

for p in "${DOWN_ORDER[@]}"; do
  echo "[demo] down: $p"
  docker compose -p "$p" -f "$ROOT/${COMPOSE[$p]}" down --remove-orphans || true
done

if [ "$KEEP_TRAEFIK" = "1" ]; then
  echo "[demo] traefik 유지 (KEEP_TRAEFIK=1)"
else
  echo "[demo] down: traefik"
  docker compose -p traefik -f "$ROOT/infra/traefik/docker-compose.yml" down || true
fi

echo "[demo] down complete"
