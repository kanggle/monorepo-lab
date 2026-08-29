#!/usr/bin/env bash
# =============================================================================
# check-fan-guard-live.sh — 배포된 팬 표면에서 **라우트 가드가 실제로 도는가** (TASK-MONO-600)
# =============================================================================
#   bash check-fan-guard-live.sh [--origin <URL>] [--self-test]
#
#   종료코드  0 = 가드가 닫는다(정상)   1 = 안 닫는다 / 설정 결핍   2 = 판정 불가
#
# 🔴 **2 를 0 으로 접지 마라.** "확인 못 했다" 를 "괜찮다" 로 번역하는 것이 이 저장소가
# 반복해서 당한 실패다. 도달 불가는 **판정이 아니다.**
#
# -----------------------------------------------------------------------------
# 🔵 왜 `check-fan-fresh.sh` 와 **다른 파일**인가 — 축이 다르고, 하나가 다른 하나를 못 덮는다
# -----------------------------------------------------------------------------
# `check-fan-fresh.sh` 가 묻는 것은 «서빙 중인 판이 `main` 인가»(신선도)다.
# 이 스크립트가 묻는 것은 «그 판에서 가드가 도는가»다.
#
# 🔴 두 축은 서로를 못 덮는다. `TASK-FAN-FE-018` 의 결함은 **신선한 판이 뚫려 있던 것**이다 —
# 신선도 축만 있었으면 그날도 초록이었다. 반대로 가드 축만 있으면, 배포가 몇 주 밀려 낡은
# 판을 서빙해도 그 판의 가드가 돌기만 하면 초록이다.
#
# 🔵 exit code 의 의미도 다르다(신선도의 1 = 「낡음」, 여기의 1 = 「안 닫힘」)。그래서 한
# 파일에 합치면 rc 가 무엇을 말하는지 흐려진다.
#
# -----------------------------------------------------------------------------
# 🔴🔴 판별자 — `/nonexistent-xyz` 를 빼지 마라
# -----------------------------------------------------------------------------
# 미들웨어는 **라우팅보다 먼저** 돈다. 그러므로 미인증 요청은 그 경로가 존재하든 말든
# `/login` 으로 **먼저** 꺾여야 한다. 존재하지 않는 경로에서 404 가 나왔다면 그것은
# "그런 페이지가 없다" 가 아니라 **"가드를 안 거쳤다"** 이다.
# `TASK-FAN-FE-018` 이 프로덕션에서 A/B 를 가른 칸이 정확히 이것이다.
#
# -----------------------------------------------------------------------------
# 🔴 `302` 를 문자 그대로 단언하지 않는다
# -----------------------------------------------------------------------------
# 실측값은 **307**(`NextResponse.redirect` 기본값 — 메서드 보존)이다. 018 의 AC 문구는
# `302` 였고, 그대로 박았으면 **고쳐진 동작에 빨간불**이 켜졌다. 그래서 판정은
# 「리다이렉트인가 + `Location` 이 `/login` 인가」 두 개이고, 코드 자체는 안 박는다.
# 이 값을 바꾸는 것: `NextResponse.redirect(url, 302)` 명시, 또는 Next 의 기본값 변경.
#
# -----------------------------------------------------------------------------
# 🔵 대조군 — 「전부 막힘」이라는 자명한 오답을 배제한다
# -----------------------------------------------------------------------------
#   음성 대조군 : `/login` 은 **200** 이어야 한다. 이게 같이 꺾이면 리다이렉트 루프이고,
#                 5xx 면 사이트가 죽은 것이다. 둘 다 「가드가 완벽하다」로 읽히면 안 된다.
#   설정 대조군 : `/api/auth/providers` 는 **200** 이어야 한다. 500 이면 auth.js 가 설정
#                 결핍 상태이고, 그것은 018 이 프로덕션에서 만난 바로 그 상태다.
#                 🔴 `TASK-FAN-FE-019` 이후로는 그 상태에서도 가드가 **닫히므로**, 가드 축만
#                 보면 초록이다 — 「닫혔다」와 「설정이 깨졌다」를 갈라 놓는 것이 이 칸이다.
#
# 🔵 이 대조군들은 **배포가 건강해도 영원히 성립한다.** 결함의 존재에 의존하는 대조군은
#    고쳐지는 순간 죽는다(`check-fan-fresh.sh` 헤더가 같은 것을 적어 뒀다).
# =============================================================================
set -uo pipefail

ORIGIN="https://fan.hubwang.com"
SELF_TEST=0

while [ $# -gt 0 ]; do
  case "$1" in
    --origin)    ORIGIN="${2:?--origin 에 URL 이 필요합니다}"; shift 2 ;;
    --self-test) SELF_TEST=1; shift ;;
    -h|--help)   sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done
ORIGIN="${ORIGIN%/}"

say() { echo "[fan-guard] $*" >&2; }

# 🔴 `--ssl-no-revoke` — 이 저장소의 호스트가 캡티브 포털 뒤에 있으면 HTTPS 가 `curl 000`
#    이 되어 **측정이 통째로 거짓**이 된다. 러너에서는 안 나지만 로컬 재현에서 난다.
# 🔴 리다이렉트를 **따라가지 않는다**. 따라가면 `/login` 의 200 을 보고 "열려 있다" 로
#    읽는다 — 이 판정자가 재야 하는 것은 **꺾였는가** 그 자체다.
probe() { # <path> -> stdout "<http_code>|<location>"
  curl -s --ssl-no-revoke --max-time 20 -o /dev/null \
       -w '%{http_code}|%{redirect_url}' "$ORIGIN$1" 2>/dev/null || echo "000|"
}

# 응답 하나를 판정으로 바꾼다. **순수 함수** — `--self-test` 가 고정 입력으로 이것만 잰다.
#   redirect-to-login / redirect-elsewhere / open / unreachable / other
#
# 🔴🔴 **오리진을 인자로 받는 이유** — 초판은 `*/login` 글롭이었고, `--self-test` 가 첫
# 실행에서 그것을 잡았다: `https://evil.example.com/login` 이 **redirect-to-login 으로
# 통과**했다. 즉 가드가 사용자를 남의 호스트로 던지고 있어도 이 감시자는 초록을 냈을
# 것이다(`NEXTAUTH_URL` 오설정의 전형적 결과이고, 018 의 Edge Cases 가 *"리다이렉트가
# `/login` 이 아니라 외부로 가면 **멈춘다**"* 로 이미 이름 붙인 사건이다).
# 🔵 라이브 실행은 이 결함을 **영원히 못 잡는다** — 실제 Location 이 올바른 호스트이기
# 때문이다. 고정 입력 대조표만이 잡을 수 있었다.
classify() { # <http_code> <location> <origin> -> stdout verdict
  local code="$1" loc="$2" origin="${3:-}"
  case "$code" in
    000) echo "unreachable"; return ;;
    301|302|303|307|308)
      case "$loc" in
        # 상대 Location — 같은 오리진이 확정적이다
        /login|/login\?*)                      echo "redirect-to-login" ;;
        # 절대 Location — **이 오리진**으로 시작할 때만 인정한다
        "$origin"/login|"$origin"/login\?*)    echo "redirect-to-login" ;;
        *)                                     echo "redirect-elsewhere" ;;
      esac
      return ;;
    2*|4*) echo "open"; return ;;     # 라우팅까지 도달했다 = 가드를 안 거쳤다
    *)     echo "other"; return ;;
  esac
}

# ---------------------------------------------------------------------------
# 🔵 --self-test — **고정 입력 대조표.**
# bite 는 술어가 *발화*하는 것만 보여 주고 *해소*는 못 보여 준다. 각 케이스에 **값만 다른
# 반대 쌍**을 붙여 분류기가 실제로 갈라내는지 본다. 라이브 사이트에 의존하지 않으므로
# 사이트가 건강해도, 죽어도, 이 대조군은 **똑같이 성립한다**.
# ---------------------------------------------------------------------------
if [ "$SELF_TEST" = "1" ]; then
  fails=0; cases=0
  check() { # <expected> <code> <loc>
    local got; got="$(classify "$2" "$3" "https://fan.hubwang.com")"
    cases=$((cases + 1))
    if [ "$got" = "$1" ]; then
      say "  ok   ($2, '${3:-}') -> $got"
    else
      say "  FAIL ($2, '${3:-}') -> $got  (기대: $1)"; fails=$((fails + 1))
    fi
  }
  say "── self-test (고정 입력 대조표)"
  check redirect-to-login  307 "https://fan.hubwang.com/login?from=%2Fartists"
  check redirect-to-login  302 "https://fan.hubwang.com/login"
  check redirect-to-login  307 "/login?from=%2Fme"                 # 상대 Location 도 인정
  # 🔴🔴 값만 다른 반대 쌍 — 초판이 여기서 걸렸다(글롭이 남의 호스트를 통과시켰다)
  check redirect-elsewhere 307 "https://evil.example.com/login"
  check redirect-elsewhere 307 "https://fan.hubwang.com.evil.test/login"
  check redirect-elsewhere 307 "https://fan.hubwang.com/somewhere-else"
  check open               404 ""                                  # 018 의 fail-open 지문
  check open               200 ""
  check unreachable        000 ""
  check other              500 ""
  if [ "$cases" -eq 0 ]; then
    say "✖ self-test 케이스가 0개다 — 「전부 통과」가 아니라 **아무것도 안 쟀다**."
    exit 2
  fi
  if [ "$fails" -ne 0 ]; then
    say "✖ self-test 실패 $fails 건 — 분류기가 축을 안 가른다. 라이브 판정은 무의미하다."
    exit 2
  fi
  # 🔴 개수를 하드코딩하지 않는다 — 표에 행을 더해 놓고 문구를 안 고치면 그 문구가 거짓이 된다.
  say "✔ self-test 통과 — 고정 입력 ${cases}개를 서로 다르게 가른다."
  exit 0
fi

# ---------------------------------------------------------------------------
say "── $ORIGIN"

rc=0
unreachable=0

# ① 판별자 + 보호 경로 — 꺾여야 한다
for path in /nonexistent-xyz /artists /me; do
  meta="$(probe "$path")"; code="${meta%%|*}"; loc="${meta#*|}"
  verdict="$(classify "$code" "$loc" "$ORIGIN")"
  case "$verdict" in
    redirect-to-login)
      say "✔ $path — $code → $loc" ;;
    unreachable)
      say "✖ $path — 도달 불가(curl 000). **판정 불가**(죽었다는 뜻이 아니다)."
      unreachable=1 ;;
    open)
      say "✖ $path — $code, 리다이렉트 없음 ⇒ **가드가 안 닫는다**(018 의 지문)."
      rc=1 ;;
    redirect-elsewhere)
      say "✖ $path — $code 인데 Location 이 이 오리진의 /login 이 아니다: $loc"
      say "   🔴 열린 리다이렉트일 수 있다 — NEXTAUTH_URL 오설정을 의심하라."
      rc=1 ;;
    *)
      say "✖ $path — $code ⇒ 예상 밖. 판정 불가."
      unreachable=1 ;;
  esac
done

# ② 🔵 음성 대조군 — /login 은 200 이어야 한다
meta="$(probe /login)"; code="${meta%%|*}"; loc="${meta#*|}"
case "$code" in
  200) say "✔ /login — 200 (음성 대조군)" ;;
  000) say "✖ /login — 도달 불가 ⇒ 판정 불가"; unreachable=1 ;;
  30*) say "✖ /login — $code → $loc ⇒ **리다이렉트 루프**. 「전부 막힘」은 고침이 아니다."; rc=1 ;;
  *)   say "✖ /login — $code ⇒ 공개 경로가 죽었다."; rc=1 ;;
esac

# ③ 🔵 설정 대조군 — auth.js 가 설정을 갖고 떴는가
meta="$(probe /api/auth/providers)"; code="${meta%%|*}"
case "$code" in
  200) say "✔ /api/auth/providers — 200 (설정 있음)" ;;
  000) say "✖ /api/auth/providers — 도달 불가 ⇒ 판정 불가"; unreachable=1 ;;
  500)
    say "✖ /api/auth/providers — **500**. auth.js 가 설정 결핍 상태로 떠 있다."
    say "   🔴 이것은 018 이 프로덕션에서 만난 그 상태다. \`TASK-FAN-FE-019\` 이후로는 그래도"
    say "   가드가 닫히므로 ①은 초록일 수 있다 — 「닫혔다」와 「설정이 깨졌다」는 다른 사건이다."
    rc=1 ;;
  *) say "✖ /api/auth/providers — $code ⇒ 예상 밖."; rc=1 ;;
esac

# ---------------------------------------------------------------------------
# 🔴 «판정 불가» 는 «괜찮다» 가 아니다. 한 칸이라도 못 쟀으면 초록을 내지 않는다.
if [ "$unreachable" = "1" ]; then
  say "⇒ **판정 불가**(exit 2) — 못 잰 칸이 있다. 이것을 0 으로 접지 마라."
  exit 2
fi
if [ "$rc" != "0" ]; then
  say "⇒ **가드가 기대대로 안 닫는다**(exit 1)."
  exit 1
fi
say "⇒ 가드가 닫는다 · 공개 경로 살아 있음 · 설정 있음 (exit 0)"
exit 0
