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
#   healthy = (running 이고 헬스체크 없음|healthy) | (exited 이고 **종료코드 0**)
#
# 🔴 종료코드를 보는 이유 — TASK-MONO-551 결함 A
# -----------------------------------------------------------------------------
# 이전 술어는 `total = docker ps -a`(종료 포함) / `healthy = running` 이었다. 그래서
# **정상적으로 끝난 일회성 작업이 영원히 실패로 계상**됐다:
#
#   ecommerce-minio-init · wms-kafka-init · iam-kafka-init  — 전부 `Exited (0)`
#   ⇒ iam 14/15 · wms 16/17 · ecommerce 33/34 = 정상인데 **영구 `partial`**
#
# 이 세 도메인은 **어떤 상태에서도 `up` 이 될 수 없었다.** 런처는 면접관에게 항상
# 노란 배지 3개를 보여줬다. `Exited (0)` 은 실패가 아니라 **성공**이다.
#
# 🔴 그러나 "exited 는 다 봐준다" 로 가면 **크래시 루프가 초록이 된다** — 이 고침이
#    만들 수 있는 최악의 결과다. 그래서 **종료코드 0 만** 센다. `Exited (1)`·`Exited (137)`
#    은 그대로 실패다.
#
# 목록을 손으로 나열하지 않는다 — 새 init 컨테이너가 생기면 그 순간 드리프트가 시작된다
# (이 저장소가 이미 두 번 데인 실패 모드). **종료코드라는 성질**로 판정한다.
#
# 🔵 이 술어가 **구분하지 못하는 것**(측정해서 적는다, 숨기지 않는다): SIGTERM 을 곱게
#    받아 0 으로 끝난 **서비스**는 여기서 healthy 로 읽힌다. 데모 자신의 종료 경로는
#    `demo-down.sh` → `docker compose down --remove-orphans` 라 컨테이너를 **지우므로**
#    (total=0 ⇒ `down`) 그 경로로는 도달할 수 없고, 손으로 `docker stop` 을 친 경우에만
#    남는다. 재시작 정책을 두 번째 축으로 쓰는 안을 검토했으나 **측정해 보고 버렸다**:
#    선언 비율이 34/51 · 1/10 · 0/8 처럼 들쭉날쭉해서 두 모집단을 가르지 못한다.
#
# 종료코드는 `docker ps` 의 `{{.Status}}` 문자열에서 읽는다(`Exited (0) 3 minutes ago`).
# `{{.State}}` 는 exited 여부만 알려 주고 종료코드 필드는 없으며, `docker inspect` 로
# 가면 컨테이너마다 프로세스를 하나씩 더 띄워야 한다.
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
    elif [ "$state" = "exited" ]; then
      # 앞자리 고정 매치다 — `Exited (10)`·`Exited (137)` 은 여기 걸리지 않는다.
      case "$status" in
        'Exited (0)'*) healthy=$(( healthy + 1 )) ;;
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
