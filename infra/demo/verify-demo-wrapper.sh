#!/usr/bin/env bash
# =============================================================================
# infra/demo/verify-demo-wrapper.sh — 통합 데모 래퍼 회귀 방어 (TASK-MONO-341/344/346)
# =============================================================================
# 래퍼(demo-up.sh)의 정당성이 의존하는 불변식을 검증한다. 하나라도 무너지면
# 데모가 부팅되지 않거나 일부 도메인이 "소리없이" 사라진다.
#
#   (a) 모든 compose 조합이 렌더된다              docker compose config -q
#   (b) container_name 이 전역에서 유일하다        (docker 는 중복 container_name 거부)
#   (c) host ports 가 전역에서 충돌하지 않는다
#   (d) 커버리지 드리프트 — 디스크의 모든 projects/*/docker-compose.yml 이 맵에 있다
#   (e) **앱 서비스 ≥1** — 각 프로젝트가 build: 를 가진 서비스를 최소 1개 기여한다
#       (TASK-MONO-342: iam/wms 는 base 만 주면 DB 만 뜨고 앱이 0개였다. iam 은
#        OIDC IdP 라 그 경우 전 도메인의 토큰 검증이 무너진다.)
#   (g) **미설정 compose 변수 0건** — 렌더가 "variable is not set" 경고를 내면 FAIL
#       (TASK-MONO-346: 미설정 변수는 error 가 아니라 warning 이라 (a)가 통과시킨다.
#        ecommerce 의 bare ${VAR} 14개는 gitignored .env 에서 오므로 fresh clone
#        (데모 AMI · CI 러너)에서 전부 빈 문자열이 됐고, 그 중 9개가
#        POSTGRES_PASSWORD 라 postgres 가 초기화를 거부해 DB 9개 + 앱 12개가 죽었다.)
#   (f) --live: 서로 다른 프로젝트의 같은 서비스 키(redis)가 별도 -p 로 공존 healthy
#
# jq 는 쓰지 않는다(러너 외 환경 호환) — `docker compose config` YAML 을 grep/awk 로 판다.
#
# 사용법:
#   bash infra/demo/verify-demo-wrapper.sh          # 정적 (a)~(e),(g)
#   bash infra/demo/verify-demo-wrapper.sh --live   # + (f) 실기동 증명
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"
# shellcheck source=infra/demo/demo.env
set -a; source "$HERE/demo.env"; set +a

LIVE=0
[ "${1:-}" = "--live" ] && LIVE=1

fail() { echo "  FAIL: $*" >&2; exit 1; }
ok()   { echo "  ok: $*"; }

render() { # $1=slug ('traefik' 특수) → 렌더된 YAML 을 stdout 으로
  if [ "$1" = "traefik" ]; then
    docker compose -p verify-traefik -f "$ROOT/$TRAEFIK_COMPOSE" config 2>/dev/null
  else
    local ARGS; mapfile -t ARGS < <(compose_args "$1")
    docker compose -p "verify-$1" "${ARGS[@]}" config 2>/dev/null
  fi
}

# ---------------------------------------------------------------------------
echo "[verify] (a) compose 렌더 — traefik + ${#COMPOSE[@]} projects"
# ---------------------------------------------------------------------------
docker compose -p verify-traefik -f "$ROOT/$TRAEFIK_COMPOSE" config -q 2>/dev/null \
  || fail "traefik compose 렌더 실패: $TRAEFIK_COMPOSE"
ok "traefik"
for p in "${!COMPOSE[@]}"; do
  mapfile -t ARGS < <(compose_args "$p")
  docker compose -p "verify-$p" "${ARGS[@]}" config -q 2>/dev/null \
    || fail "$p compose 렌더 실패: ${COMPOSE[$p]}"
  ok "$p"
done

# ---------------------------------------------------------------------------
echo "[verify] (b) container_name 전역 유일성"
# ---------------------------------------------------------------------------
names_file="$(mktemp)"; ports_file="$(mktemp)"
trap 'rm -f "$names_file" "$ports_file"' EXIT
{
  render traefik
  for p in "${!COMPOSE[@]}"; do render "$p"; done
} | awk '/^[[:space:]]*container_name:[[:space:]]*/ { print $2 }' | sort > "$names_file"

dupe_names="$(uniq -d < "$names_file")"
[ -z "$dupe_names" ] || fail "중복 container_name (docker 가 거부함):"$'\n'"$dupe_names"
ok "$(wc -l < "$names_file" | tr -d ' ') 개 container_name 전부 유일"

# ---------------------------------------------------------------------------
echo "[verify] (c) host port 전역 무충돌"
# ---------------------------------------------------------------------------
{
  render traefik
  for p in "${!COMPOSE[@]}"; do render "$p"; done
} | awk '/^[[:space:]]*published:[[:space:]]*/ { gsub(/"/,"",$2); if ($2 != "") print $2 }' \
  | sort > "$ports_file"

dupe_ports="$(uniq -d < "$ports_file")"
[ -z "$dupe_ports" ] || fail "중복 host port:"$'\n'"$dupe_ports"
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
    case " $(compose_files "$p" | tr '\n' ' ') " in *" $rel "*) found=1; break;; esac
  done
  [ "$found" -eq 1 ] || missing="$missing$rel"$'\n'
done
[ -z "$missing" ] || fail "래퍼 맵(infra/demo/projects.sh)에 미등록된 프로젝트 compose:"$'\n'"$missing"\
  $'\n'"→ 데모에서 조용히 누락됩니다. COMPOSE + FULL/DOWN_ORDER 를 갱신하세요."
ok "${#COMPOSE[@]} 개 프로젝트 전부 맵에 등록됨"

for p in "${!COMPOSE[@]}"; do
  while read -r f; do
    [ -e "$ROOT/$f" ] || fail "맵의 $p 가 가리키는 파일 없음: $f"
  done < <(compose_files "$p")
done
ok "맵의 모든 경로가 실재"

# ---------------------------------------------------------------------------
echo "[verify] (e) 앱 서비스 ≥1 — 각 프로젝트가 build: 서비스를 기여하는가"
# ---------------------------------------------------------------------------
# 근거(MONO-342): iam / wms 의 base compose 는 인프라 전용이다. base 만 기동하면
# DB 만 뜨고 앱이 0개가 된다. iam 은 OIDC IdP 이므로 데모 전체가 무너진다.
# `build:` 를 가진 서비스 = 이 저장소가 소스에서 굽는 서비스 = 애플리케이션.
appless=""
for p in "${!COMPOSE[@]}"; do
  n="$(render "$p" | awk '/^[[:space:]]{4}build:[[:space:]]*$/ { c++ } END { print c+0 }')"
  if [ "$n" -lt 1 ]; then
    appless="$appless  $p → build: 서비스 0개 (${COMPOSE[$p]})"$'\n'
  else
    ok "$p — build: 서비스 ${n}개"
  fi
done
[ -z "$appless" ] || fail "앱 서비스를 하나도 기여하지 않는 프로젝트:"$'\n'"$appless"\
  $'\n'"→ 데모에서 DB 만 뜨고 앱이 안 뜹니다. 풀스택 compose(docker-compose.e2e.yml)를"\
  $'\n'"   projects.sh 의 COMPOSE[<slug>] 에 함께 등록하세요 (TASK-MONO-342)."

# ---------------------------------------------------------------------------
echo "[verify] (g) 미설정 compose 변수 0건 (demo.env 로 전부 채워지는가)"
# ---------------------------------------------------------------------------
# 근거(MONO-346): compose 는 미설정 변수를 error 가 아니라 warning 으로 처리하고
# 빈 문자열로 보간한다. 비밀번호 자리에서 이는 "조용한 기동 실패"가 된다 —
# postgres:16-alpine 은 빈 POSTGRES_PASSWORD 로 초기화를 거부한다.
# 가드 (a)는 stderr 를 버리므로 이를 볼 수 없다. 여기서 경고를 에러로 승격한다.
#
# CI 의 fresh checkout 에는 gitignored `.env` 가 없으므로, 이 가드는 CI 에서
# 권위를 갖는다(실 `.env` 를 가진 개발자 로컬은 결손을 가릴 수 있다).
unset_vars() { # $1=slug|'traefik' → 미설정 변수명을 한 줄에 하나씩
  local out
  if [ "$1" = "traefik" ]; then
    out="$(docker compose -p verify-traefik -f "$ROOT/$TRAEFIK_COMPOSE" config -q 2>&1 >/dev/null)"
  else
    local ARGS; mapfile -t ARGS < <(compose_args "$1")
    out="$(docker compose -p "verify-$1" "${ARGS[@]}" config -q 2>&1 >/dev/null)"
  fi
  # 경고 문자열은 `\"NAME\"` 처럼 백슬래시-이스케이프된 따옴표를 포함한다.
  # 벗기지 않으면 grep 이 매치하지 않아 거짓 "clean" 이 된다.
  printf '%s\n' "$out" \
    | sed 's/\\"/"/g' \
    | grep -o '"[A-Za-z_][A-Za-z0-9_]*" variable is not set' \
    | sed 's/^"//; s/" variable is not set$//' \
    | sort -u || true
}

unset_report=""
for p in traefik "${!COMPOSE[@]}"; do
  vars="$(unset_vars "$p" | tr '\n' ' ')"
  vars="${vars% }"
  if [ -n "$vars" ]; then
    unset_report="$unset_report  $p → $vars"$'\n'
  else
    ok "$p — 미설정 변수 없음"
  fi
done
[ -z "$unset_report" ] || fail "빈 문자열로 보간되는 compose 변수:"$'\n'"$unset_report"\
  $'\n'"→ compose 는 이를 경고로만 알립니다. 비밀번호 자리라면 컨테이너가 기동에"\
  $'\n'"   실패합니다(postgres 는 빈 POSTGRES_PASSWORD 를 거부)."\
  $'\n'"→ 값을 infra/demo/demo.env 에 추가하세요 (TASK-MONO-346)."

# ---------------------------------------------------------------------------
if [ "$LIVE" -eq 0 ]; then
  echo "[verify] 정적 검증 PASS (실기동 증명은 --live)"
  exit 0
fi

echo "[verify] (f) --live: 같은 서비스 키 'redis' 가 별도 -p 로 공존하는가"
# ---------------------------------------------------------------------------
# scm 과 fan 은 둘 다 compose 키 'redis'(redis:7-alpine)를 정의하지만
# container_name 은 scm-platform-redis / fan-platform-redis 로 다르다.
# 단일 include/-f 병합이면 하나만 살아남는다 → 둘 다 healthy 여야 통과.
cleanup_live() {
  local A
  mapfile -t A < <(compose_args scm)
  docker compose -p verify-live-scm "${A[@]}" down --remove-orphans >/dev/null 2>&1 || true
  mapfile -t A < <(compose_args fan)
  docker compose -p verify-live-fan "${A[@]}" down --remove-orphans >/dev/null 2>&1 || true
  rm -f "$names_file" "$ports_file"
}
trap cleanup_live EXIT

mapfile -t SCM_ARGS < <(compose_args scm)
mapfile -t FAN_ARGS < <(compose_args fan)
docker compose -p verify-live-scm "${SCM_ARGS[@]}" up -d redis >/dev/null
docker compose -p verify-live-fan "${FAN_ARGS[@]}" up -d redis >/dev/null

wait_healthy() {
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
