#!/usr/bin/env bash
# =============================================================================
# infra/demo/verify-demo-wrapper.sh — 통합 데모 래퍼 회귀 방어 (TASK-MONO-341)
# =============================================================================
# 래퍼(demo-up.sh)의 정당성이 의존하는 불변식을 검증한다. 하나라도 무너지면
# 데모가 부팅되지 않거나 일부 도메인이 "소리없이" 사라진다.
#
#   (a) 9개 compose 가 각각 렌더된다            docker compose config -q
#   (b) container_name 이 전역에서 유일하다      (docker 는 중복 container_name 거부)
#   (c) host ports 가 전역에서 충돌하지 않는다
#   (d) 커버리지 드리프트 — 디스크의 모든 projects/*/docker-compose.yml 이 맵에 있다
#   (e) --live: 서로 다른 프로젝트의 같은 서비스 키(redis)가 별도 -p 로 공존 healthy
#
# jq 는 쓰지 않는다(러너 외 환경 호환) — `docker compose config` YAML 을 grep/awk 로 판다.
#
# 사용법:
#   bash infra/demo/verify-demo-wrapper.sh          # 정적 (a)~(d)
#   bash infra/demo/verify-demo-wrapper.sh --live   # + (e) 실기동 증명
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"

LIVE=0
[ "${1:-}" = "--live" ] && LIVE=1

fail() { echo "  FAIL: $*" >&2; exit 1; }
ok()   { echo "  ok: $*"; }

# `${VAR}` 미설정 시 compose 는 빈 문자열 + 경고(에러 아님)지만, 스모크용 더미를 넣어
# 경고 노이즈를 줄이고 redis 기동(requirepass)도 결정론적으로 만든다.
export REDIS_PASSWORD="${REDIS_PASSWORD:-verifysmoke}"

render() { # $1=project slug (or 'traefik'), $2=compose path
  docker compose -p "verify-$1" -f "$ROOT/$2" config 2>/dev/null
}

# ---------------------------------------------------------------------------
echo "[verify] (a) compose 렌더 — traefik + ${#COMPOSE[@]} projects"
# ---------------------------------------------------------------------------
docker compose -p verify-traefik -f "$ROOT/$TRAEFIK_COMPOSE" config -q 2>/dev/null \
  || fail "traefik compose 렌더 실패: $TRAEFIK_COMPOSE"
ok "traefik"
for p in "${!COMPOSE[@]}"; do
  docker compose -p "verify-$p" -f "$ROOT/${COMPOSE[$p]}" config -q 2>/dev/null \
    || fail "$p compose 렌더 실패: ${COMPOSE[$p]}"
  ok "$p"
done

# ---------------------------------------------------------------------------
echo "[verify] (b) container_name 전역 유일성"
# ---------------------------------------------------------------------------
names_file="$(mktemp)"; trap 'rm -f "$names_file" "$ports_file"' EXIT
{
  render traefik "$TRAEFIK_COMPOSE"
  for p in "${!COMPOSE[@]}"; do render "$p" "${COMPOSE[$p]}"; done
} | awk '/^[[:space:]]*container_name:[[:space:]]*/ { print $2 }' | sort > "$names_file"

dupe_names="$(uniq -d < "$names_file")"
if [ -n "$dupe_names" ]; then
  fail "중복 container_name (docker 가 거부함):"$'\n'"$dupe_names"
fi
ok "$(wc -l < "$names_file" | tr -d ' ') 개 container_name 전부 유일"

# ---------------------------------------------------------------------------
echo "[verify] (c) host port 전역 무충돌"
# ---------------------------------------------------------------------------
ports_file="$(mktemp)"
{
  render traefik "$TRAEFIK_COMPOSE"
  for p in "${!COMPOSE[@]}"; do render "$p" "${COMPOSE[$p]}"; done
} | awk '
  /^[[:space:]]*published:[[:space:]]*/ { gsub(/"/,"",$2); if ($2 != "") print $2 }
' | sort > "$ports_file"

dupe_ports="$(uniq -d < "$ports_file")"
if [ -n "$dupe_ports" ]; then
  fail "중복 host port:"$'\n'"$dupe_ports"
fi
ok "published host ports: $(tr '\n' ' ' < "$ports_file")— 충돌 없음"

# ---------------------------------------------------------------------------
echo "[verify] (d) 커버리지 드리프트 — 모든 projects/*/docker-compose.yml 이 맵에 등록"
# ---------------------------------------------------------------------------
missing=""
for f in "$ROOT"/projects/*/docker-compose.yml; do
  [ -e "$f" ] || continue
  rel="${f#"$ROOT"/}"
  found=0
  for p in "${!COMPOSE[@]}"; do
    [ "${COMPOSE[$p]}" = "$rel" ] && { found=1; break; }
  done
  [ "$found" -eq 1 ] || missing="$missing$rel"$'\n'
done
if [ -n "$missing" ]; then
  fail "래퍼 맵(infra/demo/projects.sh)에 미등록된 프로젝트 compose:"$'\n'"$missing"\
       $'\n'"→ 데모에서 조용히 누락됩니다. COMPOSE + FULL/DOWN_ORDER 를 갱신하세요."
fi
ok "${#COMPOSE[@]} 개 프로젝트 전부 맵에 등록됨"

# 역방향: 맵에 있는데 파일이 없는 경우
for p in "${!COMPOSE[@]}"; do
  [ -e "$ROOT/${COMPOSE[$p]}" ] || fail "맵의 $p 가 가리키는 파일 없음: ${COMPOSE[$p]}"
done
ok "맵의 모든 경로가 실재"

# ---------------------------------------------------------------------------
if [ "$LIVE" -eq 0 ]; then
  echo "[verify] 정적 검증 PASS (실기동 증명은 --live)"
  exit 0
fi

echo "[verify] (e) --live: 같은 서비스 키 'redis' 가 별도 -p 로 공존하는가"
# ---------------------------------------------------------------------------
# scm 과 fan 은 둘 다 compose 키 'redis'(redis:7-alpine)를 정의하지만
# container_name 은 scm-platform-redis / fan-platform-redis 로 다르다.
# 단일 include/-f 병합이면 하나만 살아남는다 → 둘 다 healthy 여야 통과.
cleanup_live() {
  docker compose -p verify-live-scm -f "$ROOT/${COMPOSE[scm]}" down --remove-orphans >/dev/null 2>&1 || true
  docker compose -p verify-live-fan -f "$ROOT/${COMPOSE[fan]}" down --remove-orphans >/dev/null 2>&1 || true
  rm -f "$names_file" "$ports_file"
}
trap cleanup_live EXIT

docker compose -p verify-live-scm -f "$ROOT/${COMPOSE[scm]}" up -d redis >/dev/null
docker compose -p verify-live-fan -f "$ROOT/${COMPOSE[fan]}" up -d redis >/dev/null

wait_healthy() { # $1=container name
  for _ in $(seq 1 30); do
    st="$(docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null || echo missing)"
    [ "$st" = "healthy" ] && return 0
    [ "$st" = "missing" ] && return 1
    sleep 2
  done
  return 1
}

wait_healthy scm-platform-redis || fail "scm-platform-redis 가 healthy 되지 않음"
ok "scm-platform-redis healthy"
wait_healthy fan-platform-redis || fail "fan-platform-redis 가 healthy 되지 않음"
ok "fan-platform-redis healthy"

running="$(docker ps --filter 'name=scm-platform-redis' --filter 'name=fan-platform-redis' -q | wc -l | tr -d ' ')"
[ "$running" = "2" ] || fail "두 redis 가 공존하지 않음 (running=$running) — 병합 회귀 의심"
ok "같은 키 'redis' 2개가 별도 -p 로 공존 (running=2)"

echo "[verify] 전체 PASS (정적 + 실기동 증명)"
