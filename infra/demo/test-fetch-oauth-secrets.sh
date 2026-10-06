#!/usr/bin/env bash
# =============================================================================
# infra/demo/test-fetch-oauth-secrets.sh — fetch-oauth-secrets.sh 단위 시험
# =============================================================================
# TASK-MONO-763 AC-2. 가짜 `aws` 를 PATH 맨 앞에 두고 세 경우를 한 번에 돈다
# (실 AWS·실 네트워크 미접촉):
#
#   google     — 파라미터 있음               → export 한다
#   kakao      — 파라미터 없음(ParameterNotFound) → export 안 함, 경고 없음
#   microsoft  — 읽기 실패(AccessDeniedException) → export 안 함, 경고 **한 줄**
#   naver      — (google 과 같은 "있음" 축을 한 번 더 — 여러 제공자가 동시에
#                동작하는지를 본다. 서로 다른 가짜 값으로 겹침을 잡아낸다.)
#
# 🔴🔴 대조군(AC-2 가 요구하는 것) — 가짜 비밀 문자열이 **출력 어디에도** 없어야
#    한다. fetch_oauth_secrets 가 만든 출력(stdout+stderr)만 파일로 받아 그 파일을
#    grep 한다 — 이 시험 스크립트 자신의 단언 로그는 대상이 아니다(그건 production
#    경로가 아니다).
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FAIL=0

# ---------------------------------------------------------------------------
# 가짜 aws — PATH 맨 앞에 둔다. `ssm get-parameter --name <name> ...` 호출만 해석한다.
# ---------------------------------------------------------------------------
FAKE_BIN="$(mktemp -d)"
cat > "$FAKE_BIN/aws" <<'FAKE_AWS'
#!/usr/bin/env bash
args="$*"
case "$args" in
  *"--name /portfolio-demo/oauth/google/client-id"*)
    echo "FAKE_SECRET_google_cid_9f2a"; exit 0 ;;
  *"--name /portfolio-demo/oauth/google/client-secret"*)
    echo "FAKE_SECRET_google_sec_7b31"; exit 0 ;;
  *"--name /portfolio-demo/oauth/naver/client-id"*)
    echo "FAKE_SECRET_naver_cid_4c10"; exit 0 ;;
  *"--name /portfolio-demo/oauth/naver/client-secret"*)
    echo "FAKE_SECRET_naver_sec_8e55"; exit 0 ;;
  *"--name /portfolio-demo/oauth/kakao/"*)
    echo "An error occurred (ParameterNotFound) when calling the GetParameter operation: ParameterNotFound" >&2
    exit 254 ;;
  *"--name /portfolio-demo/oauth/microsoft/"*)
    echo "An error occurred (AccessDeniedException) when calling the GetParameter operation: User is not authorized to perform: ssm:GetParameter" >&2
    exit 255 ;;
  *)
    echo "fake aws: unexpected invocation: $args" >&2
    exit 1 ;;
esac
FAKE_AWS
chmod +x "$FAKE_BIN/aws"

cleanup() { rm -rf "$FAKE_BIN" "${OUT_FILE:-}"; }
trap cleanup EXIT

PATH="$FAKE_BIN:$PATH"
export PATH
# IMDS 를 치지 않는다 — 리전을 명시해 EC2 밖에서도(CI) 네트워크 없이 돈다.
export AWS_REGION="ap-northeast-2"

# shellcheck source=infra/demo/fetch-oauth-secrets.sh
source "$HERE/fetch-oauth-secrets.sh"

OUT_FILE="$(mktemp)"
fetch_oauth_secrets >"$OUT_FILE" 2>&1

assert_eq() { # $1=label $2=actual $3=expected
  if [ "$2" = "$3" ]; then
    echo "  ok: $1"
  else
    echo "  FAIL: $1 — 실제='$2' 기대='$3'" >&2
    FAIL=1
  fi
}

assert_unset() { # $1=label $2=varname
  local v="${!2:-__UNSET__}"
  if [ "$v" = "__UNSET__" ]; then
    echo "  ok: $1 (export 안 됨)"
  else
    echo "  FAIL: $1 — export 되어 있음(값은 여기 안 찍습니다)" >&2
    FAIL=1
  fi
}

echo "[test] 있음 — google/naver 는 export 된다"
assert_eq "OAUTH_GOOGLE_CLIENT_ID"     "${OAUTH_GOOGLE_CLIENT_ID:-}"     "FAKE_SECRET_google_cid_9f2a"
assert_eq "OAUTH_GOOGLE_CLIENT_SECRET" "${OAUTH_GOOGLE_CLIENT_SECRET:-}" "FAKE_SECRET_google_sec_7b31"
assert_eq "OAUTH_NAVER_CLIENT_ID"      "${OAUTH_NAVER_CLIENT_ID:-}"      "FAKE_SECRET_naver_cid_4c10"
assert_eq "OAUTH_NAVER_CLIENT_SECRET"  "${OAUTH_NAVER_CLIENT_SECRET:-}"  "FAKE_SECRET_naver_sec_8e55"

echo "[test] 없음(ParameterNotFound) — kakao 는 export 되지 않고, 경고도 없다"
assert_unset "OAUTH_KAKAO_CLIENT_ID"     OAUTH_KAKAO_CLIENT_ID
assert_unset "OAUTH_KAKAO_CLIENT_SECRET" OAUTH_KAKAO_CLIENT_SECRET
kakao_lines="$(grep -c 'kakao' "$OUT_FILE" || true)"
assert_eq "kakao 관련 출력 줄 수(경고 없음)" "$kakao_lines" "0"

echo "[test] 읽기 실패(AccessDenied) — microsoft 는 export 되지 않고, 경고가 한 줄씩 있다"
assert_unset "OAUTH_MICROSOFT_CLIENT_ID"     OAUTH_MICROSOFT_CLIENT_ID
assert_unset "OAUTH_MICROSOFT_CLIENT_SECRET" OAUTH_MICROSOFT_CLIENT_SECRET
ms_warn_lines="$(grep -c '읽기 실패' "$OUT_FILE" || true)"
assert_eq "microsoft 읽기 실패 경고 줄 수" "$ms_warn_lines" "2"

echo "[test] 🔴🔴 대조군 — 가짜 비밀 문자열이 출력 어디에도 없다"
for secret in FAKE_SECRET_google_cid_9f2a FAKE_SECRET_google_sec_7b31 \
              FAKE_SECRET_naver_cid_4c10 FAKE_SECRET_naver_sec_8e55; do
  n="$(grep -c "$secret" "$OUT_FILE" || true)"
  assert_eq "출력에 '$secret' 없음" "$n" "0"
done

if [ "$FAIL" -eq 0 ]; then
  echo "[test] PASS — fetch-oauth-secrets.sh 단위 시험 전부 통과"
  exit 0
else
  echo "[test] FAIL — 위 FAIL 줄 참조" >&2
  exit 1
fi
