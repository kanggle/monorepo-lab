#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/lib.sh — 도메인 시드 스크립트가 공유하는 도구 (TASK-MONO-506)
# =============================================================================
# 이 파일은 **정책을 코드로 강제한다.** 시드의 원칙은 하나다:
#
#     넣을 수 있는 것은 실제 API 로 넣는다. 넣는 행위 자체가 그 기능의 검증이다.
#
# API 200 을 확인하는 스모크와 API 로 데이터를 만드는 시드는 같은 요청을 보내지만
# 성질이 다르다 — 후자는 실패하면 데모가 비므로 **아무도 무시할 수 없다.** 그래서
# 이 저장소가 반복해서 당한 "초록인데 화면은 비었다" 를 구조적으로 줄인다.
#
# 직접-DB 는 금지가 아니라 **유료**다: `dbexec` 는 `--why` 를 필수 인자로 받고,
# 빠뜨리면 실행되지 않는다. 사유 없는 직접-DB 를 "깜빡" 하는 것이 불가능하다
# (TASK-MONO-506 AC-1 이 요구하는 성질을 **술어 자체로** 검사한다 — 사후 grep 가드는
# 우회 가능한 대리지표다). 가드 (y) 가 이 파일 밖의 raw `psql`/`mysql` 호출을 막는다.
#
# -----------------------------------------------------------------------------
# 의존성: curl · openssl · docker · bash 4+.  **jq 는 쓰지 않는다** — 데모 AMI 와
# 이 저장소의 Windows 개발 호스트 양쪽에 없다(그리고 없을 때 조용히 빈 문자열을
# 내어 폴링 루프를 영원히 돌린다). JSON 은 grep/sed 로 최소한만 긁는다.
# =============================================================================
set -uo pipefail

SEED_LIB_LOADED=1

# --- 로깅 --------------------------------------------------------------------
SEED_DOMAIN="${SEED_DOMAIN:-?}"
seed_log()  { printf '[seed:%s] %s\n' "$SEED_DOMAIN" "$*"; }
seed_warn() { printf '[seed:%s] ⚠ %s\n' "$SEED_DOMAIN" "$*" >&2; }
seed_fail() { printf '[seed:%s] ✗ %s\n' "$SEED_DOMAIN" "$*" >&2; SEED_FAILURES=$((SEED_FAILURES + 1)); }
SEED_FAILURES=0
SEED_CREATED=0
SEED_EXISTING=0

# --- DEMO_DOMAIN 기반 호스트 -------------------------------------------------
# demo.env 가 이미 source 돼 있다는 가정 (demo-up.sh 가 그렇게 부른다). 단독 실행 시
# 호출자가 DEMO_DOMAIN 을 export 하면 된다.
DEMO_DOMAIN="${DEMO_DOMAIN:-local}"

# `*.local` 은 이 저장소의 개발 호스트에서 hosts 파일에 없을 수 있고, EC2 에서는
# sslip.io 가 진짜 DNS 로 해소된다. 양쪽 모두에서 동작하도록 curl 에 --resolve 를
# 건다 — DEMO_DOMAIN=local 일 때만 필요하고, 실 도메인일 때는 무해하다(진짜 DNS 가
# 이긴다면 --resolve 는 그 이름에만 적용되므로 정확히 같은 곳을 가리킨다).
seed_resolve_args() {
  local host="$1"
  if [ "$DEMO_DOMAIN" = "local" ]; then printf -- '--resolve\n%s:80:127.0.0.1\n' "$host"; fi
}

# --- HTTP --------------------------------------------------------------------
# SEED_LAST_BODY / SEED_LAST_STATUS 에 결과를 남긴다(파이프를 쓰지 않는다 —
# `cmd | tail` 은 tail 의 종료코드를 내므로 실패가 초록으로 보인다).
SEED_LAST_BODY=""
SEED_LAST_STATUS=""

# http <method> <url> [body] [extra-header...]
#
# 🔴 본문은 **임시 파일**로 받는다. 첫 판은 `-o /dev/stdout -w '\n%{http_code}'` 로
# 본문과 상태코드를 한 스트림에 섞었는데, 그 둘의 쓰기 순서가 보장되지 않아 2회차
# 실행에서 본문이 통째로 비어 나왔다(상태코드는 멀쩡했다). 본문을 마커로 검사하는
# 멱등 로직이 그 위에 서 있으므로, 이 흔들림은 "이미 있는데 또 만든다" 로 직결된다.
http() {
  local method="$1" url="$2" body="${3:-}"; shift 3 2>/dev/null || shift $#
  local host; host="$(printf '%s' "$url" | sed -E 's#^https?://([^/]+).*#\1#')"
  local bodyfile; bodyfile="$(mktemp)"
  local -a args=(-s -o "$bodyfile" -w '%{http_code}' -X "$method")
  mapfile -t r < <(seed_resolve_args "$host"); [ "${#r[@]}" -gt 0 ] && args+=("${r[@]}")
  [ -n "${SEED_TOKEN:-}" ] && args+=(-H "Authorization: Bearer $SEED_TOKEN")
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  args+=("$@")
  SEED_LAST_STATUS="$(curl "${args[@]}" "$url" 2>/dev/null)"
  SEED_LAST_BODY="$(cat "$bodyfile" 2>/dev/null)"
  rm -f "$bodyfile"
  [ "${SEED_LAST_STATUS:-000}" -ge 200 ] 2>/dev/null && [ "$SEED_LAST_STATUS" -lt 300 ]
}

# api_create <라벨> <url> <body> — 멱등 생성.
# 2xx = 생성, 409/422(중복) = 이미 존재. 그 외는 실패로 센다.
# 왜 409 를 성공으로 세는가: AC-4 는 "연속 2회 실행해도 같은 상태에 수렴" 을 요구하지
# "2회차에도 201" 을 요구하지 않는다. 서버가 중복을 거절하는 것은 **올바른 동작**이다.
api_create() {
  local label="$1" url="$2" body="$3"
  if http POST "$url" "$body"; then
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  $label"
    return 0
  fi
  case "$SEED_LAST_STATUS" in
    409|422)
      SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label (HTTP $SEED_LAST_STATUS)"
      return 0 ;;
    *)
      seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
      return 1 ;;
  esac
}

# api_create_unless <라벨> <탐지 GET url> <탐지 마커> <생성 url> <body>
# 서버가 중복을 409 로 거절하지 **않는** 엔드포인트(같은 것을 계속 새로 만드는
# 엔드포인트)를 위한 멱등 래퍼. 마커가 목록 응답에 이미 있으면 건너뛴다.
api_create_unless() {
  local label="$1" probe="$2" marker="$3" url="$4" body="$5"
  if http GET "$probe" && printf '%s' "$SEED_LAST_BODY" | grep -qF -- "$marker"; then
    SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label"
    return 0
  fi
  api_create "$label" "$url" "$body"
}

# --- 토큰 --------------------------------------------------------------------
# client_credentials — 워크로드 클라이언트. 도메인 백오피스(WMS/SCM/ERP/Finance)의
# 쓰기 스코프가 여기 달려 있다(auth-service V0010/V0013/V0017/V0018/V0019).
client_token() {
  local cid="$1" csec="$2" scope="$3"
  local iam="http://iam.${DEMO_DOMAIN}"
  local -a args=(-s -u "$cid:$csec" -d grant_type=client_credentials --data-urlencode "scope=$scope")
  mapfile -t r < <(seed_resolve_args "iam.${DEMO_DOMAIN}"); [ "${#r[@]}" -gt 0 ] && args+=("${r[@]}")
  local out; out="$(curl "${args[@]}" "$iam/oauth2/token" 2>/dev/null)"
  printf '%s' "$out" | grep -q '"access_token"' || { seed_warn "client_credentials 실패($cid): ${out:0:200}"; return 1; }
  printf '%s' "$out" | sed -E 's/.*"access_token":"([^"]*)".*/\1/'
}

# user_token <client_id> <secret> <redirect_uri> <scope> [email] [password]
# authorization_code + PKCE 를 **끝까지** 밟아 데모 사용자 토큰을 얻는다.
#
# 왜 이 고생을 하는가: 소비자 표면(스토어프런트 · 팬)의 API 는 전부 사용자 신원에
# 묶여 있다(`sub` → X-User-Id). client_credentials 토큰으로는 "내 장바구니 / 내 구독"
# 을 만들 수 없다. 그리고 이 플로우가 성립한다는 것 자체가 **면접관이 밟을 로그인
# 경로가 살아 있다**는 증거다 — 시드가 통과하면 로그인은 이미 검증된 것이다.
#
# 알려진 함정 두 가지(실측):
#   · `/oauth2/authorize` 는 `Accept: text/html` 이 없으면 401 을 낸다.
#   · 로그인 폼은 CSRF 토큰을 요구한다 — 같은 쿠키 자(jar)로 authorize → login →
#     authorize(재개) → redirect 를 이어야 한다.
user_token() {
  local cid="$1" csec="$2" redir="$3" scope="$4"
  local email="${5:-${DEMO_EMAIL:-demo@demo.com}}" pass="${6:-${DEMO_PASSWORD:-Demo1234!}}"
  local iam="http://iam.${DEMO_DOMAIN}"
  local jar tmp; jar="$(mktemp)"; tmp="$(mktemp -d)"
  local -a R; mapfile -t R < <(seed_resolve_args "iam.${DEMO_DOMAIN}")
  local -a C=(-s -c "$jar" -b "$jar"); [ "${#R[@]}" -gt 0 ] && C+=("${R[@]}")

  local verifier challenge
  verifier="$(openssl rand -hex 48)"
  challenge="$(printf '%s' "$verifier" | openssl dgst -binary -sha256 | openssl base64 -A | tr '+/' '-_' | tr -d '=')"

  local url="$iam/oauth2/authorize?response_type=code&client_id=$(urlenc "$cid")&redirect_uri=$(urlenc "$redir")&scope=$(urlenc "$scope")&state=seed&code_challenge=$challenge&code_challenge_method=S256"
  curl "${C[@]}" -L -H 'Accept: text/html' -o "$tmp/login.html" "$url" 2>/dev/null

  local csrf; csrf="$(grep -oE 'name="_csrf"[^>]*value="[^"]*"' "$tmp/login.html" | head -1 | sed -E 's/.*value="([^"]*)".*/\1/')"
  if [ -z "$csrf" ]; then
    seed_warn "로그인 폼에서 CSRF 토큰을 찾지 못했습니다 (인증 서버 응답 ${#csrf} bytes)"
    rm -rf "$jar" "$tmp"; return 1
  fi
  curl "${C[@]}" -o /dev/null -D "$tmp/login.hdr" -H 'Accept: text/html' \
    --data-urlencode "username=$email" --data-urlencode "password=$pass" \
    --data-urlencode "_csrf=$csrf" "$iam/login" 2>/dev/null

  local loc; loc="$(grep -i '^location:' "$tmp/login.hdr" | tail -1 | tr -d '\r' | awk '{print $2}')"
  if [ -z "$loc" ]; then
    seed_warn "로그인이 리다이렉트를 내지 않았습니다 — 자격증명($email)이 틀렸을 수 있습니다"
    rm -rf "$jar" "$tmp"; return 1
  fi
  curl "${C[@]}" -o /dev/null -D "$tmp/cb.hdr" -H 'Accept: text/html' "$loc" 2>/dev/null
  local code; code="$(grep -i '^location:' "$tmp/cb.hdr" | tail -1 | grep -oE 'code=[^&[:space:]]*' | cut -d= -f2 | tr -d '\r')"
  if [ -z "$code" ]; then
    seed_warn "인가 코드를 받지 못했습니다 — redirect_uri($redir)가 등록되지 않았을 수 있습니다"
    rm -rf "$jar" "$tmp"; return 1
  fi

  # 공개 클라이언트(secret 없음, PKCE only — `platform-console-web` 이 그렇다)는
  # basic auth 대신 body 의 client_id 로 자신을 밝힌다.
  local -a T=(-s); [ "${#R[@]}" -gt 0 ] && T+=("${R[@]}")
  if [ -n "$csec" ]; then T+=(-u "$cid:$csec"); else T+=(-d "client_id=$cid"); fi
  local out; out="$(curl "${T[@]}" -d grant_type=authorization_code -d "code=$code" \
    --data-urlencode "redirect_uri=$redir" -d "code_verifier=$verifier" "$iam/oauth2/token" 2>/dev/null)"
  rm -rf "$jar" "$tmp"
  printf '%s' "$out" | grep -q '"access_token"' || { seed_warn "토큰 교환 실패: ${out:0:200}"; return 1; }
  printf '%s' "$out" | sed -E 's/.*"access_token":"([^"]*)".*/\1/'
}

# operator_token [tenant] [email] [password] — 5개 도메인 백오피스를 여는 토큰.
#
# 🔵 email/password 인자는 TASK-MONO-519 가 추가했다. 기본값은 그대로 데모 단일
#    신원(`demo@demo.com`)이라 기존 호출부는 한 글자도 안 바뀐다. 두 번째 신원이
#    필요한 이유는 erp 결재의 자기결재 금지가 **생성 시점 게이트**라서다 — 상신자와
#    승인자가 같은 `sub` 면 행이 아예 안 만들어진다(seed-erp.sh §6 참조).
#    🔴 인자를 넘길 때는 그 신원이 `admin_operators` + `operator_tenant_assignment`
#    양쪽에 있어야 한다. 자격증명만 있으면 로그인은 되고 assume 이 `invalid_grant`
#    로 떨어지는데, 두 실패는 호출자 입장에서 똑같이 "토큰을 못 얻었다" 로 보인다.
#
# 이것이 이 시드에서 가장 중요한 발견이다. 데모 계정은 각 도메인 테넌트에서
# `CUSTOMER` 일 뿐이라 `/api/admin/**` 에 그대로 가면 **전부 403** 이다(실측 6/6).
# 운영자 권한은 계정에 붙어 있지 않고 **`demo-corp` 를 assume 하는 순간 파생된다**
# (`OperatorRoleDerivation.fromEntitledDomains` — TASK-BE-571 이 그 테넌트에 5개
# 도메인 구독을 심었다). 즉:
#
#   콘솔 로그인(platform-console-web, 공개 클라이언트 · PKCE)
#     → RFC 8693 token-exchange, audience=demo-corp
#     → roles=[ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
#              WMS_OPERATOR, OUTBOUND_*, INBOUND_*, INVENTORY_*, MASTER_READ]
#
# 실측: 이 토큰으로 ecommerce 의 admin 7개 엔드포인트가 403 → 200 이 됐다.
# 그리고 이 경로는 **면접관이 콘솔에서 밟는 바로 그 경로**다 — 시드가 성립하면
# 콘솔의 도메인 운영 섹션이 열린다는 것이 이미 증명된 셈이다.
operator_token() {
  local tenant="${1:-demo-corp}" email="${2:-}" pass="${3:-}"
  local iam="http://iam.${DEMO_DOMAIN}"
  local base
  base="$(user_token "platform-console-web" "" "http://console.${DEMO_DOMAIN}/api/auth/callback" \
            "openid profile email tenant.read erp.write" "$email" "$pass")" || return 1
  local -a R; mapfile -t R < <(seed_resolve_args "iam.${DEMO_DOMAIN}")
  local -a T=(-s); [ "${#R[@]}" -gt 0 ] && T+=("${R[@]}")
  local out; out="$(curl "${T[@]}" \
    -d 'grant_type=urn:ietf:params:oauth:grant-type:token-exchange' \
    -d 'client_id=platform-console-web' \
    -d "subject_token=$base" \
    -d 'subject_token_type=urn:ietf:params:oauth:token-type:access_token' \
    -d "audience=$tenant" "$iam/oauth2/token" 2>/dev/null)"
  printf '%s' "$out" | grep -q '"access_token"' \
    || { seed_warn "assume-tenant($tenant) 실패: ${out:0:200}"; return 1; }
  printf '%s' "$out" | sed -E 's/.*"access_token":"([^"]*)".*/\1/'
}

# JWT 의 `sub` — 시드가 사용자 소유 행을 만들 때 필요하다(디코드만; 검증은 서버 몫).
jwt_sub() {
  local payload="${1#*.}"; payload="${payload%%.*}"
  # base64url → base64 + 패딩
  payload="$(printf '%s' "$payload" | tr '_-' '/+')"
  case $(( ${#payload} % 4 )) in 2) payload="$payload==";; 3) payload="$payload=";; esac
  printf '%s' "$payload" | openssl base64 -d -A 2>/dev/null | sed -E 's/.*"sub":"([^"]*)".*/\1/'
}

urlenc() {
  local s="$1" o="" i c
  for (( i=0; i<${#s}; i++ )); do
    c="${s:i:1}"
    case "$c" in [a-zA-Z0-9.~_-]) o="$o$c";; *) o="$o$(printf '%%%02X' "'$c")";; esac
  done
  printf '%s' "$o"
}

# --- 직접 DB (사유 필수) ------------------------------------------------------
# dbexec --why "<사유>" <container> <engine> <db> <user> <password> <<'SQL'
#
# `--why` 가 없으면 **실행 자체를 거부한다.** AC-1 의 "사유 없는 직접-DB 0건" 은
# 리뷰어의 성실함이 아니라 이 게이트가 보장한다.
#
# 사유에는 *무엇이 막혔는지*를 적는다("API 없음" 이 아니라 "생성 엔드포인트가
# 존재하지 않는다 — user-service 컨트롤러 4개 전수 확인"). 다음 사람이 그 사유를
# 재검증할 수 있어야 한다.
dbexec() {
  local why=""
  if [ "${1:-}" = "--why" ]; then why="$2"; shift 2; fi
  if [ -z "$why" ]; then
    seed_fail "dbexec 가 --why 없이 호출됐습니다 (AC-1: 사유 없는 직접-DB 금지)"
    return 2
  fi
  local container="$1" engine="$2" db="$3" user="$4" pass="${5:-}"
  seed_log "직접-DB [$container/$db] 사유: $why"
  case "$engine" in
    psql)  docker exec -i "$container" psql -U "$user" -d "$db" -v ON_ERROR_STOP=1 -q ;;
    mysql) docker exec -i "$container" mysql -u"$user" -p"$pass" "$db" 2>/dev/null ;;
    *)     seed_fail "dbexec: 알 수 없는 engine=$engine"; return 2 ;;
  esac
}

# dbquery — 읽기 전용 조회(검증용). 사유가 필요 없다: 아무것도 쓰지 않는다.
dbquery() {
  local container="$1" engine="$2" db="$3" user="$4" pass="$5" sql="$6"
  case "$engine" in
    psql)  docker exec "$container" psql -U "$user" -d "$db" -tAc "$sql" 2>/dev/null ;;
    mysql) docker exec "$container" mysql -u"$user" -p"$pass" "$db" -N -B -e "$sql" 2>/dev/null ;;
  esac
}

# --- 대기 --------------------------------------------------------------------
# demo-up.sh 는 `up -d` 로 끝난다 — 여기 도달했을 때 앱이 아직 부팅 중일 수 있다.
# 시드가 "연결 거부" 로 실패하고 데모가 비는 것이 이 저장소의 반복 실패 모드다.
#
# 🔴 `wait_http` 는 **엣지 준비성**만 잰다. 뒤의 백엔드에 대해서는 아무것도 증명하지
# 않는다 — 아래 `wait_backend` 를 반드시 함께 쓸 것.
wait_http() {
  local url="$1" timeout="${2:-180}" i
  local host; host="$(printf '%s' "$url" | sed -E 's#^https?://([^/]+).*#\1#')"
  local -a args=(-s -o /dev/null -w '%{http_code}')
  mapfile -t r < <(seed_resolve_args "$host"); [ "${#r[@]}" -gt 0 ] && args+=("${r[@]}")
  for (( i=0; i<timeout; i+=5 )); do
    local sc; sc="$(curl "${args[@]}" "$url" 2>/dev/null)"
    # 401/403 도 "떠 있다" 이다 — 인증이 걸린 엔드포인트가 살아있다는 뜻이다.
    case "${sc:-000}" in 2??|30?|401|403) return 0 ;; esac
    sleep 5
  done
  return 1
}

# wait_backend <라벨> <절대 URL> [초] — **인증된** 2xx 를 기다린다.
#
# 🔴 왜 `wait_http` 만으로는 부족한가 (이 저장소가 **세 도메인에서 각각** 물렸다)
#
# `wait_http` 는 401/403 을 "살아 있다" 로 센다. 게이트웨이 자신에 대해서는 맞는
# 술어지만 **뒤의 서비스에 대해서는 아무것도 증명하지 않는다** — 토큰 없는 요청은
# 게이트웨이의 시큐리티 필터가 끊어 버려서 백엔드에 닿지도 않기 때문이다.
#
#   erp (2026-08-06, 볼륨 초기화 후 첫 기동)
#     wait_http → 통과(401) → POST /api/erp/masterdata/job-grades → 500
#     게이트웨이 로그 `Connection refused: masterdata-service`, masterdata 로그 에러 0건
#     ⇒ 요청이 **도달조차** 하지 않았다.  → 16건 전부 실패
#   scm · wms (2026-08-07, `demo-up.sh` 직후)
#     같은 지문으로 각각 **8건 / 2건 전부 500**. 컨테이너가 healthy 가 된 뒤 같은
#     스크립트를 재실행하면 실패 0 으로 수렴 ⇒ 시드가 아니라 **게이트가 틀렸다**.
#
# 그래서 **토큰을 얻은 뒤** 인증된 GET 이 2xx 를 낼 때까지 다시 기다린다.
# 🔵 서비스를 **각각** 확인할 것 — 하나만 보고 나머지를 추정하면 같은 함정을 한 겹
# 아래에서 반복한다(그 도메인의 백엔드는 서로 다른 시점에 뜬다).
wait_backend() {
  local label="$1" url="$2" timeout="${3:-240}" i
  for (( i=0; i<timeout; i+=5 )); do
    http GET "$url" >/dev/null && return 0
    sleep 5
  done
  seed_fail "$label 이 ${timeout}초 안에 인증된 요청에 2xx 를 내지 않았습니다 (마지막 HTTP $SEED_LAST_STATUS)"
  return 1
}

container_up() { docker ps --format '{{.Names}}' | grep -qx "$1"; }

seed_summary() {
  seed_log "요약 — 생성 $SEED_CREATED · 기존 $SEED_EXISTING · 실패 $SEED_FAILURES"
  return $(( SEED_FAILURES > 0 ? 1 : 0 ))
}
