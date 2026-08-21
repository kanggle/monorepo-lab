#!/usr/bin/env bash
# =============================================================================
# check-fan-fresh.sh — 서빙 중인 fan 프런트가 main 판인가 (TASK-MONO-563 AC-3)
# =============================================================================
# 🔴 **판정은 URL 200 이 아니다.** 200 은 낡은 판도 낸다 — 배포가 실패해도 사이트는 마지막
# 성공 판을 계속 서빙한다. `TASK-MONO-562` 가 론처에서 그것을 겪었고, 563 은 fan 에서
# **성공한 배포가 하나도 없는데도** 아무 계기판이 그것을 말하지 않는 상태를 만났다.
#
#   bash check-fan-fresh.sh [--origin <URL>] [--ref <git-ref>] [--self-test]
#
#   종료코드  0 = 신선   1 = 낡음/다름   2 = 판정 불가
#
# 🔴 **2 를 0 으로 접지 마라.** "확인 못 했다" 를 "괜찮다" 로 번역하는 것이 이 저장소가
# 반복해서 당한 실패다.
#
# -----------------------------------------------------------------------------
# 🔵 왜 축이 **커밋 하나**인가 — 론처의 판정자를 그대로 복사하면 틀린다
# -----------------------------------------------------------------------------
# `infra/demo/aws/site` 는 정적 문서라 **서빙 바이트의 md5** 가 판정 축으로 성립한다.
# 이 앱은 다르다 — `next build` 결과가 라우트 14개 중 대부분 동적(`ƒ`)이라 같은 커밋이라도
# 응답 바이트가 요청마다 달라질 수 있다. **바이트를 재면 건강한 배포에도 "낡음" 이 나온다.**
# 그래서 여기서는 빌드가 스스로 적어 둔 `/build-info.json` 의 커밋만 본다
# (`scripts/write-build-info.mjs`).
#
# 🔴 그 대신 잃는 것을 적어 둔다: 이 판정자는 **배포 후 누가 문서를 바꿔치기한 경우를 못 본다**
# (론처 쪽 판정자는 md5 축이 있어 그것을 본다). 여기서 그 축은 성립하지 않는다.
#
# -----------------------------------------------------------------------------
# 🔴🔴 대조군 — 같은 값끼리 비교하면 언제나 통과한다
# -----------------------------------------------------------------------------
# `--self-test` 는 **같은 오리진에 기준만 바꿔** 두 결론이 갈리는지 본다:
#   기준 = 현재 ref            -> 신선(0)
#   기준 = 이 앱을 바꾼 이전 커밋 -> **다름(1)**
# 둘이 같은 답이면 판정자는 **서빙 값을 읽지 않고** 있는 것이다.
#
# 🔵 이 대조군은 **배포가 건강해도 영원히 성립한다.** 초판의 "오리진 둘이 다른 판정을 내는가"
#    는 결함의 존재에 의존했고, 둘 다 고쳐지는 순간 죽었다(562 실측). 고치면 죽는 대조군은
#    대조군이 아니다.
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_PATH='projects/fan-platform/web/fan-platform-web'
ORIGIN="https://kanggle-fan.vercel.app"
REF="origin/main"
SELFTEST=0

while [ $# -gt 0 ]; do
  case "$1" in
    --origin) ORIGIN="${2:-}"; shift 2 ;;
    --ref)    REF="${2:-}"; shift 2 ;;
    --self-test) SELFTEST=1; shift ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done

# 🔴 **stdout 이 아니라 stderr 로 낸다.** `served_commit` 의 출력은 `$(...)` 로 캡처되므로,
#    진단이 stdout 으로 나가면 **그 문자열이 SHA 자리에 섞여 들어가고 사람은 아무것도 못 본다**
#    (초판에서 정확히 그랬다 — rc 만 맞고 화면은 비었다). 이 판정자의 stdout 은 값 전용이다.
say() { echo "[fan-fresh] $*" >&2; }

# 🔴 리다이렉트를 따라가고 **최종 상태**를 본다. `curl -f` 는 3xx 에서 죽지 않으므로
#    따라가지 않으면 리다이렉트 스텁을 본문으로 착각한다(562 가 `cleanUrls` 로 당했다:
#    15바이트 308 스텁을 해싱하며 확신에 차서 "낡음" 을 보고했다).
fetch() { # <url> <outfile> -> "<http_code>|<final-url>"
  curl -sSL --max-time 20 -o "$2" -w '%{http_code}|%{url_effective}' "$1" 2>/dev/null || echo "000|"
}

served_commit() { # <origin> -> stdout: sha  |  rc 2 = 판정 불가
  local origin="$1" tmp meta http final sha
  tmp="$(mktemp)"
  meta="$(fetch "$origin/build-info.json" "$tmp")"
  http="${meta%%|*}"; final="${meta#*|}"
  if [ "$http" != "200" ]; then
    say "✖ $origin/build-info.json — 최종 HTTP $http (final=$final)"
    say "  ⇒ **판정 불가**(낡음이 아니다). build-info.json 이전 판이거나 자산이 안 올라갔다."
    rm -f "$tmp"; return 2
  fi
  sha="$(tr -d ' \n\r' < "$tmp" | sed -n 's/.*"commit":"\([0-9a-zA-Z]*\)".*/\1/p')"
  rm -f "$tmp"
  # 🔴 `unknown` 을 신선으로 읽지 않는다. 빌드가 커밋을 못 읽었다고 **적어 둔** 상태이고,
  #    그것은 "같다" 도 "다르다" 도 아니다. 없는 파일과 모른다고 적힌 파일은 다르다.
  case "$sha" in
    '' ) say "✖ build-info.json 에서 commit 을 못 뽑았습니다 ⇒ 판정 불가"; return 2 ;;
    unknown ) say "✖ build-info.json 이 commit=unknown 이라고 적었습니다 ⇒ 판정 불가"; return 2 ;;
  esac
  case "$sha" in
    *[!0-9a-f]* | "" ) say "✖ commit 이 SHA 모양이 아닙니다: '$sha' ⇒ 판정 불가"; return 2 ;;
  esac
  printf '%s\n' "$sha"
}

verdict_for() { # <served-sha> <expected-sha> <label>
  local sha="$1" exp="$2" label="$3" behind ahead
  say "   서빙 커밋 = $sha"
  say "   기대 커밋 = $exp   ($label)"
  if [ "$sha" = "$exp" ]; then
    say "   ✔ 신선 — 서빙 중인 판이 $label 입니다."
    return 0
  fi
  # 🔴 한 방향만 세면 거짓말이 된다. 서빙본이 기준보다 **앞서** 있을 수도 있고(대조군이
  #    옛 판을 기준으로 댈 때가 정확히 그 경우다), 그때 "0개 뒤처짐" 은 "같다" 처럼 읽힌다.
  behind="$(git -C "$HERE" rev-list --count "$sha..$exp" 2>/dev/null || echo '?')"
  ahead="$(git -C "$HERE" rev-list --count "$exp..$sha" 2>/dev/null || echo '?')"
  if [ "$behind" != "0" ] && [ "$behind" != "?" ]; then
    say "   ✖ 낡음 — $label 보다 커밋 $behind 개 뒤처져 있습니다."
  elif [ "$ahead" != "0" ] && [ "$ahead" != "?" ]; then
    say "   ✖ 다름 — 서빙본이 $label 보다 커밋 $ahead 개 **앞서** 있습니다."
  else
    say "   ✖ 다름 — 두 커밋의 선후를 셀 수 없습니다(fetch 가 필요할 수 있습니다)."
  fi
  return 1
}

EXP_SHA="$(git -C "$HERE" rev-parse "$REF" 2>/dev/null)"
if [ -z "$EXP_SHA" ]; then
  say "✖ ref 를 해석하지 못했습니다: $REF ⇒ 판정 불가"; exit 2
fi

say "── $ORIGIN"
SERVED="$(served_commit "$ORIGIN")" || exit 2

# 🔵 도달성은 **판정이 아니라 부수 관측**이다. build-info 가 신선하다고 말하는데 정문이
#    200 이 아니면 그건 "낡음" 과 전혀 다른 사건이고, 조용히 넘기면 안 된다.
DOC="$(mktemp)"; DOCMETA="$(fetch "$ORIGIN/" "$DOC")"; DOCHTTP="${DOCMETA%%|*}"
DOCBYTES="$(wc -c < "$DOC" | tr -d ' ')"; rm -f "$DOC"
say "   정문 / = HTTP $DOCHTTP ($DOCBYTES B)"
[ "$DOCHTTP" = "200" ] || say "   ⚠ 정문이 200 이 아닙니다 — 신선도와 별개의 사건입니다."

if [ "$SELFTEST" -eq 0 ]; then
  verdict_for "$SERVED" "$EXP_SHA" "$REF"
  exit $?
fi

# =============================================================================
# --self-test
# =============================================================================
# 🔴 `git log -- <path>` 는 **cwd 기준**이고 `git show <ref>:<path>` 는 **저장소 루트 기준**이다.
#    같은 문자열을 두 술어에 쓰면 한쪽이 조용히 0건이 된다(562 실측: 대조군이 "이전 커밋
#    없음" 으로 죽었다). 루트 앵커 `:/` 로 축을 맞춘다.
say "▶ 대조군 — 같은 오리진에 기준만 바꿔 두 결론이 갈리는지 확인합니다."
PREV_SHA="$(git -C "$HERE" log -2 --format=%H "$EXP_SHA" -- ":/$APP_PATH" 2>/dev/null | tail -1)"
if [ -z "$PREV_SHA" ] || [ "$PREV_SHA" = "$EXP_SHA" ]; then
  say "✖ 이 앱을 바꾼 이전 커밋을 못 찾았습니다 ⇒ 대조군 성립 불가(판정 불가)"; exit 2
fi
say "   대조 기준 = ${PREV_SHA:0:9} (이전 판) vs 현재 ${EXP_SHA:0:9}"

verdict_for "$SERVED" "$EXP_SHA"  "$REF (현재)";                a=$?
verdict_for "$SERVED" "$PREV_SHA" "${PREV_SHA:0:9} (이전 판)";  b=$?

say "── 대조군 결과: 현재기준=$a  이전판기준=$b"
if [ "$a" -eq 0 ] && [ "$b" -eq 1 ]; then
  say "✔ 판정자가 서빙 값을 실제로 읽고 두 기준을 구별합니다."
  exit 0
fi
say "✖ 기대는 현재기준=0(신선) · 이전판기준=1(다름) 이었습니다."
say "  둘이 같으면 판정자가 무엇을 대도 같은 답을 내는 것이고, 현재기준이 0 이 아니면"
say "  **배포가 실제로 낡은 것**이다 — 그때는 판정자가 아니라 배포를 봐라."
exit 2
