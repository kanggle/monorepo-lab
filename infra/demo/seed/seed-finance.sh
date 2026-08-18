#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-finance.sh — finance 도메인 데모 데이터 (TASK-MONO-510)
# =============================================================================
# 콘솔 Finance 섹션 4화면 중 데이터에 의존하는 2개를 표적으로 한다:
#
#   /finance/accounts  ← account-service 계좌 + 잔액 + 거래내역
#   /ledger            ← ledger-service 분개/시산표 (계좌 이벤트의 투영)
#
# (`/finance` 개요와 `/finance/guide` 는 데이터 없이도 렌더된다.)
#
# -----------------------------------------------------------------------------
# 🔵 원장은 **직접 넣지 않는다** — 계좌 이벤트가 만들게 한다
# -----------------------------------------------------------------------------
# `ledger_account` 는 사전 등록이 필요하고 Flyway 는 한 줄도 심지 않는다. 그러나
# 코드를 읽어 보면 갈래가 둘이다:
#   · **이벤트 기반** 전기(`PostJournalEntryUseCase`)는 `ensureAccountExists` 로
#     계정을 **자동 생성**한다.
#   · **수동** 전기(`PostManualJournalEntryUseCase`)는 없으면 `LedgerAccountNotFound`.
# ⇒ 그래서 이 시드는 수동 분개를 쏘지 않고 **계좌 개설 → KYC → 이체** 라는 제품의
#   자연 경로를 태운다. 그러면 /ledger 의 데이터는 "시드가 넣었다"가 아니라
#   **"투영이 동작한다"** 는 증거가 된다. (scm 의 공급사는 이런 갈래가 없어서 직접-DB
#   말고는 길이 없었다 — 도메인마다 다르다.)
#
# 🔴 그리고 비-2xx 를 "없음"으로 번역하지 않는다. 조회 실패와 200+빈 목록은 다른
# 사건이고, 갈라서 보고한다.
#
# 금액은 **minor-unit 정수 문자열**이다(MoneyDto: `^-?\d+$`). "1000.00" 은 422 다.
# =============================================================================
set -uo pipefail

SEED_DOMAIN="finance"
# shellcheck source=infra/demo/seed/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

FIN="http://finance.${DEMO_DOMAIN}"

seed_log "시작 — $FIN (운영자 토큰, assume demo-corp)"

if ! wait_http "$FIN/api/finance/accounts/probe" 240; then
  seed_fail "finance 게이트웨이가 240초 안에 응답하지 않았습니다"
  seed_summary; exit $?
fi

if ! SEED_TOKEN="$(operator_token demo-corp)"; then
  seed_fail "운영자 토큰(assume demo-corp)을 얻지 못했습니다"
  seed_summary; exit $?
fi
export SEED_TOKEN

# --- 백엔드 준비성 (TASK-MONO-537) -------------------------------------------
# 🔴 위 `wait_http` 는 **엣지 준비성만** 잰다 — 401/403 을 "살아 있다" 로 세기 때문에
# 뒤의 서비스에 대해서는 아무것도 증명하지 않는다(lib.sh 의 `wait_backend` 주석이 erp·scm·wms
# 세 도메인의 같은 사고를 이미 기록해 두었다). **finance 만 그 게이트가 없었다.**
#
# 실측 (2026-08-16, `demo-up.sh finance` 직후 자동 시드):
#   ✗ 계좌 A/B — HTTP 500 · ✗ KYC ×2 · ✗ 이체 생략 · ✗ 시산표 — HTTP 500  ⇒ 요약 `실패 6`
# 그 시각 account-service·ledger-service 는 `health: starting` 이었고, healthy 확인 후
# 같은 명령을 재실행하니 `생성 4 · 실패 0` 이었다 — 코드 결함이 아니라 **너무 일찍 물은 것**.
#
# 🔴 account-service 에는 **id 없이 2xx 를 내는 GET 이 없다**(`/{id}`, `/{id}/balances`,
# `/{id}/transactions` 뿐) ⇒ 2xx 를 요구하는 `wait_backend` 를 그대로 쓸 수 없다. 여기서
# 옳은 술어는 *"백엔드가 **답했는가**"* 다: 없는 id 에 대한 **404 는 도달의 증거**이고,
# 게이트웨이가 내는 **5xx(`Connection refused`)가 미도달의 증거**다. 그 둘을 가른다.
# 🔵 ledger-service 는 id 없는 2xx 엔드포인트가 있으므로 형제들과 같이 `wait_backend` 를 쓴다.
wait_backend_answered() { # <라벨> <url> [초] — 5xx/무응답이 아닐 때까지. 4xx 는 "답했다".
  local label="$1" url="$2" timeout="${3:-240}" i
  for (( i=0; i<timeout; i+=5 )); do
    http GET "$url" >/dev/null
    case "${SEED_LAST_STATUS:-000}" in 2??|4??) return 0 ;; esac
    sleep 5
  done
  seed_fail "$label 이 ${timeout}초 안에 응답하지 않았습니다 (마지막 HTTP ${SEED_LAST_STATUS:-none})"
  return 1
}

fin_ready=1
wait_backend_answered "account-service" "$FIN/api/finance/accounts/00000000-0000-0000-0000-000000000000" || fin_ready=0
wait_backend          "ledger-service"  "$FIN/api/finance/ledger/trial-balance" 120                       || fin_ready=0
if [ "$fin_ready" != 1 ]; then
  seed_fail "finance 백엔드가 준비되지 않았습니다 — 아래 항목은 시도하지 않습니다(빈 화면보다 이 줄이 낫다)"
  seed_summary; exit $?
fi

CURRENCY="KRW"
OWNER_A="demo-owner-a"
OWNER_B="demo-owner-b"

# --- 계좌 개설 ---------------------------------------------------------------
# 🔵 멱등: 고정 Idempotency-Key 를 쓰면 2회차는 서버가 **저장된 응답**을 돌려준다.
#
# 🔴 그래서 아래 "생성" 로그는 **요청 수**이지 생성 수가 아니다. replay 도 2xx 라
#    응답 코드로는 안 갈리고, 저장된 응답은 최초 시점 것이라(그때는 PENDING_KYC)
#    현재 상태로도 안 갈린다. scm 쪽은 postgres 라 POST 전후 행 수를 세어 갈랐지만
#    여기는 mysql 이고 자격증명이 컨테이너 env 안에 있어 같은 방식이 매끄럽지 않다.
#    ⇒ **수렴 증거는 로그가 아니라 행 수다.** 실측(2026-08-07): 여러 회 실행 후
#      `finance_db.accounts` = **2행**(둘 다 ACTIVE/FULL), `idempotency_keys` = 6행.
#      상태는 수렴한다 — 낙관적인 것은 라벨뿐이고, 이 주석이 그 차이를 기록한다.
#    (부수 관측: `owner_ref` 는 저장 시 암호화된다 — `v1:…` base64.)
open_account() {
  local label="$1" key="$2" owner="$3"
  ACCOUNT_ID=""
  if http POST "$FIN/api/finance/accounts" \
       "{\"ownerRef\":\"$owner\",\"currency\":\"$CURRENCY\",\"kycLevel\":\"BASIC\"}" \
       -H "Idempotency-Key: $key"; then
    ACCOUNT_ID="$(printf '%s' "$SEED_LAST_BODY" | grep -o '"[a-zA-Z]*[Ii]d":"[^"]*"' | head -1 | cut -d'"' -f4)"
    if [ -z "$ACCOUNT_ID" ]; then
      seed_fail "$label — 응답에서 계좌 id 를 못 뽑았다: ${SEED_LAST_BODY:0:160}"
      return 1
    fi
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  $label (id=${ACCOUNT_ID:0:12}…)"
    return 0
  fi
  seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  return 1
}

ACC_A=""; ACC_B=""
open_account "계좌 A (demo-owner-a)" "seed-fin-acc-a" "$OWNER_A" && ACC_A="$ACCOUNT_ID"
open_account "계좌 B (demo-owner-b)" "seed-fin-acc-b" "$OWNER_B" && ACC_B="$ACCOUNT_ID"

# --- KYC 승급 → ACTIVE -------------------------------------------------------
# 계좌는 `PENDING_KYC` 로 열리고 그 상태에서는 자금 이동이 409(`ACCOUNT_NOT_ACTIVE`)다.
# 승급이 `PENDING_KYC → ACTIVE` 를 만든다(실측: 200, status=ACTIVE).
# 🔵 `kycLevel:"BASIC"` 을 개설 요청에 넣어도 ACTIVE 가 되지는 않는다 — 별도 전이다.
activate() {
  local label="$1" acc="$2"
  [ -n "$acc" ] || { seed_fail "$label — 계좌 id 가 비어 있다"; return 1; }
  if http POST "$FIN/api/finance/accounts/$acc/kyc/upgrade" \
       '{"toLevel":"FULL","reason":"demo seed"}' -H "Idempotency-Key: seed-fin-kyc-$acc"; then
    local st; st="$(printf '%s' "$SEED_LAST_BODY" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)"
    if [ "$st" = "ACTIVE" ]; then
      SEED_CREATED=$((SEED_CREATED + 1)); seed_log "전이  $label → ACTIVE (확인)"
      return 0
    fi
    seed_fail "$label — 승급은 2xx 인데 status=$st (ACTIVE 아님)"
    return 1
  fi
  seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  return 1
}
activate "계좌 A KYC" "$ACC_A"
activate "계좌 B KYC" "$ACC_B"

# --- 입금 (자금 유입) --------------------------------------------------------
# 🔵 `POST /{id}/topups` 는 **운영자 전용**이다(TASK-FIN-BE-068). 이 시드는 이미 운영자
# 토큰으로 돌고 있으므로 그대로 통한다.
#
# 🔴 **응답 코드로는 갈 수 없다.** 고정 `Idempotency-Key` 를 쓰므로 2회차는 서버가
# **저장된 2xx 를 재생**하고, 재생된 본문은 최초 시점 것이다. 즉 "이번 실행이 돈을
# 넣었나" 는 200 으로 판별 불가능하다.
#
# 🔴🔴 **그렇다고 잔액을 술어로 쓸 수도 없다 — 첫 판이 그렇게 했고 틀렸다(TASK-MONO-556).**
# `after >= TOPUP_MINOR` 는 *"잔액은 입금 말고는 안 변한다"* 를 전제하는데 **바로 다음
# 단계인 이체 A→B 가 그 전제를 깬다**: A 는 400,000 으로 내려가고, 2회차 입금은 재생이라
# 잔액을 못 올린다 ⇒ **재시작마다 결정론적으로 실패**했다(= 한 번도 재실행된 적 없는
# 계좌에서만 통과). 지문은 **A 만 실패하고 B 는 통과하는 비대칭**이었다 — B 는 이체를
# *받아서* 600,000 이 되므로 실패 방향이 정확히 이체 방향과 일치했다.
# 🔵 답은 바로 아래 상수의 주석에 이미 있었다: 작성자는 이체가 잔액을 깎는 것을 **알고**
# 입금액을 그만큼 키웠는데, 같은 함수의 술어만 그 사실을 안 넣었다.
#
# ⇒ 술어를 **입금 사실 자체**로 옮긴다: `GET /{id}/transactions?type=TOPUP` 에 이 시드가
# 넣은 금액의 TOPUP 거래가 **존재하는가**. 잔액이 나중에 어떻게 쓰이든 무관하고 시드 내부
# 순서에 결합하지 않는다 — **이체 금액을 바꿔도 안 깨진다**. 잔액은 계속 읽지만 그건
# 판정이 아니라 *생성/재생* 을 가르는 **보고용**이다. 그 둘을 한 값에 겹쳐 쓴 것이 결함이었다.
ledger_of() { # <account> — ledger 잔액(minor). 못 읽으면 빈 문자열
  http GET "$FIN/api/finance/accounts/$1/balances" >/dev/null || { printf ''; return 1; }
  printf '%s' "$SEED_LAST_BODY" | grep -o '"ledger":"[0-9]*"' | head -1 | cut -d'"' -f4
}

# topup_txns <account> <amount-minor> — 그 금액의 TOPUP 거래 **건수**.
# 🔴 조회가 실패하면 빈 문자열이다 — **"0건" 이 아니라 "판정 불가"**. 그 둘을 섞으면
# 계측 실패가 "입금이 없다" 로 읽힌다.
# 🔴 `json_objects` 로 **객체 단위**로 자른 뒤 한 줄 안에서 두 조건을 본다. 통짜 grep 은
# 서로 다른 거래의 필드를 합쳐 **키메라 행**을 만든다(TRANSFER 의 type + TOPUP 의 금액).
topup_txns() {
  http GET "$FIN/api/finance/accounts/$1/transactions?type=TOPUP&size=100" >/dev/null \
    || { printf ''; return 1; }
  json_objects "$SEED_LAST_BODY" \
    | grep -F '"type":"TOPUP"' | grep -cF "\"amount\":\"$2\""
}

TOPUP_MINOR=500000   # ₩5,000.00 — 이체 100,000 + 홀드 여유
topup() {
  local label="$1" acc="$2" before after txns
  [ -n "$acc" ] || { seed_fail "$label — 계좌 id 가 비어 있다"; return 1; }
  before="$(ledger_of "$acc")"
  if ! http POST "$FIN/api/finance/accounts/$acc/topups" \
       "{\"money\":{\"amount\":\"$TOPUP_MINOR\",\"currency\":\"$CURRENCY\"},\"reason\":\"데모 시드 입금\"}" \
       -H "Idempotency-Key: seed-fin-topup-$acc"; then
    seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
    return 1
  fi
  txns="$(topup_txns "$acc" "$TOPUP_MINOR")"
  if [ -z "$txns" ]; then
    seed_fail "$label — 입금은 2xx 인데 거래내역을 읽지 못했다(판정 불가)"
    return 1
  fi
  if [ "$txns" -lt 1 ] 2>/dev/null; then
    # 🔴 2xx 를 받고도 TOPUP 거래가 0건 = 입금이 성립하지 않았다. 이 시드가 지키는 회귀
    # (`INSUFFICIENT_AVAILABLE_BALANCE`)의 상류가 정확히 여기다.
    seed_fail "$label — 입금이 2xx 인데 $TOPUP_MINOR $CURRENCY TOPUP 거래가 0건이다"
    return 1
  fi
  after="$(ledger_of "$acc")"
  if [ -n "$after" ] && [ "$after" != "$before" ]; then
    SEED_CREATED=$((SEED_CREATED + 1))
    seed_log "생성  $label — ledger $before → $after (TOPUP 거래 ${txns}건)"
  else
    SEED_EXISTING=$((SEED_EXISTING + 1))
    seed_log "기존  $label — TOPUP 거래 ${txns}건 확인 (재생, 이중 입금 없음)"
  fi
  return 0
}

topup "계좌 A 입금" "$ACC_A"
topup "계좌 B 입금" "$ACC_B"

# --- 이체 (→ 원장 이벤트) ----------------------------------------------------
# 이체가 원장 전기를 낳고, 그 전기가 ledger_account 를 자동 생성한다.
# 🔴 첫 판은 `409|422 → "존재"` 로 뭉갰다. 첫 실행에는 이미 존재할 것이 없는데도 초록으로
# 보였다 — 실제 409 는 `ACCOUNT_NOT_ACTIVE`(승급 전)였고 422 는 잔액 부족이다.
# status 코드를 상태로 번역하지 않는다: **본문의 code 로 갈라 판정**한다.
#
# 🔵 그 잔액 부족은 2026-08-07 까지 **입금 경로가 아예 없어서** 구조적으로 풀 수 없었고,
# 이 스크립트는 그 사실을 로그로 설명하고 넘어갔다. `TASK-FIN-BE-068` 이 `POST /{id}/topups`
# 를 열면서 그 서술은 **거짓이 됐는데 9일 동안 인쇄되고 있었다**(TASK-MONO-537 이 그것을
# 걷어냈다). 이제 `INSUFFICIENT_AVAILABLE_BALANCE` 는 관측이 아니라 **회귀**다 — 입금이
# 먼저 성공했는데도 잔액이 모자란다는 뜻이기 때문이다.
TRANSFER_MINOR=100000
if [ -n "$ACC_A" ] && [ -n "$ACC_B" ]; then
  b_before="$(ledger_of "$ACC_B")"
  if http POST "$FIN/api/finance/accounts/$ACC_A/transfers" \
       "{\"toAccountId\":\"$ACC_B\",\"money\":{\"amount\":\"$TRANSFER_MINOR\",\"currency\":\"$CURRENCY\"},\"reason\":\"데모 이체\"}" \
       -H "Idempotency-Key: seed-fin-transfer-1"; then
    # 🔴 2xx 로 판정하지 않는다 — **양쪽 잔액을 다시 읽는다**(위 입금과 같은 이유).
    a_after="$(ledger_of "$ACC_A")"; b_after="$(ledger_of "$ACC_B")"
    if [ -z "$a_after" ] || [ -z "$b_after" ]; then
      seed_fail "이체 A→B — 2xx 인데 잔액을 다시 읽지 못했다"
    elif [ "$b_after" != "$b_before" ]; then
      SEED_CREATED=$((SEED_CREATED + 1))
      seed_log "생성  이체 A→B $TRANSFER_MINOR $CURRENCY (A=$a_after · B=$b_before→$b_after)"
    else
      SEED_EXISTING=$((SEED_EXISTING + 1))
      seed_log "기존  이체 A→B (재생 — 잔액 불변 A=$a_after B=$b_after)"
    fi
  elif printf '%s' "$SEED_LAST_BODY" | grep -q 'INSUFFICIENT_AVAILABLE_BALANCE'; then
    seed_fail "이체 A→B — 입금이 선행했는데도 잔액 부족이다(회귀): ${SEED_LAST_BODY:0:160}"
  elif printf '%s' "$SEED_LAST_BODY" | grep -q 'ACCOUNT_NOT_ACTIVE'; then
    seed_fail "이체 A→B — 계좌가 ACTIVE 가 아니다(KYC 승급이 안 먹었다): ${SEED_LAST_BODY:0:160}"
  else
    seed_fail "이체 A→B — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  fi
else
  seed_fail "이체 생략 — 계좌 id 가 비어 있다 (A='${ACC_A:0:8}' B='${ACC_B:0:8}')"
fi

# --- 검증: 화면이 읽는 것을 그대로 읽는다 ------------------------------------
verify_get() {
  local label="$1" url="$2" marker="$3"
  if http GET "$url"; then
    if printf '%s' "$SEED_LAST_BODY" | grep -q "$marker"; then
      seed_log "확인  $label"
    else
      seed_log "관측  $label — 200 인데 '$marker' 없음 (본문 ${#SEED_LAST_BODY}B)"
    fi
  else
    seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"
  fi
}

if [ -n "$ACC_A" ]; then
  verify_get "계좌 A 조회"       "$FIN/api/finance/accounts/$ACC_A"              '"'
  verify_get "계좌 A 잔액"       "$FIN/api/finance/accounts/$ACC_A/balances"     '"'
  verify_get "계좌 A 거래내역"   "$FIN/api/finance/accounts/$ACC_A/transactions" '"'
fi

# 🔵 원장은 **투영 결과**다. 이체 직후엔 아직 안 왔을 수 있으므로 잠깐 기다린다.
await_ledger() {
  local i
  for (( i=0; i<60; i+=5 )); do
    if http GET "$FIN/api/finance/ledger/trial-balance"; then
      # 🔴 필드명은 `ledgerAccountCode` 다 — `accountCode` 로 세면 실제로 찬 시산표를
      #    "비었다" 로 읽는다(TASK-MONO-537 발굴 중 실제로 그 오답을 냈다).
      if printf '%s' "$SEED_LAST_BODY" | grep -q '"ledgerAccountCode"'; then
        seed_log "확인  원장 시산표에 계정이 있다 (이체 이벤트의 투영 동작)"
        return 0
      fi
    else
      seed_fail "시산표 조회 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"
      return 1
    fi
    sleep 5
  done
  # 🔴 **이제는 "예상된 결과" 가 아니다.** 이 자리의 옛 문구는 *"입금 경로가 없어서 전기가
  # 없다 — 코드 결함이 아니라 미구현"* 이었는데, `TASK-FIN-BE-068`(2026-08-07)이 입금을
  # 열었고 위 단계가 그것을 실제로 태운다. 입금·이체가 성립했는데 전기가 없으면 그건 결함이다.
  seed_fail "원장 시산표 — 200 인데 60초 동안 계정 0건. 입금·이체가 성립했으므로 전기가 따라와야 한다"
  seed_log  "      갈라 볼 것 ①브로커: finance.transaction.completed.v1 과 그 .DLT 의 offset"
  seed_log  "      ②테넌트: journal_line.tenant_id 가 계좌의 tenant_id 와 같은지"
  seed_log  "        (FIN-BE-068 에서 봉투에 tenantId 가 없어 원장이 리터럴 'finance' 로"
  seed_log  "         폴백했고, 발행 3·전기 3·DLT 0 인데 화면만 비는 모양이었다)"
  return 1
}
await_ledger

seed_summary
