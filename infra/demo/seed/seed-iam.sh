#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-iam.sh — iam 도메인: 최근 4주 로그인·잠금 이력 (TASK-MONO-820)
# =============================================================================
# iam 의 계정 · 자격증명 · 운영자 · 테넌트는 **Flyway 가 심는다**(각 서비스의 db/migration-dev
# R__ 파일 — e2e 프로필에서만 로드). 이 스크립트가 하는 일은 하나뿐이다:
#
#   콘솔 IAM 개요의 «실적»(GET /api/admin/metrics — loginSuccess · loginFailure · accountLocked)
#   과 운영자 개요의 «로그인 실패 — 이번 주 vs 지난 주» 가 하루치가 아니라 **최근 4주**를
#   그리게 한다.
#
# -----------------------------------------------------------------------------
# 🔴 왜 직접-DB 인가 (dbexec --why 의 근거 — 재검증 가능하게)
# -----------------------------------------------------------------------------
# · login_history / account_lock_history 를 **쓰는 API 가 없다.** 두 테이블은 security-service 의
#   Kafka 소비자(auth.login.* · account.locked)만 쓰고, occurred_at 은 그 이벤트의 시각 = 실제
#   로그인한 순간이다. «지난주 화요일의 로그인» 을 만드는 길은 존재하지 않는다.
# · 그리고 **데모 운영자의 로그인은 demo-corp 지표에 안 잡힌다.** demo@ · requester@ 의 콘솔
#   자격증명은 tenant `iam`(auth R__01 · R__seed_demo_second_operator_credential)이라 그 로그인은
#   login_history.tenant_id='iam' 으로 기록되고, 지표는 tenant_id = X 또는 site_tenant_id = X
#   로 센다(SecurityMetricsQueryService). demo-corp 에 속한 계정은 하나뿐이다 —
#   newhire@demo-corp.example (account R__07, 자격증명 auth R__03, Demo1234!). 그래서 이력은
#   **그 계정**에 귀속한다. 가짜 계정은 만들지 않는다(소유자 결정 — 자격 없는 로그인 가능 신원).
#
# -----------------------------------------------------------------------------
# 🔴 지울 수 없다 — 그래서 누적된다 (README § 시각 경계 예외 · IAM 누적 상한)
# -----------------------------------------------------------------------------
# login_history 는 UPDATE/DELETE 를 트리거가 막는다(security V0002 · V0010). 이 시드는 행을
# 고치거나 지우지 않는다 — event_id(UNIQUE)를 KST 날짜 + 순번에서 유도하고 INSERT IGNORE 한다.
# ⇒ 같은 날 재실행은 +0, 다음 날 부팅은 «새로 창에 들어온 날짜» 만큼만 늘어난다.
#   하루 ≤ 11행(성공 ≤7 · 실패 ≤3 · 잠금 ≤1), 총량 ≤ 11 × (28 + 첫 부팅~마지막 부팅 날 수).
#   창 밖으로 밀린 옛 날짜의 행은 그대로 남는다(지표 창 밖이라 그래프에는 안 보인다).
#
# 이 스크립트는 API 를 부르지 않으므로 게이트웨이 · 토큰이 필요 없다. 대신 Flyway 가 끝났는지
# (security V0013 의 site_tenant_id 열, account R__07 의 계정 행)를 **읽어서** 기다린다.
# =============================================================================
set -uo pipefail
SEED_DOMAIN=iam
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/demo/seed/lib.sh
. "$HERE/lib.sh"
# shellcheck source=infra/demo/seed/lib-backfill.sh
. "$HERE/lib-backfill.sh"

IAM_MY_C="iam-mysql"
SEC_DB="security_db"; SEC_U="security_user"; SEC_P="${SECURITY_DB_PASSWORD:-security_pass}"
ACC_DB="account_db";  ACC_U="account_user";  ACC_P="${ACCOUNT_DB_PASSWORD:-account_pass}"
IAM_TENANT="demo-corp"
# account-service db/migration-dev/R__07_seed_demo_corp_new_hire_account.sql 의 고정 id —
# demo-corp 의 **유일한** 계정(그 파일 헤더: «account_db.accounts tenant_id='demo-corp' count = 0» 을 메운 행).
DEMO_CORP_ACCOUNT_ID="0199de70-0000-7000-8000-00000000ad07"

if ! container_up "$IAM_MY_C"; then
  seed_log "iam-mysql 이 기동돼 있지 않습니다 — 건너뜁니다"
  exit 0
fi

# --- Flyway 완료 대기 (읽기 전용) ---------------------------------------------
# 🔴 컨테이너가 떠 있다 ≠ 스키마가 있다. 대기는 60초 — 시드 단계 전체가 demo-up.sh 의
#    POST_UP_BUDGET(240s) 하나에 묶여 있고 iam 이 첫 도메인이다(여기서 오래 기다리면 뒤 도메인이 잘린다).
#    security-service 가 아직 V0013 을 적용하지 않았으면
#    INSERT 가 열 이름에서 실패하고, account R__07 이 아직이면 없는 계정에 이력을 붙이게 된다.
sec_ready="" acc_ready=""
for (( t=0; t<60; t+=5 )); do
  sec_ready="$(dbquery "$IAM_MY_C" mysql "$SEC_DB" "$SEC_U" "$SEC_P" \
    "SELECT count(*) FROM information_schema.columns WHERE table_schema='$SEC_DB' AND table_name='login_history' AND column_name='site_tenant_id'" | tr -d ' \r\n')"
  acc_ready="$(dbquery "$IAM_MY_C" mysql "$ACC_DB" "$ACC_U" "$ACC_P" \
    "SELECT count(*) FROM accounts WHERE id='$DEMO_CORP_ACCOUNT_ID' AND tenant_id='$IAM_TENANT'" | tr -d ' \r\n')"
  [ "$sec_ready" = "1" ] && [ "$acc_ready" = "1" ] && break
  sleep 5
done
if [ "$sec_ready" != "1" ]; then
  seed_fail "security_db.login_history 에 site_tenant_id 열이 없습니다(읽은 값 '${sec_ready:-<읽기 실패>}', 60초 대기) — security-service Flyway(V0013)가 끝나지 않았거나 접속 자격(security_user)이 다릅니다"
  seed_summary; exit $?
fi
if [ "$acc_ready" != "1" ]; then
  seed_fail "account_db.accounts 에 demo-corp 계정 $DEMO_CORP_ACCOUNT_ID 가 없습니다(읽은 값 '${acc_ready:-<읽기 실패>}') — account-service 의 dev 시드 R__07 이 적용되지 않았습니다(e2e 프로필?). 없는 계정에 이력을 붙이지 않습니다"
  seed_summary; exit $?
fi

# --- 최근 4주 로그인·잠금 이력 ---------------------------------------------------
iam_backfill() {
  local sql pl pk q qk before after bk ak
  bf_init || { seed_fail "로그인 이력 — 창을 계산하지 못했습니다"; return 1; }
  sql="$(bf_iam_sql "$IAM_TENANT" "$DEMO_CORP_ACCOUNT_ID")" || { seed_fail "로그인 이력 — SQL 생성 실패"; return 1; }
  pl="$(bf_planned "$sql" login_history)"; pk="$(bf_planned "$sql" account_lock_history)"
  # 🔴 범위는 코드마다 따로 — «2101 시작 ~ 2102 끝» 한 범위는 창보다 오래된 2102(실패) 행까지 덮어
  #    두 번째 부팅부터 계획보다 많이 센다(누적 표라 옛 날짜 행이 남는다).
  local s_lo s_hi
  bf_id_range 2101; s_lo="$BF_LO"; s_hi="$BF_HI"
  bf_id_range 2102
  q="SELECT count(*) FROM login_history WHERE tenant_id='$IAM_TENANT' AND (event_id BETWEEN '$s_lo' AND '$s_hi' OR event_id BETWEEN '$BF_LO' AND '$BF_HI')"
  bf_id_range 2103; qk="SELECT count(*) FROM account_lock_history WHERE tenant_id='$IAM_TENANT' AND event_id BETWEEN '$BF_LO' AND '$BF_HI'"
  before="$(dbquery "$IAM_MY_C" mysql "$SEC_DB" "$SEC_U" "$SEC_P" "$q" | tr -d ' \r\n')"
  bk="$(dbquery "$IAM_MY_C" mysql "$SEC_DB" "$SEC_U" "$SEC_P" "$qk" | tr -d ' \r\n')"
  if ! dbexec --why "login_history·account_lock_history 를 쓰는 API 가 없다 — 두 표는 security-service 의 Kafka 소비자(auth.login.* · account.locked)만 쓰고 occurred_at 은 실제 로그인 순간이라 과거 날짜 로그인을 만들 길이 없다. 데모 운영자(demo@·requester@)의 콘솔 로그인은 자격증명 tenant 'iam'(auth R__01)으로 기록돼 demo-corp 지표(tenant_id 또는 site_tenant_id = demo-corp, SecurityMetricsQueryService)에 안 잡힌다 ⇒ demo-corp 의 유일한 계정(account R__07 newhire)에 귀속한 «최근 28일» 이력을 넣는다(TASK-MONO-820 · README § 시각 경계 예외). 두 표는 UPDATE/DELETE 트리거로 지울 수 없다 → event_id 를 KST 날짜에서 유도하고 INSERT IGNORE 로 수렴(하루 ≤ 11행 누적)" \
      "$IAM_MY_C" mysql "$SEC_DB" "$SEC_U" "$SEC_P" <<<"$sql" >/dev/null; then
    seed_fail "로그인 이력 INSERT 실패(mysql rc≠0 — 오류 본문은 dbexec 가 버린다; 같은 SQL 을 손으로 흘려 확인하십시오)"
    return 1
  fi
  after="$(dbquery "$IAM_MY_C" mysql "$SEC_DB" "$SEC_U" "$SEC_P" "$q" | tr -d ' \r\n')"
  ak="$(dbquery "$IAM_MY_C" mysql "$SEC_DB" "$SEC_U" "$SEC_P" "$qk" | tr -d ' \r\n')"
  bf_day "$BF_FIRST"; local first="$BF_DATE"; bf_day "$BF_LAST"
  if [ -n "$pl" ] && [ "${after:-x}" = "$pl" ] && [ "${ak:-x}" = "$pk" ]; then
    if [ $(( ${after:-0} + ${ak:-0} )) -gt $(( ${before:-0} + ${bk:-0} )) ] 2>/dev/null; then
      SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  로그인 이력 $first ~ $BF_DATE (KST) — 로그인 $after · 잠금 $ak (이번에 +$(( after - ${before:-0} )) · +$(( ak - ${bk:-0} )))"
    else
      SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  로그인 이력 $first ~ $BF_DATE (KST) — 로그인 $after · 잠금 $ak (수렴, +0)"
    fi
  elif [ "${after:-0}" -gt 0 ] 2>/dev/null; then
    seed_warn "로그인 이력 — 창 안 로그인 $after/$pl · 잠금 $ak/$pk. 같은 event_id 를 다른 생성기 판이 먼저 넣었을 수 있습니다(지울 수 없는 표 — lib-backfill.sh 변경 후 첫 부팅이면 정상)"
  else
    seed_fail "로그인 이력 — INSERT 는 성공했는데 창 안 행이 ${after:-<읽기 실패>} 건입니다(계획 $pl)"
  fi
}
iam_backfill

seed_summary
