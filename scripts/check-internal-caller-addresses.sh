#!/usr/bin/env bash
#
# check-internal-caller-addresses.sh — TASK-MONO-717 (소유자 결정 ⓐ) AC-2
#
# 「설정이 없으면 조용히 localhost 로 떨어진다」를 문다.
#
# 무엇을 재는가
# -----------------------------------------------------------------------------
# GAP-internal 호출자가 읽는 주소 설정(`IAM_TOKEN_URI` · `ACCOUNT_SERVICE_BASE_URL`)이
# **그 서비스의 compose 환경에 실제로 실려 있는가.** 모집단은 `git ls-files` 가 준다.
#
# 🔴 **왜 로그나 날짜로 재지 않는가.** 2026-09-22 데모 창의 증상은 로그 두 줄이었고
#    (`Connection refused`, `seller left PENDING_PROVISIONING`), 그 둘은 **fail-soft 라
#    아무 화면도 빨개지지 않았다.** 문구로 무는 술어는 문구가 바뀌면 조용히 죽는다
#    (티켓 Failure Scenario 2). 그래서 무는 대상은 **설정의 부재**다.
#
# 🔴🔴 **이것은 fail-soft 를 fail-closed 로 바꾸는 것이 아니다.**
#    무는 것은 «값이 선언돼 있지 않다» 이지 «account-service 가 지금 안 뜬다» 가 아니다.
#    둘을 같은 술어로 묶으면 IAM 일시 장애에 셀러 등록이 죽는다 — ADR-MONO-042 D3 가
#    일부러 피한 그 상태다. 이 가드는 **저장소를** 보지 런타임을 보지 않는다.
#
# 무엇을 안 재는가 (선언된 공백)
# -----------------------------------------------------------------------------
# · 그 주소가 **맞는지**. 이 가드는 「선언됐는가」만 안다. 맞는지는 창이 답한다
#   (티켓 AC-1: `account_db` 에 행이 생기는가 — 로그 침묵은 판정이 아니다).
# · 게이트웨이가 그 경로를 라우트하는지. `POST /internal/accounts/{a}/lock` 은
#   지금도 라우트가 없고(TASK-MONO-713 ⓑ), 그것은 별건이다.
#
set -euo pipefail

SELF_TEST=0
[ "${1:-}" = "--self-test" ] && SELF_TEST=1

# (compose 파일, 서비스 이름) — 그 서비스가 GAP-internal 호출자다.
# 🔴 목록을 넓힐 때는 **그 서비스가 실제로 /internal/** 을 부르는지** 확인하고 넣어라.
#    부르지도 않는 서비스에 설정을 요구하면 이 가드는 소음이 되고, 소음은 꺼진다.
CALLERS=(
  "projects/ecommerce-microservices-platform/docker-compose.yml|product-service|IAM_TOKEN_URI ACCOUNT_SERVICE_BASE_URL"
)

judge() {
  local root="$1" fail=0 checked=0
  for row in "${CALLERS[@]}"; do
    local file="${row%%|*}" rest="${row#*|}"
    local svc="${rest%%|*}" keys="${rest#*|}"
    local path="$root/$file"
    if [ ! -f "$path" ]; then
      echo "MISSING: $file (호출자 목록이 낡았습니다 — 파일이 옮겨졌거나 지워졌습니다)"
      fail=1; continue
    fi
    # 그 서비스의 블록만 잘라낸다: `  <svc>:` 부터 다음 같은 들여쓰기의 서비스 키 앞까지.
    local block
    block="$(awk -v s="  $svc:" '
      $0 == s {inb=1; next}
      inb && /^  [a-zA-Z0-9_.-]+:[[:space:]]*$/ {exit}
      inb {print}
    ' "$path")"
    if [ -z "$block" ]; then
      echo "MISSING: $file 에 서비스 '$svc' 블록이 없습니다"
      fail=1; continue
    fi
    for k in $keys; do
      checked=$((checked + 1))
      if ! printf '%s' "$block" | grep -q "$k"; then
        echo "DRIFT: $file § $svc 에 '$k' 가 없습니다."
        echo "       ⇒ 그 서비스는 application.yml 의 기본값(localhost)으로 떨어지고,"
        echo "          그 호출은 fail-soft 라 **아무 화면도 빨개지지 않습니다**."
        echo "          (TASK-MONO-717 — 2026-09-22 데모 창에서 실측된 상태)"
        fail=1
      fi
    done
  done
  # 🔴 비공허성: 아무것도 안 봤으면 통과가 아니다.
  if [ "$checked" -eq 0 ]; then
    echo "✗ 검사한 키가 0개입니다 — 호출자 목록이 비었거나 파싱이 깨졌습니다."
    return 2
  fi
  echo "checked=$checked"
  return "$fail"
}

if [ "$SELF_TEST" = "1" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  d="$tmp/projects/ecommerce-microservices-platform"
  mkdir -p "$d"

  # (a) 두 키가 있다 → 통과
  cat > "$d/docker-compose.yml" <<'YML'
services:
  product-service:
    environment:
      - IAM_TOKEN_URI=${IAM_TOKEN_URI:-http://iam.local/oauth2/token}
      - ACCOUNT_SERVICE_BASE_URL=${ACCOUNT_SERVICE_BASE_URL:-http://iam.local}
  other-service:
    environment:
      - X=1
YML
  if ! judge "$tmp" >/dev/null 2>&1; then
    echo "SELF-TEST FAILED (a): 두 키가 있는 트리를 거부했습니다"; exit 1
  fi

  # (b) 한 키를 지운다 → 물어야 한다  ← 티켓 AC-2 의 bite
  cat > "$d/docker-compose.yml" <<'YML'
services:
  product-service:
    environment:
      - IAM_TOKEN_URI=${IAM_TOKEN_URI:-http://iam.local/oauth2/token}
  other-service:
    environment:
      - ACCOUNT_SERVICE_BASE_URL=x
YML
  if judge "$tmp" >/dev/null 2>&1; then
    echo "SELF-TEST FAILED (b): 설정이 빠진 트리를 통과시켰습니다"; exit 1
  fi
  # 🔵 (b) 는 **다른 서비스**에 같은 키를 둔다 — 파일 전체를 grep 하는 술어였다면
  #    통과시켰을 트리다. 블록을 잘라 보는 것이 이 칸의 요점이다.

  # (c) 서비스 블록 자체가 사라진다 → 통과가 아니라 실패
  cat > "$d/docker-compose.yml" <<'YML'
services:
  other-service:
    environment:
      - IAM_TOKEN_URI=x
      - ACCOUNT_SERVICE_BASE_URL=y
YML
  if judge "$tmp" >/dev/null 2>&1; then
    echo "SELF-TEST FAILED (c): 호출자가 사라진 트리를 통과시켰습니다"; exit 1
  fi

  echo "check-internal-caller-addresses: self-test 3칸 통과 (있다/한쪽이 없다/호출자가 없다)"
  exit 0
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
# 🔵 모집단 확인: 목록의 파일이 git 에 있는가(`git ls-files`) — 없으면 위 MISSING 이 문다.
out="$(judge "$REPO_ROOT")" || {
  printf '%s\n' "$out"
  echo
  echo "check-internal-caller-addresses: FAILED"
  echo "  → 처방: 그 서비스의 compose environment 에 값을 실으세요."
  echo "    데모 체인은 infra/demo/demo.env 가 \${DEMO_DOMAIN} 에서 파생시킵니다."
  exit 1
}
printf '%s\n' "$out" | sed 's/^/  /'
echo "check-internal-caller-addresses: OK — GAP-internal 호출자의 주소가 전부 선언돼 있습니다."
