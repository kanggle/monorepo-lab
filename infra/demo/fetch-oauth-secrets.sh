#!/usr/bin/env bash
# =============================================================================
# infra/demo/fetch-oauth-secrets.sh — SSM 에서 소셜 로그인 키(+ 데모 메일함 자격, TASK-MONO-770)를 읽어 export 한다
# =============================================================================
# TASK-MONO-763.
#
# source 해서 쓴다 — export 가 **호출자 쉘에 남아야** demo-up.sh 가 그 값을 compose 로
# 넘길 수 있다(함수를 서브셸로 실행하면 export 가 거기서 끝난다):
#
#   # shellcheck source=infra/demo/fetch-oauth-secrets.sh
#   source infra/demo/fetch-oauth-secrets.sh
#   fetch_oauth_secrets
#
# -----------------------------------------------------------------------------
# 왜 demo-boot.sh 에서 부르는가 (셸 경계를 넘는 경로)
# -----------------------------------------------------------------------------
# systemd(demo-stack.service) → demo-boot.sh → **exec** demo-up.sh → docker compose.
# 마지막 홉이 `fork` 가 아니라 **`exec`** 라는 것이 load-bearing 이다 — exec 는 현재
# 프로세스의 환경을 교체하지 않고 그대로 새 프로그램에 넘긴다. 그래서 demo-boot.sh 가
# `exec bash demo-up.sh "$@"` 를 부르기 **전에** 여기서 export 한 값은 demo-up.sh 의
# 쉘 환경에 그대로 있고, demo-up.sh 가 그 환경에서 `docker compose up -d` 를 부르면
# compose 는 (명시적으로 보간하는 `${VAR}` 자리에 한해) 그 환경변수를 읽는다 — `demo.env`
# 의 `REDIS_PASSWORD=` 가 같은 경로로 전달되는 것과 동일한 기전이다.
#
# -----------------------------------------------------------------------------
# 🔴🔴 값은 절대 찍지 않는다
# -----------------------------------------------------------------------------
# · 이 파일 어디에도 `set -x` 를 켜지 않는다.
# · 에러 메시지에 값을 넣지 않는다 — AWS CLI 의 `get-parameter` 실패 본문은 파라미터
#   **이름**과 에러 코드(`ParameterNotFound` · `AccessDeniedException` …)만 담지 값은
#   담지 않는다(실측 확인). 그래도 그 stderr 를 그대로 echo 하지 않고 grep 으로만 분류한다
#   — 메시지 형식이 바뀌어도 "그대로 흘려보내 값이 섞일" 경로를 만들지 않기 위해서다.
# · 호출자가 이미 `set -x` 를 켜 둔 채 이 파일을 source 했을 경우를 위한 방어도 둔다
#   (아래 fetch_oauth_secrets 의 xtrace 저장/복원).
#
# -----------------------------------------------------------------------------
# 세 가지 결과 — 하나는 조용하고, 하나는 한 줄 경고다
# -----------------------------------------------------------------------------
#   있음        → export 한다.
#   없음        → export 하지 않는다. **경고를 찍지 않는다** — kakao·microsoft 는
#                 지금 이 상태가 **정상**이고(SSM 에 아직 등록 안 함), 그래야 기본값
#                 `test-*` 가 남아 TASK-BE-623 이 그 제공자의 버튼을 숨긴다.
#   읽기 실패   → (권한 · 네트워크 등 "없음"이 아닌 모든 에러) export 하지 않고
#                 **한 줄 경고**를 찍은 뒤 계속한다. 이 스크립트는 부팅 경로에 있으므로
#                 실패가 부팅을 막으면 안 된다 — demo-boot.sh 는 `set -euo pipefail` 아래
#                 있고, 여기서 비-0 으로 끝나면 그 자체가 데모 전체를 못 띄우게 한다.
# =============================================================================

# -----------------------------------------------------------------------------
# TASK-MONO-770 — 데모 메일함(Mailpit UI)의 basic auth 자격도 **이 파일이** 읽는다
# -----------------------------------------------------------------------------
# 같은 부팅 경로 · 같은 «값을 찍지 않는다» 규칙 · 같은 SSM 이라 새 파일을 만들지 않았다. 🔴 더 중요한 이유:
# 부팅 계약(demo-boot.sh 가 이 파일을 source 하고 `fetch_oauth_secrets` 하나를 부른다)을 **하네스 둘이**
# 이 파일을 통째로 스텁해 모사한다(ci.yml 의 DEMO_DOMAIN 폴백 칸 · verify-demo-wrapper.sh (z24)). 두 번째
# 파일·두 번째 함수를 만들면 그 둘이 «No such file / command not found» 로 죽는다 — 763 이 (z24)에서 실제로
# 겪은 그 모양이다. 그래서 함수 이름은 그대로 두고 그 안에서 한 칸을 더 읽는다.
#
#   있음(`<user>:<hash>` 한 줄) → MAILPIT_UI_BASICAUTH_USERS · MAILPIT_UI_ENABLED=true 를 export
#   없음 · 읽기 실패 · 모양이 틀림 → **아무것도 export 하지 않는다** ⇒ Mailpit 라우터 꺼짐(fail-closed —
#   iam-traefik.override.yml 의 `traefik.enable=${MAILPIT_UI_ENABLED:-false}`). «인증 없는 메일함» 으로
#   떨어지는 갈래는 없다. 없음은 조용히, 읽기 실패·모양 틀림은 한 줄 경고.
MAIL_UI_SECRET_NAME="/portfolio-demo/mailpit/ui-basicauth-users"

OAUTH_PROVIDERS=(google kakao microsoft naver)

# -----------------------------------------------------------------------------
# 리전 — demo-selection.sh 와 같은 IMDS 해소를 **재사용**한다(중복 정의하지 않는다).
# 🔵 declare -F 가드는 demo-selection.sh 자신의 것과 같다 — demo-boot.sh 의 selection
#    분기가 이미 demo-selection.sh 를 source 했으면 여기서 다시 정의하지 않는다.
# -----------------------------------------------------------------------------
if ! declare -F imds_region >/dev/null 2>&1; then
  __OAUTH_FETCH_HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  # shellcheck source=infra/demo/demo-selection.sh
  source "$__OAUTH_FETCH_HERE/demo-selection.sh"
  unset __OAUTH_FETCH_HERE
fi

# -----------------------------------------------------------------------------
# _oauth_fetch_one <provider> <field> <env접미사>
#   provider : google | kakao | microsoft | naver
#   field    : client-id | client-secret   (파라미터 이름의 마지막 세그먼트)
#   접미사   : CLIENT_ID | CLIENT_SECRET    (OAUTH_<PROVIDER>_<접미사> 로 export)
# -----------------------------------------------------------------------------
_oauth_fetch_one() {
  local provider="$1" field="$2" suffix="$3"
  local name value rc err_file provider_upper var
  name="/portfolio-demo/oauth/${provider}/${field}"
  err_file="$(mktemp)"
  # 🔴 `&&`/`||` 로 감싼다 — 그냥 `value="$(...)"` 단독이면 명령 치환 실패가 이 함수를
  #    호출한 쪽의 `set -e` 를 그대로 발동시켜 부팅을 끊는다(demo-selection.sh 의
  #    `raw="$(aws ssm get-parameter …)" || raw=""` 와 같은 이유).
  value="$(aws ssm get-parameter --region "$OAUTH_REGION" --with-decryption \
             --name "$name" --query 'Parameter.Value' --output text 2>"$err_file")" \
    && rc=0 || rc=$?

  if [ "$rc" -eq 0 ] && [ -n "$value" ] && [ "$value" != "None" ]; then
    provider_upper="$(printf '%s' "$provider" | tr '[:lower:]' '[:upper:]')"
    var="OAUTH_${provider_upper}_${suffix}"
    export "${var}=${value}"
  elif grep -q 'ParameterNotFound' "$err_file" 2>/dev/null; then
    : # 정상 — 아직 등록되지 않은 제공자/키. export 하지 않는다. 경고 없음(§ 위 설계).
  else
    echo "[oauth] ⚠ ${provider}/${field} 읽기 실패 — export 하지 않고 계속합니다 (권한·네트워크를 확인하세요)" >&2
  fi
  rm -f "$err_file"
}

# -----------------------------------------------------------------------------
# _mail_ui_fetch — TASK-MONO-770. 데모 메일함 basic auth 자격(htpasswd 한 줄)을 읽는다(위 머리말).
# 🔴 값은 찍지 않는다 · 모양 검사도 값을 출력하지 않고 참/거짓만 쓴다.
# -----------------------------------------------------------------------------
_mail_ui_fetch() {
  local value rc err_file
  err_file="$(mktemp)"
  value="$(aws ssm get-parameter --region "$OAUTH_REGION" --with-decryption \
             --name "$MAIL_UI_SECRET_NAME" --query 'Parameter.Value' --output text 2>"$err_file")" \
    && rc=0 || rc=$?

  if [ "$rc" -eq 0 ] && [ -n "$value" ] && [ "$value" != "None" ]; then
    # 한 줄 `<user>:<hash>` 만 받는다 — 공백·줄바꿈이 섞이면 Traefik 라벨이 깨지거나 사용자 둘로 읽힌다.
    if [[ "$value" =~ ^[^:[:space:]]+:[^[:space:]]+$ ]]; then
      export MAILPIT_UI_BASICAUTH_USERS="$value"
      export MAILPIT_UI_ENABLED=true
    else
      echo "[mail] ⚠ 메일함 자격의 모양이 '<user>:<hash>' 한 줄이 아닙니다 — 메일함 UI 를 열지 않고 계속합니다" >&2
    fi
  elif grep -q 'ParameterNotFound' "$err_file" 2>/dev/null; then
    : # 아직 등록 안 함 — 메일함 UI 는 닫힌 채(정상). 경고 없음.
  else
    echo "[mail] ⚠ 메일함 자격 읽기 실패 — 메일함 UI 를 열지 않고 계속합니다 (권한·네트워크를 확인하세요)" >&2
  fi
  rm -f "$err_file"
}

# -----------------------------------------------------------------------------
# fetch_oauth_secrets — 네 제공자 × (client-id, client-secret) 를 전부 읽는다.
# 🔴 항상 rc=0 으로 끝난다 — 이 함수 자체의 실패로 부팅을 막지 않는다. 진짜 실패는
#    위 _oauth_fetch_one 의 한 줄 경고로만 드러난다.
# -----------------------------------------------------------------------------
fetch_oauth_secrets() {
  local __oauth_had_xtrace=0
  case "$-" in *x*) __oauth_had_xtrace=1; set +x ;; esac

  if ! command -v aws >/dev/null 2>&1; then
    echo "[oauth] ⚠ aws CLI 가 없습니다 — 소셜 로그인 키를 읽지 않고 기본값(test-*)으로 계속합니다" >&2
    [ "$__oauth_had_xtrace" -eq 1 ] && set -x
    return 0
  fi

  OAUTH_REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
  if [ -z "$OAUTH_REGION" ]; then
    OAUTH_REGION="$(imds_region)" || {
      echo "[oauth] ⚠ 리전을 알 수 없습니다(EC2 밖이거나 IMDSv2 차단) — 소셜 로그인 키를 읽지 않고 계속합니다" >&2
      [ "$__oauth_had_xtrace" -eq 1 ] && set -x
      return 0
    }
  fi

  local provider
  for provider in "${OAUTH_PROVIDERS[@]}"; do
    _oauth_fetch_one "$provider" "client-id" "CLIENT_ID"
    _oauth_fetch_one "$provider" "client-secret" "CLIENT_SECRET"
  done

  # TASK-MONO-770 — 데모 메일함 UI 자격(머리말 참조). 실패해도 rc=0, 메일함만 닫힌다.
  _mail_ui_fetch

  [ "$__oauth_had_xtrace" -eq 1 ] && set -x
  return 0
}
