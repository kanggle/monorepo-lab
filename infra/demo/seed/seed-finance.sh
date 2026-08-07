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

# --- 이체 (→ 원장 이벤트) ----------------------------------------------------
# 이체가 원장 전기를 낳고, 그 전기가 ledger_account 를 자동 생성한다.
# 🔴 첫 판은 `409|422 → "존재"` 로 뭉갰다. 첫 실행에는 이미 존재할 것이 없는데도 초록으로
# 보였다 — 실제 409 는 `ACCOUNT_NOT_ACTIVE`(승급 전)였고 422 는 아래의 잔액 부족이다.
# status 코드를 상태로 번역하지 않는다: **본문의 code 로 갈라 판정**한다.
if [ -n "$ACC_A" ] && [ -n "$ACC_B" ]; then
  if http POST "$FIN/api/finance/accounts/$ACC_A/transfers" \
       "{\"toAccountId\":\"$ACC_B\",\"money\":{\"amount\":\"100000\",\"currency\":\"$CURRENCY\"},\"reason\":\"데모 이체\"}" \
       -H "Idempotency-Key: seed-fin-transfer-1"; then
    SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  이체 A→B 1,000.00 $CURRENCY"
  elif printf '%s' "$SEED_LAST_BODY" | grep -q 'INSUFFICIENT_AVAILABLE_BALANCE'; then
    # ── 이 도메인의 구조적 벽 (실측 2026-08-07) ──────────────────────────────
    # 계좌는 잔액 0 으로 열리고, **입금 경로가 API 에 없다**:
    #   · account-service 컨트롤러에 deposit/topup/credit 매핑 0건
    #   · 애플리케이션에 `topUp(actor, accountId, amountMinor)` 이 있으나
    #     "internal funding (v1 stub)" 이고 **프로덕션 호출자 0건**(테스트에서만 6회)
    #   · `@KafkaListener` 0건 — 다른 도메인 이벤트로 들어오지도 않는다
    # ⇒ 이체·홀드·캡처는 영원히 불가능하고, 자금 이동이 없으니 `/ledger` 도 영원히 빈다.
    #   이것은 시드의 한계가 아니라 **제품의 갭**이다. 후속 티켓 후보로 적는다.
    seed_log "관측  이체 A→B 불가 — 잔액 0, 그리고 입금 API 가 존재하지 않는다"
    seed_log "      topUp() 은 있으나 프로덕션 호출자 0건(테스트 전용), Kafka 리스너 0건"
    seed_log "      ⇒ 자금 이동이 불가능하므로 /ledger 는 이 스택에서 채울 수 없다"
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
  for (( i=0; i<15; i+=5 )); do
    if http GET "$FIN/api/finance/ledger/trial-balance"; then
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
  seed_log "관측  원장 시산표 — 200 인데 15초 동안 계정 0건"
  seed_log "      예상된 결과다: 자금 이동이 한 건도 성립하지 않았으므로(위 관측) 전기가 없다."
  seed_log "      /ledger 를 채우려면 입금 경로가 먼저 있어야 한다 — 코드 결함이 아니라 미구현이다"
  return 2
}
await_ledger

seed_summary
