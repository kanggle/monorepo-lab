#!/usr/bin/env bash
# =============================================================================
# infra/demo/check-label-drift.sh — 라우터 라벨이 **현재** 데모 도메인을 가리키는가
# =============================================================================
# TASK-MONO-553 (C).
#
# 무엇을 재는가
# -----------------------------------------------------------------------------
# 데모 호스트는 EIP 가 없다 ⇒ **재시작마다 공인 IP 가 바뀌고**, Traefik 라우터 규칙은
# `Host(`console.<a-b-c-d>.sslip.io`)` 로 **컨테이너 라벨에 각인된다.** 따라서 재시작 뒤
# 컨테이너가 재생성되지 않으면 그 컨테이너는 **옛 주소로만** 열린다.
#
# 2026-08-17 실증(TASK-MONO-553 배경): `demo-up.sh` 가 iam 을 처리하다 죽었고, 그래서
# sslip 호스트명을 라벨에 담은 컨테이너 12개 중 **11개가 옛 IP** 였다. 갱신이 어디서
# 끊겼는지가 라벨에 그대로 남아 있었다.
#
# 왜 다른 신호로는 안 되는가 (대리지표 금지)
# -----------------------------------------------------------------------------
#   · `docker ps`          → 초록이다. 옛 컨테이너도 running + healthy 다.
#   · `demo-status.sh`     → 초록이다. 컨테이너를 셀 뿐 **어느 주소로 열리는지** 모른다.
#   · compose 종료코드     → 0 일 수 있다. 그 프로젝트는 성공했고 실패한 건 옆 프로젝트다.
#   · HTTP 200             → 초록이다. **옛 호스트명으로 물으면** 열리기 때문이다.
# 어긋난 것은 오직 라벨 안의 호스트명이었다. 그러니 라벨을 직접 읽는다.
#
# 🔴 판정은 **실행 결과**로 한다 — 소스 grep 이 아니다. 이 저장소의 스크립트·문서는
#    예시 호스트명(`13-209-2-22.sslip.io` 류)을 주석에 담으므로, 소스를 훑는 술어는
#    **자기 문서에 걸린다.** 여기서 읽는 것은 `docker inspect` 의 출력뿐이다.
#
# 🔴 모집단을 나눈다 (거짓 실패 방지)
# -----------------------------------------------------------------------------
#   · **인자로 받은 도메인**(= 이번에 기동한 것)의 드리프트 → 실패(exit 1). 우리가 갱신
#     했어야 하는데 못 했다는 뜻이다.
#   · 그 밖의 도메인의 옛 컨테이너 → 경고만. `demo-core` 로 부팅하면 재시작 정책이 되살린
#     나머지 4개 도메인이 옛 라벨로 남아 있는 것이 **정상**이다. 이 둘을 합치면 정상
#     부팅이 빨개지고, 빨개지는 가드는 곧 꺼진다.
#
# 사용법:
#   DEMO_DOMAIN=1-2-3-4.sslip.io bash infra/demo/check-label-drift.sh iam wms console
#   → exit 0: 기동 대상의 라벨이 전부 현재 도메인
#     exit 1: 기동 대상 중 옛 도메인을 든 컨테이너가 있다 (이름을 전부 출력)
#     exit 0 + 경고: 기동 대상이 아닌 컨테이너만 옛 도메인
#
# DEMO_DOMAIN 이 sslip 형태가 아니면(로컬 `local`) IP 드리프트라는 개념이 없으므로
# skip 하고 0 을 반환한다.
# =============================================================================
set -uo pipefail

DEMO_DOMAIN="${DEMO_DOMAIN:-local}"
SET_DOMAINS=("$@")

case "$DEMO_DOMAIN" in
  *-*-*-*.sslip.io) ;;
  *)
    echo "[drift] 검사 생략 — DEMO_DOMAIN=$DEMO_DOMAIN (sslip 형태가 아니므로 IP 드리프트가 없습니다)"
    exit 0
    ;;
esac

running_ids="$(docker ps -q 2>/dev/null || true)"
if [ -z "$running_ids" ]; then
  echo "[drift] 실행 중인 컨테이너가 없습니다 — 검사할 대상 없음"
  exit 0
fi

# 라벨 **값** 전체에서 sslip 호스트명을 뽑는다. 라우터 rule 만 보지 않는 이유: 같은 오타가
# `traefik.http.middlewares.*.redirectregex.replacement` 처럼 다른 라벨에도 들어가고,
# 그것들도 똑같이 옛 주소를 가리키기 때문이다. 라벨 값에 `|` 가 들어갈 수 있으므로
# awk 는 앞의 두 필드만 잘라 내고 나머지를 통째로 값으로 본다.
#
# `{{println}}` 이 아니라 템플릿 안의 실제 개행을 쓴다 — 컨테이너 하나가 라벨 N개를
# 기여하고, 각 줄이 `<이름>|<compose 프로젝트>|<라벨값>` 이어야 하기 때문이다.
drift_lines="$(
  # shellcheck disable=SC2086
  docker inspect $running_ids --format \
    '{{$n := .Name}}{{$p := index .Config.Labels "com.docker.compose.project"}}{{range $k, $v := .Config.Labels}}{{$n}}|{{$p}}|{{$v}}
{{end}}' 2>/dev/null \
  | awk -F'|' -v dom="$DEMO_DOMAIN" '
      {
        n=$1; sub(/^\//, "", n); p=$2
        v=$0; sub(/^[^|]*\|[^|]*\|/, "", v)
        # 한 라벨 값에 호스트명이 여러 개일 수 있다(Host(`a`) || Host(`b`)).
        while (match(v, /[0-9]+-[0-9]+-[0-9]+-[0-9]+\.sslip\.io/)) {
          h=substr(v, RSTART, RLENGTH)
          if (h != dom) print n "|" p "|" h
          v=substr(v, RSTART+RLENGTH)
        }
      }' \
  | sort -u
)"

if [ -z "$drift_lines" ]; then
  echo "[drift] 라벨 일치 — 실행 중인 컨테이너에 ${DEMO_DOMAIN} 아닌 sslip 호스트명이 없습니다"
  exit 0
fi

in_scope=""
out_scope=""
while IFS='|' read -r cname cproj chost; do
  [ -n "$cname" ] || continue
  if [ "${#SET_DOMAINS[@]}" -gt 0 ] && [[ " ${SET_DOMAINS[*]} traefik relay " == *" $cproj "* ]]; then
    in_scope="$in_scope  $cname (-p $cproj) → $chost"$'\n'
  else
    out_scope="$out_scope  $cname (-p $cproj) → $chost"$'\n'
  fi
done <<<"$drift_lines"

if [ -n "$out_scope" ]; then
  echo "[drift] ⚠ 이번 기동 대상이 아닌 컨테이너가 옛 호스트명을 들고 있습니다:" >&2
  printf '%s' "$out_scope" >&2
  echo "[drift]   (이전 프로파일의 잔재를 재시작 정책이 되살린 것입니다. 전부 갱신하려면 demo-up.sh full)" >&2
fi

if [ -n "$in_scope" ]; then
  echo "[drift] ✖ 라벨 드리프트 — 이번에 기동한 도메인인데 라벨이 현재 도메인(${DEMO_DOMAIN})과 다릅니다:" >&2
  printf '%s' "$in_scope" >&2
  echo "[drift]   ⇒ 이 컨테이너들은 **옛 주소로만** 열립니다. 새 주소는 Traefik 404 입니다." >&2
  echo "[drift]   ⇒ 갱신이 중간에 끊겼다는 뜻입니다(TASK-MONO-553) — 위쪽 '기동 실패' 줄을 보세요." >&2
  exit 1
fi

echo "[drift] 기동 대상 라벨 일치 — 드리프트는 기동 대상 밖에만 있습니다"
exit 0
