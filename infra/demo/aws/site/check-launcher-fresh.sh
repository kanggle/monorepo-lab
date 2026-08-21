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
# 이 판정자가 **실제로 구별하는지** 는 오리진 두 개로 확인한다(2026-08-21 실측):
#
#   CloudFront (terraform apply 직후)  → 신선   (exit 0)
#   Vercel     (rate limit 로 배포 정지) → 낡음   (exit 1)
#
# 같은 판정자, 같은 시각, **반대 결론**. 이것이 대조군이다 — 자기 자신과 비교해서 통과하는
# 것이 아님을 두 결과의 어긋남이 증명한다. `--self-test` 가 이 왕복을 그대로 돌린다.
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIGIN="https://kanggle-portfolio.vercel.app"
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

say() { echo "[launcher-fresh] $*"; }

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

verdict_for() {
  local origin="$1" exp_md5="$2" exp_sha="$3"
  local body sha_served md5_served code

  body="$(curl -fsS --max-time 20 "$origin/index.html" 2>/dev/null)"; code=$?
  if [ $code -ne 0 ] || [ -z "$body" ]; then
    say "✖ $origin — index.html 을 못 읽었습니다 (curl rc=$code) ⇒ 판정 불가"
    return 2
  fi
  local cr
  cr="$(printf '%s' "$body" | tr -cd '\r' | wc -c | tr -d ' ')"
  md5_served="$(printf '%s' "$body" | strip_cr | md5sum | cut -d' ' -f1)"

  sha_served="$(curl -fsS --max-time 20 "$origin/build-info.json" 2>/dev/null \
                 | tr -d ' \n' | sed -n 's/.*"commit":"\([0-9a-f]*\)".*/\1/p')"

  say "── $origin"
  say "   서빙 md5 = $md5_served   (CR $cr개 걷어낸 뒤)"
  say "   기대 md5 = $exp_md5   ($REF)"
  if [ -n "$sha_served" ]; then
    say "   서빙 커밋 = $sha_served"
    say "   기대 커밋 = $exp_sha"
  else
    say "   서빙 커밋 = (build-info.json 없음 — 562 이전 배포이거나 S3 사본)"
  fi

  if [ "$md5_served" = "$exp_md5" ]; then
    say "   ✔ 신선 — 서빙 중인 바이트가 $REF 과 같습니다."
    return 0
  fi

  if [ -n "$sha_served" ] && [ "$sha_served" != "$exp_sha" ]; then
    local behind
    behind="$(git -C "$HERE" rev-list --count "$sha_served..$REF" 2>/dev/null || echo '?')"
    say "   ✖ 낡음 — $REF 보다 커밋 $behind 개 뒤처져 있습니다."
  else
    say "   ✖ 낡음 — 서빙 중인 바이트가 $REF 과 다릅니다."
  fi
  return 1
}

exp_bytes="$(expected_bytes)"
if [ -z "$exp_bytes" ]; then
  say "✖ $REF 에서 index.html 을 읽지 못했습니다 (fetch 가 필요할 수 있습니다) ⇒ 판정 불가"
  exit 2
fi
EXP_MD5="$(printf '%s' "$exp_bytes" | strip_cr | md5sum | cut -d' ' -f1)"
EXP_SHA="$(git -C "$HERE" rev-parse "$REF" 2>/dev/null || echo unknown)"

if [ "$SELFTEST" -eq 0 ]; then
  verdict_for "$ORIGIN" "$EXP_MD5" "$EXP_SHA"
  exit $?
fi

# --- --self-test: 판정자가 실제로 구별하는지 -----------------------------------
# 🔴 두 오리진의 결론이 **같으면** 그건 판정자가 아무것도 안 재고 있다는 뜻이다.
say "▶ 대조군 왕복 — 판정자가 두 오리진을 다르게 판정하는지 확인합니다."
# 대조 오리진 = S3/CloudFront 사본. terraform 이 `site_url` 로 알고 있으므로 거기서 묻고,
# terraform 이 없는 환경(CI)에서는 `CONTROL_ORIGIN` 으로 넘긴다. 🔴 리터럴 기본값을 두지
# 않는다 — CloudFront 도메인은 재생성마다 바뀌고, 박아두면 그 순간부터 썩는다(MONO-389).
CF="${CONTROL_ORIGIN:-}"
if [ -z "$CF" ] && command -v terraform >/dev/null 2>&1; then
  CF="$(terraform -chdir="$HERE/../terraform" output -raw site_url 2>/dev/null || true)"
fi
if [ -z "$CF" ]; then
  say "✖ 대조 오리진을 모릅니다 — CONTROL_ORIGIN 을 주거나 terraform 이 필요합니다 ⇒ 판정 불가"
  exit 2
fi

verdict_for "$CF" "$EXP_MD5" "$EXP_SHA"; a=$?
verdict_for "$ORIGIN" "$EXP_MD5" "$EXP_SHA"; b=$?

say "── 대조군 결과: CloudFront=$a  Vercel=$b"
if [ "$a" -eq "$b" ]; then
  say "✖ 두 오리진이 **같은 판정**을 냈습니다 — 대조군이 성립하지 않습니다."
  say "  (둘 다 신선하면 이 대조군은 소진된 것이고, 둘 다 낡았으면 판정자를 의심하라.)"
  exit 2
fi
say "✔ 판정자가 두 오리진을 구별했습니다 (한쪽 신선 / 한쪽 낡음)."
exit 0
