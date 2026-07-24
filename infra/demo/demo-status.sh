#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-status.sh — 도메인별 헬스 스냅샷 JSON (TASK-MONO-477)
# =============================================================================
# 각 도메인(-p <slug>) 스택의 컨테이너를 조회해 헬스를 집계하고, 도메인별
# {state, healthy, total} 을 하나의 JSON 오브젝트로 stdout 에 찍는다.
#
#   {"iam":{"state":"up","healthy":5,"total":5},
#    "wms":{"state":"down","healthy":0,"total":0}, ... ,"traefik":{...}}
#
# 왜 이게 필요한가 (컨트롤 플레인, TASK-MONO-477):
#   항상-뜬 정문 페이지가 도메인별 토글에 헬스 배지를 붙이려면 상태를 알아야 한다.
#   SSM SendCommand 는 비동기라 매 요청마다 인스턴스에 왕복하면 느리고 취약하다.
#   그래서 인스턴스가 이 스크립트를 **주기적으로** 돌려 스냅샷을 발행하고, 컨트롤
#   플레인 Lambda 는 그 스냅샷을 **읽기만** 한다. 이 파일은 그 스냅샷 생산자다.
#   (SSM 파라미터 발행은 컨트롤 플레인 증분에서 이 stdout 을 감싸 처리한다 —
#    예: `demo-status.sh | aws ssm put-parameter --name … --value file:///dev/stdin`.)
#
# jq 는 쓰지 않는다(러너 외 환경 호환 + 이 저장소 관례). docker 의 Go 템플릿으로
# State/Status 만 뽑아 shell 로 집계한다. 도커가 없거나 스택이 안 떠 있으면 전 도메인이
# state="down" 으로 나온다(에러 아님) — 가드로도 안전하게 쓸 수 있다.
#
# 사용법:
#   bash infra/demo/demo-status.sh          # 도메인별 헬스 JSON 을 stdout 으로
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"

# 도메인 하나의 헬스를 집계해 JSON 조각으로 반환: {"state":..,"healthy":N,"total":M}
#   state: down(컨테이너 0) | up(전부 healthy) | partial(일부만)
#   healthy = running 이고 (헬스체크 없음 | healthy). unhealthy / health:starting 은 제외.
domain_json() {
  local slug="$1" total=0 healthy=0 state status st
  while IFS='|' read -r state status; do
    [ -n "$state" ] || continue
    total=$(( total + 1 ))
    if [ "$state" = "running" ]; then
      case "$status" in
        *'(unhealthy)'* | *'(health: starting)'*) : ;;
        *) healthy=$(( healthy + 1 )) ;;
      esac
    fi
  done < <(docker ps -a --filter "label=com.docker.compose.project=$slug" \
             --format '{{.State}}|{{.Status}}' 2>/dev/null)

  if   [ "$total" -eq 0 ];           then st="down"
  elif [ "$healthy" -eq "$total" ];  then st="up"
  else                                    st="partial"
  fi
  printf '{"state":"%s","healthy":%d,"total":%d}' "$st" "$healthy" "$total"
}

# FULL 순서(iam 먼저 … console 마지막) + 공유 엣지 traefik 를 하나의 오브젝트로.
out="{"
first=1
for slug in "${FULL[@]}" traefik; do
  if [ "$first" = "1" ]; then first=0; else out="$out,"; fi
  out="$out\"$slug\":$(domain_json "$slug")"
done
out="$out}"
printf '%s\n' "$out"
