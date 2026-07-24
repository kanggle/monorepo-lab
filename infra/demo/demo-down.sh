#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-down.sh — 통합 데모 종료 (전체 또는 도메인 선택) (TASK-MONO-336/477)
# =============================================================================
# 인자 없이 부르면 떠 있을 수 있는 모든 프로젝트 스택을 역순으로 내리고, traefik
# 네트워크/라우터도 함께 내린다(KEEP_TRAEFIK=1 이면 유지).
#
# 도메인 인자를 주면 **그 도메인만** 내린다(부분 종료, TASK-MONO-477). 이때:
#   · **잔존 가드** — 아직 떠 있고 종료 대상이 아닌 도메인이 X 에 하드-의존하면(예:
#     다른 도메인이 살아 있는데 iam 을 내리려 하면) X 를 남긴다. 안 그러면 남은
#     도메인들의 로그인·토큰 검증이 조용히 무너진다(MONO-358).
#   · traefik 은 **유지한다** — 남은 도메인이 공유 엣지를 쓰므로. 전체 종료만 내린다.
#
# 프로젝트 맵·의존은 projects.sh 단일 출처 (TASK-MONO-341/477).
#
# 사용법:
#   bash infra/demo/demo-down.sh                  # 전체 종료 (+ traefik)
#   bash infra/demo/demo-down.sh console fan      # 부분 종료 (traefik·의존 유지)
#   KEEP_TRAEFIK=1 bash infra/demo/demo-down.sh   # 전체 종료하되 traefik-net 유지
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"
# demo.env — up 과 동일한 ${VAR} 해소가 있어야 compose 가 같은 프로젝트를 인식한다
# shellcheck source=infra/demo/demo.env
set -a; source "$HERE/demo.env"; set +a

KEEP_TRAEFIK="${KEEP_TRAEFIK:-0}"

# 도메인 -p <slug> 스택에 컨테이너가 하나라도 있으면 "떠 있음"으로 본다.
is_running() { [ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$1" 2>/dev/null)" ]; }

# 종료 대상 집합 결정 — 인자 없으면 전체, 있으면 그 도메인만(부분 종료).
declare -A DOWNSET=()
if [ "$#" -eq 0 ]; then
  for p in "${DOWN_ORDER[@]}"; do DOWNSET["$p"]=1; done
  PARTIAL=0
else
  unknown=""
  for s in "$@"; do
    if [ -n "${COMPOSE[$s]+x}" ]; then DOWNSET["$s"]=1; else unknown="$unknown $s"; fi
  done
  [ -z "$unknown" ] || { echo "demo-down.sh: 알 수 없는 도메인:$unknown (유효: ${!COMPOSE[*]})" >&2; exit 2; }
  PARTIAL=1
fi

# 부분 종료 잔존 가드: 아직 떠 있고 종료 대상이 아닌 도메인 r 이 x 에 하드-의존하면 x 를 남긴다.
if [ "$PARTIAL" = "1" ]; then
  for x in "${!DOWNSET[@]}"; do
    for r in "${!COMPOSE[@]}"; do
      [ -n "${DOWNSET[$r]+x}" ] && continue     # r 도 내려가는 중이면 무관
      is_running "$r" || continue               # r 이 안 떠 있으면 무관
      for d in ${DEPS[$r]:-}; do
        if [ "$d" = "$x" ]; then
          echo "[demo] $x 유지 — 아직 떠 있는 $r 이 하드-의존함"
          unset 'DOWNSET[$x]'
          break 2
        fi
      done
    done
  done
fi

# DOWN_ORDER 순서로 종료.
for p in "${DOWN_ORDER[@]}"; do
  [ -n "${DOWNSET[$p]+x}" ] || continue
  mapfile -t ARGS < <(compose_args "$p")
  echo "[demo] down: $p"
  docker compose -p "$p" "${ARGS[@]}" down --remove-orphans || true
done

# traefik: 부분 종료면 공유 엣지를 유지한다(남은 도메인이 쓴다). 전체 종료만 내린다.
if [ "$PARTIAL" = "1" ]; then
  echo "[demo] traefik 유지 (부분 종료 — 남은 도메인이 공유 엣지를 씀)"
elif [ "$KEEP_TRAEFIK" = "1" ]; then
  echo "[demo] traefik 유지 (KEEP_TRAEFIK=1)"
else
  echo "[demo] down: traefik"
  docker compose -p traefik -f "$ROOT/$TRAEFIK_COMPOSE" down || true
fi

echo "[demo] down complete"
