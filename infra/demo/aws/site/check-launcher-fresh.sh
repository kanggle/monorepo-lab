#!/usr/bin/env bash
# =============================================================================
# check-launcher-fresh.sh — 서빙 중인 론처가 main 판인가 (TASK-MONO-562 AC-3)
# =============================================================================
# 🔴 **판정은 URL 200 이 아니다.** 200 은 낡은 판도 낸다 — 배포가 실패해도 사이트는 마지막
# 성공 판을 계속 서빙하기 때문이다(`TASK-MONO-557` 이 이름 붙인 함정). 그 상태에서 우리 쪽
# 증거는 "머지됨 + main 초록" 뿐이라 **아무도 안 본다.**
#
# 판정 축은 **서빙 중인 바이트가 `origin/main` 의 그것과 같은가** 다.
#
#   bash check-launcher-fresh.sh [--origin <URL>] [--ref <git-ref>]
#
#   종료코드  0 = 신선   1 = 낡음   2 = 판정 불가
#
# 🔴 **2 를 0 으로 접지 마라.** "확인 못 했다" 를 "괜찮다" 로 번역하는 것이 이 저장소가
# 반복해서 당한 실패다. 판정 불가는 실패도 성공도 아니고, **말해져야 하는 상태**다.
#
# -----------------------------------------------------------------------------
# 두 축을 따로 본다 — 하나만 보면 못 보는 것이 있다
# -----------------------------------------------------------------------------
#   (a) 내용   서빙 중인 index.html 의 md5  vs  <ref> 의 index.html 의 md5
#              → **어느 오리진에서나 성립한다.** S3/CloudFront 사본에도 쓴다.
#   (b) 커밋   서빙 중인 build-info.json 의 commit  vs  <ref> 의 SHA
#              → Vercel 빌드만 낸다(`build.sh`). 있으면 **얼마나** 낡았는지까지 말해준다.
#
# (a) 없이 (b) 만 보면 CDN 이 내용을 바꿔치기한 경우를 못 본다. (b) 없이 (a) 만 보면
# "다르다" 는 알지만 **어느 판인지** 를 모른다.
#
# -----------------------------------------------------------------------------
# 🔴🔴 대조군 — 같은 값끼리 비교하면 언제나 통과한다
# -----------------------------------------------------------------------------
# `--self-test` 가 **같은 오리진에 기준만 바꿔** 두 결론이 갈리는지 본다:
#   기준 = 현재 ref        → 신선 (0)
#   기준 = 그 파일의 이전 판 → 낡음 (1)
#
# 🔴 초판은 "오리진 두 개(Vercel vs CloudFront)가 서로 다른 판정을 내는가" 였고, 그날은
# 성립했다 — 한쪽 배포가 멈춰 있었기 때문이다. **둘 다 고쳐지는 순간 그 대조군은 소진된다.**
# 대조군이 *결함의 존재*에 의존하고 있었던 것이고, 고치면 죽는 대조군은 대조군이 아니다.
# 지금 형태는 배포가 건강해도 영원히 성립한다.
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
ORIGIN=""
REF="origin/main"
SELFTEST=0

# -----------------------------------------------------------------------------
# 🔴🔴 기본 오리진은 **정본 표에서 파생한다** — 하드코딩하지 않는다 (TASK-MONO-602)
# -----------------------------------------------------------------------------
# 예전 기본값은 `https://kanggle-portfolio.vercel.app` 였고 **죽었다**(404 — 2026-08-29,
# 2026-09-01 재측, 같은 시각 `https://hubwang.com` 은 200 이라 네트워크 탓이 아니다).
# 🔴 그 사실이 **아무 데서도 발화하지 않았다**: 이 파일을 도는 러너가 없었고, 자가검사조차
#    같은 죽은 오리진을 써서 «판정 불가» 로 끝났다 — **자기가 무는지조차 증명 못 하는 상태**였다.
#
# 정본은 `TEMPLATE.md` § 공개 호스트명 배분의 launcher 행이고, 그것을 파싱하는 코드는
# `scripts/check-public-domains.sh` 에 **이미 있다**. 🔴 여기로 **복사하지 않는다** —
# 표가 바뀌면 한쪽만 고쳐지고, 낡은 쪽은 «틀린 답» 이 아니라 **조용한 통과**를 낸다.
#
# 🔵 파생이 실패하면 **fail-closed**: 옛 기본값으로 조용히 떨어지지 않고 rc=2(판정 불가)다.
derive_origin() {
  local h
  h="$(bash "$ROOT/scripts/check-public-domains.sh" --print-launcher-host 2>/dev/null)" || return 1
  [ -n "$h" ] || return 1
  printf 'https://%s\n' "$h"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --origin) ORIGIN="${2:-}"; shift 2 ;;
    --ref)    REF="${2:-}"; shift 2 ;;
    --self-test) SELFTEST=1; shift ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done

say() { echo "[launcher-fresh] $*"; }

# --origin 을 안 줬으면 정본 표에서 파생한다. 🔴 실패는 «판정 불가»(2)이지 «신선»(0)이 아니다.
if [ -z "$ORIGIN" ]; then
  if ! ORIGIN="$(derive_origin)"; then
    say "✖ 정본 표에서 launcher 오리진을 파생하지 못했습니다 — **판정 불가**입니다."
    say "  → TEMPLATE.md 의 PUBLIC-HOSTNAMES 표에 launcher 행이 있는지,"
    say "     scripts/check-public-domains.sh --print-launcher-host 가 도는지 보세요."
    say "  → 🔴 옛 기본값으로 떨어지지 않습니다. 그렇게 하면 이 가드가 죽은 주소를"
    say "     다시 가리키면서 초록으로 보일 것이고, 그것이 TASK-MONO-602 의 결함입니다."
    exit 2
  fi
  say "기본 오리진을 정본 표에서 파생: $ORIGIN"
fi

# 🔴 줄끝만 정규화한다. 왜 필요했는지: 2026-08-21 실측에서 S3 사본은 CRLF(윈도우 작업 트리를
# 그대로 업로드), Vercel 판은 LF(리눅스 빌드) 여서 **같은 커밋이 413 바이트 다른 두 판**으로
# 서빙됐다. `.gitattributes` 의 `eol=lf` 고정으로 원인은 없앴지만, 고정이 안 걸린 체크아웃에서도
# 이 판정자가 **거짓 '낡음'** 을 내지 않도록 여기서도 걷어낸다.
# 🔵 이 정규화는 **신호를 지우지 않는다** — CR 외의 어떤 바이트 차이도 그대로 살아남는다.
#    (고정이 제대로 걸렸다면 이 걷어내기는 no-op 여야 하고, 그건 CR 수를 세면 확인된다.)
strip_cr() { tr -d '\r'; }

# `git show` 로 읽는다 — 작업 트리가 아니라 **ref 가 말하는 것**이 기준이다. 커밋 안 한
# 로컬 수정이 기준이 되면 "내 트리와 같다" 를 "배포됐다" 로 오독한다.
expected_bytes() { git -C "$HERE" show "$REF:infra/demo/aws/site/index.html" 2>/dev/null; }

# 🔴🔴 **방문자가 여는 경로를 재라 — `/index.html` 이 아니라 `/` 다.**
# 2026-08-21 실측: `vercel.json` 의 `cleanUrls: true` 때문에 `/index.html` 은 **308 리다이렉트**이고
# 본문이 **15 바이트**다. 그런데 `curl -f` 는 3xx 에서 죽지 않으므로, 이 판정자는 그 스텁을
# 해싱하면서 **"낡음" 을 확신에 차서 보고했다** — 정작 정문(`/`)은 그 순간 main 과 바이트가
# 정확히 같았다(`6437f1fe…`, 방문자 화면 링크 3개). 판정 축이 사용자 축과 달랐던 것이다.
# ⇒ **리다이렉트를 따라가고(`-L`), 최종 상태가 200 이 아니면 '낡음' 이 아니라 '판정 불가'** 로 낸다.
fetch_doc() { # <origin> <outfile>  -> "<http_code>|<final-url>"
  curl -sSL --max-time 20 -o "$2" -w '%{http_code}|%{url_effective}' "$1/" 2>/dev/null || echo "000|"
}

verdict_for() {
  local origin="$1" exp_md5="$2" exp_sha="$3" label="${4:-$REF}"
  local sha_served md5_served meta http final tmpf
  tmpf="$(mktemp)"

  say "── $origin"
  meta="$(fetch_doc "$origin" "$tmpf")"
  http="${meta%%|*}"; final="${meta#*|}"
  if [ "$http" != "200" ]; then
    say "✖ $origin/ — 최종 HTTP $http (final=$final) ⇒ **판정 불가**(낡음이 아니다)"
    rm -f "$tmpf"; return 2
  fi
  if [ ! -s "$tmpf" ]; then
    say "✖ $origin/ — 본문이 비어 있습니다 ⇒ 판정 불가"
    rm -f "$tmpf"; return 2
  fi

  local cr bytes
  cr="$(tr -cd '\r' < "$tmpf" | wc -c | tr -d ' ')"
  bytes="$(wc -c < "$tmpf" | tr -d ' ')"
  md5_served="$(strip_cr < "$tmpf" | md5sum | cut -d' ' -f1)"
  say "   최종 URL = $final  ($bytes B)"
  rm -f "$tmpf"

  sha_served="$(curl -fsS --max-time 20 "$origin/build-info.json" 2>/dev/null \
                 | tr -d ' \n' | sed -n 's/.*"commit":"\([0-9a-f]*\)".*/\1/p')"

  local md5_claimed
  md5_claimed="$(curl -fsS --max-time 20 "$origin/build-info.json" 2>/dev/null \
                  | tr -d ' \n' | sed -n 's/.*"index_md5":"\([0-9a-f]*\)".*/\1/p')"

  say "   서빙 md5 = $md5_served   (CR $cr개 걷어낸 뒤)"
  say "   기대 md5 = $exp_md5   ($label)"
  if [ -n "$sha_served" ]; then
    say "   서빙 커밋 = $sha_served"
    say "   기대 커밋 = $exp_sha"
  else
    say "   서빙 커밋 = (build-info.json 없음 — 562 이전 배포이거나 S3 사본)"
  fi

  # 🔴 **두 축이 어긋나면 그 자체가 발견이다.** build-info.json 은 빌드가 *조립한* 파일의
  # md5 를 적고, 위의 서빙 md5 는 *실제로 나온* 바이트다. 둘이 다르면 배포 후 누가(CDN·엣지·
  # 미들웨어) 문서를 바꾼 것이고, 그건 "낡음" 과 전혀 다른 사건이다. 조용히 넘기지 마라.
  if [ -n "$md5_claimed" ] && [ "$md5_claimed" != "$md5_served" ]; then
    say "   ⚠ build-info 가 적은 index_md5=$md5_claimed 와 실제 서빙 바이트가 다릅니다"
    say "     — 배포 후 문서가 변형됐거나, 재는 경로가 서빙 경로와 다릅니다."
  fi

  if [ "$md5_served" = "$exp_md5" ]; then
    say "   ✔ 신선 — 서빙 중인 바이트가 $label 과 같습니다."
    return 0
  fi

  if [ -n "$sha_served" ] && [ "$sha_served" != "$exp_sha" ]; then
    local behind
    # 🔴 한 방향만 세면 거짓말이 된다. 서빙본이 기준보다 **앞서** 있을 수도 있고
    #    (대조군이 옛 판을 기준으로 댈 때가 정확히 그 경우다), 그때 "0개 뒤처짐" 은
    #    "같다" 처럼 읽힌다. 양방향을 세서 어느 쪽인지 말한다.
    local ahead
    behind="$(git -C "$HERE" rev-list --count "$sha_served..$exp_sha" 2>/dev/null || echo '?')"
    ahead="$(git -C "$HERE" rev-list --count "$exp_sha..$sha_served" 2>/dev/null || echo '?')"
    if [ "$behind" != "0" ]; then
      say "   ✖ 낡음 — $label 보다 커밋 $behind 개 뒤처져 있습니다."
    elif [ "$ahead" != "0" ]; then
      say "   ✖ 다름 — 서빙본이 $label 보다 커밋 $ahead 개 **앞서** 있습니다(내용도 다름)."
    else
      say "   ✖ 다름 — 커밋은 같은데 바이트가 다릅니다(배포 후 변형 의심)."
    fi
  else
    say "   ✖ 낡음 — 서빙 중인 바이트가 $label 과 다릅니다."
  fi
  return 1
}

# 🔴 기대값도 **파일로** 읽는다. `$(...)` 는 끝의 개행을 먹으므로, 한쪽만 명령치환으로
# 만들면 두 값이 **같은 문서인데도 다른 md5** 가 된다 — 실제로 그랬다(`48fc5c57…` vs 파일
# md5 `6437f1fe…`). 그러면 `build-info.json` 의 `index_md5` 와도 대조가 안 된다.
EXP_FILE="$(mktemp)"
expected_bytes > "$EXP_FILE"
if [ ! -s "$EXP_FILE" ]; then
  say "✖ $REF 에서 index.html 을 읽지 못했습니다 (fetch 가 필요할 수 있습니다) ⇒ 판정 불가"
  rm -f "$EXP_FILE"; exit 2
fi
EXP_MD5="$(strip_cr < "$EXP_FILE" | md5sum | cut -d' ' -f1)"
rm -f "$EXP_FILE"
# 🔴🔴 커밋 축의 기대값은 `<ref>` 의 **tip 이 아니다** (TASK-MONO-564)
# 이 프로젝트에도 `ignoreCommand` 가 있다 — 트리거 경로가 안 바뀐 커밋은 **의도적으로**
# 배포를 건너뛴다. tip 을 기대값으로 쓰면 문서 전용 PR 이 머지될 때마다 서빙 커밋이
# "뒤처진" 것처럼 보인다. 여기서는 md5 축이 먼저 일치해서 그 오판이 **가려져 있었을 뿐**이고
# (실측: `✔ 신선` 옆에 서빙 `d1f263aa3` ≠ 기대 `b651b115b` 가 그대로 찍혔다), 사람은 그
# 불일치를 보고 둘 중 하나를 불신하게 된다. fan 쪽에서는 같은 결함이 md5 축이 없어
# **그대로 빨간불**로 나타났다(TASK-MONO-564 의 발견 경로).
#
# 목록은 **단일 출처**에 있다 — 여기 복사하지 않는다.
#
# 🔴🔴 TASK-MONO-602: 그 «단일 출처» 가 **옮겨갔는데 이 파일만 몰랐다.**
#    예전에는 `vercel.json` 의 `ignoreCommand` 에 pathspec 이 **인라인**이었고 여기서 그것을
#    긁었다. `TASK-MONO-607` 이 그것을 `vercel-ignore.sh` 로 뽑아내면서 — 형제 둘의 모양을
#    따른 옳은 변경이다 — 이 grep 은 **0건**이 됐고, 이 판정자는 그날부터 **판정 불가**였다.
#    🔴 그 사실이 **아무 데서도 발화하지 않았다**: 이 파일을 도는 러너가 없었기 때문이다.
#    ⇒ 이 티켓의 두 결함(죽은 오리진 · 러너 없음)이 **세 번째를 낳았다**. 러너가 있었다면
#      607 의 PR 에서 즉시 빨간불이었을 것이다.
#    🔵 형제 `check-fan-fresh.sh` 는 처음부터 래퍼에서 읽고 있었다 — **답이 형제에 있었다.**
IGNORE_WRAPPER="$HERE/vercel-ignore.sh"
SPECS=()
while IFS= read -r sp; do [ -n "$sp" ] && SPECS+=("$sp"); done \
  < <(grep -o "':/[^']*'" "$IGNORE_WRAPPER" 2>/dev/null | tr -d "'")

# 🔴 추출이 죽으면 조용히 통과시키지 않는다. `git log -1 <ref> --` 는 인자가 없으면
#    **모든 경로**를 뜻해 tip 을 돌려주고, 그러면 이 수정이 **초록인 채로 무효**가 된다.
if [ "${#SPECS[@]}" -eq 0 ]; then
  say "✖ $IGNORE_WRAPPER 에서 ':/...' pathspec 을 하나도 못 뽑았습니다 ⇒ 판정 불가"
  say "  (추출이 죽은 채로 진행하면 기대값이 ref 의 tip 이 되어 결함이 되살아난다)"
  exit 2
fi
EXP_SHA="$(git -C "$HERE" log -1 --format=%H "$REF" -- "${SPECS[@]}" 2>/dev/null)"
if [ -z "$EXP_SHA" ]; then
  say "✖ $REF 에서 트리거 경로를 바꾼 커밋을 못 찾았습니다 ⇒ 판정 불가"
  exit 2
fi
say "   트리거 경로 ${#SPECS[@]}개 · 마지막으로 바꾼 커밋 = ${EXP_SHA:0:9}"

if [ "$SELFTEST" -eq 0 ]; then
  verdict_for "$ORIGIN" "$EXP_MD5" "$EXP_SHA"
  exit $?
fi

# =============================================================================
# --self-test — 판정자가 **실제로 구별하는지**
# =============================================================================
# 🔴🔴 초판은 "오리진 두 개(Vercel vs CloudFront)가 서로 다른 판정을 내는가" 였다.
# 그날은 성립했다(한쪽은 배포가 멈춰 낡았고 한쪽은 방금 apply 해서 신선했다). **그런데 둘 다
# 고쳐지는 순간 그 대조군은 소진된다** — 건강한 세상에서는 영원히 만족될 수 없는 조건이라,
# 대조군이 *결함의 존재*에 의존하고 있었던 것이다. 고치면 죽는 대조군은 대조군이 아니다.
#
# 지금은 **같은 오리진 · 같은 판정자 · 두 기준**으로 가른다. 언제나 성립한다:
#   (1) 기준 = 현재 ref      -> 신선(0) 이어야 한다
#   (2) 기준 = 그 파일의 이전 판 -> **낡음(1)** 이어야 한다
# (2)가 0 을 내면 판정자는 무엇을 대도 "같다" 고 말하는 것이다.
say "▶ 대조군 — 같은 오리진에 기준만 바꿔 두 결론이 갈리는지 확인합니다."

# 이 파일을 **실제로 바꾼** 직전 커밋을 기준으로 쓴다. `HEAD~n` 같은 상수는 그 사이에
# 무관한 커밋이 쌓이면 "내용이 같은 옛 커밋" 을 집어 (2)를 조용히 무효로 만든다.
# 🔴 `git log -- <path>` 는 **cwd 기준**이고 `git show <ref>:<path>` 는 **저장소 루트 기준**이다.
# 두 술어를 같은 문자열로 쓰면 한쪽이 조용히 0건이 된다(실측: 대조군이 "이전 커밋 없음" 으로
# 죽었다). 루트 앵커 `:/` 를 붙여 둘의 축을 맞춘다.
PREV_SHA="$(git -C "$HERE" log -2 --format=%H -- ':/infra/demo/aws/site/index.html' 2>/dev/null | tail -1)"
if [ -z "$PREV_SHA" ] || [ "$PREV_SHA" = "$EXP_SHA" ]; then
  say "✖ index.html 을 바꾼 이전 커밋을 못 찾았습니다 ⇒ 대조군 성립 불가(판정 불가)"
  exit 2
fi
PREV_FILE="$(mktemp)"
git -C "$HERE" show "$PREV_SHA:infra/demo/aws/site/index.html" > "$PREV_FILE" 2>/dev/null
PREV_MD5="$(strip_cr < "$PREV_FILE" | md5sum | cut -d' ' -f1)"
rm -f "$PREV_FILE"
if [ "$PREV_MD5" = "$EXP_MD5" ]; then
  say "✖ 이전 판의 내용이 현재와 **같습니다** — 이 기준으로는 아무것도 구별할 수 없습니다 ⇒ 판정 불가"
  exit 2
fi
say "   대조 기준 = ${PREV_SHA:0:9} (md5 $PREV_MD5) vs 현재 $EXP_MD5"

verdict_for "$ORIGIN" "$EXP_MD5"  "$EXP_SHA"  "$REF (현재)";               a=$?
verdict_for "$ORIGIN" "$PREV_MD5" "$PREV_SHA" "${PREV_SHA:0:9} (이전 판)"; b=$?

say "── 대조군 결과: 현재기준=$a  이전판기준=$b"
if [ "$a" -ne 0 ] || [ "$b" -ne 1 ]; then
  say "✖ 기대는 현재기준=0(신선) · 이전판기준=1(낡음) 이었습니다."
  say "  둘이 같으면 판정자가 무엇을 대도 같은 답을 내는 것이고, 현재기준이 0 이 아니면"
  say "  **배포가 실제로 낡은 것**이다 — 그때는 판정자가 아니라 배포를 봐라."
  exit 2
fi
say "✔ 판정자가 같은 오리진을 두 기준으로 갈랐습니다 (현재=신선 / 이전판=낡음)."
exit 0
