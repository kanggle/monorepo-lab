#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-down.sh — 통합 데모 전체 종료 (TASK-MONO-336)
# =============================================================================
# 프로파일과 무관하게 떠 있을 수 있는 모든 프로젝트 스택을 역순으로 내린다.
# 기본은 traefik 네트워크/라우터도 함께 내린다(KEEP_TRAEFIK=1 이면 유지).
#
# 프로젝트 맵은 projects.sh 단일 출처 (TASK-MONO-341).
#
# 사용법:
#   bash infra/demo/demo-down.sh
#   KEEP_TRAEFIK=1 bash infra/demo/demo-down.sh   # traefik-net 유지(다른 스택과 공유 시)
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"

KEEP_TRAEFIK="${KEEP_TRAEFIK:-0}"

for p in "${DOWN_ORDER[@]}"; do
  echo "[demo] down: $p"
  docker compose -p "$p" -f "$ROOT/${COMPOSE[$p]}" down --remove-orphans || true
done

if [ "$KEEP_TRAEFIK" = "1" ]; then
  echo "[demo] traefik 유지 (KEEP_TRAEFIK=1)"
else
  echo "[demo] down: traefik"
  docker compose -p traefik -f "$ROOT/$TRAEFIK_COMPOSE" down || true
fi

echo "[demo] down complete"
