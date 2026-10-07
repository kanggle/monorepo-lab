#!/usr/bin/env bash
# =============================================================================
# check-no-next-font-google.sh — `next/font/google` 재도입 금지 (TASK-MONO-767)
# =============================================================================
# `next/font/google` 은 **빌드 시점에** Google Fonts 호스트(fonts.googleapis.com)
# 를 호출해 CSS 를 내려받아 정규식으로 파싱한다. 응답이 예상과 다른 모양이면
# (속도 제한 · 일시 오류 · 다른 본문) 매치가 `null` 이 되어
# `TypeError: Cannot read properties of null (reading '1')` 로 **빌드 전체**가
# 죽는다 — 코드가 같아도 재실행하면 통과해서 flake 로 오인되기 쉽다.
#
# web-store · fan-platform-web 은 TASK-MONO-767 에서 `next/font/local` + 저장소에
# 커밋한 OFL-1.1 woff2 로 바꿨다(빌드 시점 네트워크 0). 이 가드는 그 수정이
# 되돌려지거나, 세 번째 앱이 같은 패턴(next/font/google)을 새로 들이는지 지킨다.
#
#   bash scripts/check-no-next-font-google.sh [--self-test]
#
# -----------------------------------------------------------------------------
# 🔵 project-agnostic — 어떤 앱 이름도 적지 않는다
# -----------------------------------------------------------------------------
# 모집단은 `git grep` 이 트리 전체에서 스스로 찾는다(루트 scripts/ 는
# CLAUDE.md HARDSTOP-03 에 따라 project-specific 문자열을 담으면 안 된다).
#
# -----------------------------------------------------------------------------
# 🔴 제외 두 곳은 구멍이 아니라 — «서술은 재현이 아니다» 축
# -----------------------------------------------------------------------------
# `tasks/*`(모든 깊이) 와 이 스크립트 자신은 검색에서 뺀다. 티켓 본문 ·
# `tasks/INDEX.md` 의 행 · 이 스크립트의 위 설명 문단은 전부 이 문자열을
# **역사 기록/자기 설명**으로 적는 것이 일이고, 그것은 import 재도입이 아니다.
# self-test (b) 가 이 제외가 실제 import 까지 삼키지 않는지(src 쪽은 여전히
# 걸리는지) 를 따로 확인한다.
# =============================================================================
set -uo pipefail

SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
ROOT="${NFG_GUARD_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SELF_REL="scripts/$(basename "${BASH_SOURCE[0]}")"

PATTERN='next/font/google'

main() {
  echo "[no-next-font-google] 리터럴 '$PATTERN' 재도입 검사  (root=$ROOT)"

  local hits=() line
  while IFS= read -r line; do
    [ -n "$line" ] && hits+=("$line")
  done < <(
    git -C "$ROOT" grep -n -F "$PATTERN" -- \
      ':!tasks/*' ':!*/tasks/*' ":!$SELF_REL" 2>/dev/null
  )

  if [ "${#hits[@]}" -gt 0 ]; then
    echo "  x (1) '$PATTERN' 이 재도입되었습니다:"
    printf '      %s\n' "${hits[@]}"
    echo "  x     → next/font/local + 커밋된 woff2 (OFL 확인) 로 바꾸세요 (TASK-MONO-767)."
    return 1
  fi

  echo "[no-next-font-google] ok — 트리에 '$PATTERN' 없음 (tasks/ 서술 제외)"
  return 0
}

# =============================================================================
# --self-test — 합성 트리를 만들어 **무는지 / 제외가 구멍이 아닌지** 둘 다 본다
# =============================================================================
self_test() {
  local rc=0

  _mk() {
    local d; d="$(mktemp -d)"
    mkdir -p "$d/scripts" "$d/src/app" "$d/tasks/done"
    cp "$SELF" "$d/scripts/"
    printf "import type { Metadata } from 'next';\nexport const x = 1;\n" > "$d/src/app/layout.tsx"
    printf '# ticket\n\n역사 기록: 과거 결함 서술 자리\n' > "$d/tasks/done/TASK-X.md"
    git -C "$d" init -q
    git -C "$d" config user.email t@l; git -C "$d" config user.name t
    git -C "$d" add -A >/dev/null; git -C "$d" commit -qm base
    echo "$d"
  }
  _run() { NFG_GUARD_ROOT="$1" bash "$SELF" >/dev/null 2>&1; echo $?; }
  _expect() {
    local what="$1" want="$2" got="$3"
    if [ "$got" = "$want" ]; then echo "  ok: $what (rc=$got)"
    else echo "  x  $what — rc=$got 인데 $want 를 기대했습니다."; rc=1; fi
  }

  echo "[no-next-font-google] --self-test"

  local t

  t="$(_mk)"; _expect "무망가 사본은 통과" 0 "$(_run "$t")"; rm -rf "$t"

  # (a) src 파일에 import 를 심는다 -> 문다. 이 가드의 본체.
  t="$(_mk)"
  printf "import { Noto_Sans_KR } from 'next/font/google';\n" >> "$t/src/app/layout.tsx"
  if ! grep -qF "$PATTERN" "$t/src/app/layout.tsx"; then
    echo "  x (a) 주입 실패 — 이 칸은 아무것도 시험하지 않았습니다."; rc=1
  else
    git -C "$t" commit -qam mutate
    _expect "(a) next/font/google import 재도입 -> 문다" 1 "$(_run "$t")"
  fi
  rm -rf "$t"

  # (b) tasks/ 서술에 같은 문자열을 적는다 -> 안 문다 (제외 축 대조군 —
  #     제외가 구멍이 아니라 서술/재현 구분임을 확인한다).
  t="$(_mk)"
  printf "next/font/google 를 썼던 과거 결함 서술\n" >> "$t/tasks/done/TASK-X.md"
  if ! grep -qF "$PATTERN" "$t/tasks/done/TASK-X.md"; then
    echo "  x (b) 주입 실패 — 이 칸은 아무것도 시험하지 않았습니다."; rc=1
  else
    git -C "$t" commit -qam mutate
    _expect "(b) tasks/ 서술은 안 문다 (제외 대조군)" 0 "$(_run "$t")"
  fi
  rm -rf "$t"

  [ "$rc" -eq 0 ] && echo "[no-next-font-google] --self-test ok" || echo "[no-next-font-google] --self-test 실패"
  return "$rc"
}

if [ "${1:-}" = "--self-test" ]; then
  self_test; exit $?
fi
main; exit $?
