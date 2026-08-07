# TASK-FIN-BE-068 — `seed-finance.sh` 패치 (인계)

**왜 파일이 아니라 패치인가**: 이 작업 세션에서 `infra/demo/seed/seed-finance.sh` 에 대한
편집이 **분류기에 하드 차단**됐다. 3줄짜리 로그 문구 수정도 같은 차단을 받았으므로
diff 크기나 내용이 아니라 **파일 단위** 차단으로 보인다. 저장소 규칙(`platform/git-workflow-policy.md`)에
따라 shell 로 우회하지 않고 패치를 인계한다.

**이 패치가 검증하려는 것은 이미 전부 실측됐다** — 시드가 아니라 프로브(scratchpad)로
같은 순서를 태워 확인했다. 아래 § 실측값. 그러므로 이 패치는 *증명*이 아니라 *데모 재현성*이
목적이다.

적용 후 `bash infra/demo/seed/seed-finance.sh` 를 두 번 돌려 `기존` 카운트로 수렴을 확인하면
AC-5 가 닫힌다.

---

## 1) 교체 — `# --- 이체 (→ 원장 이벤트) ---` 블록 전체

아래 블록(현재 파일의 `# --- 이체 (→ 원장 이벤트) ---` 주석부터 그 `fi` 까지)을 통째로
다음으로 바꾼다.

```bash
# --- 입금 (자금 유입) --------------------------------------------------------
# 🔵 `POST /{id}/topups` 는 **운영자 전용** 이다(TASK-FIN-BE-068). 이 시드는 이미
# 운영자 토큰으로 도는 중이므로 그대로 통한다.
#
# 🔴 **잔액을 다시 읽어서 판정한다 — 응답 코드로는 갈 수 없다.** 고정
# `Idempotency-Key` 를 쓰므로 2회차는 서버가 **저장된 2xx 를 재생**하고, 재생된 응답은
# 첫 실행 때의 본문 그대로다. 즉 "이번 실행이 돈을 넣었나" 는 200 으로 판별 불가능하다.
# scm 에서 같은 함정을 상태 이력 행 수로 갈랐고(SCM-BE-060), 여기서는 **잔액 자체**가
# 그 술어다: 목표 이상이면 도달, 미만이면 실패. 재생이든 최초든 결론이 같다.
ledger_of() { # <account> — ledger 잔액(minor). 못 읽으면 빈 문자열
  http GET "$FIN/api/finance/accounts/$1/balances" >/dev/null || { printf ''; return 1; }
  printf '%s' "$SEED_LAST_BODY" | grep -o '"ledger":"[0-9]*"' | head -1 | cut -d'"' -f4
}

TOPUP_MINOR=500000   # ₩500,000 — 이체 100,000 + 홀드 여유
topup() {
  local label="$1" acc="$2" want="$3" before after
  [ -n "$acc" ] || { seed_fail "$label — 계좌 id 가 비어 있다"; return 1; }
  before="$(ledger_of "$acc")"
  if ! http POST "$FIN/api/finance/accounts/$acc/topups" \
       "{\"money\":{\"amount\":\"$TOPUP_MINOR\",\"currency\":\"$CURRENCY\"},\"reason\":\"데모 시드 입금\"}" \
       -H "Idempotency-Key: seed-fin-topup-$acc"; then
    seed_fail "$label — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
    return 1
  fi
  after="$(ledger_of "$acc")"
  if [ -z "$after" ]; then
    seed_fail "$label — 입금은 2xx 인데 잔액을 다시 읽지 못했다"
    return 1
  fi
  if [ "$after" -ge "$want" ] 2>/dev/null; then
    if [ "$after" != "$before" ]; then
      SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  $label — ledger $before → $after"
    else
      SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "기존  $label — ledger $after (재생, 이중 입금 없음)"
    fi
    return 0
  fi
  seed_fail "$label — 입금 후 ledger=$after 로 목표 $want 미만이다"
  return 1
}

topup "계좌 A 입금" "$ACC_A" "$TOPUP_MINOR"
topup "계좌 B 입금" "$ACC_B" "$TOPUP_MINOR"

# --- 이체 (→ 원장 이벤트) ----------------------------------------------------
# 이체가 원장 전기를 낳고, 그 전기가 ledger_account 를 자동 생성한다.
# 🔴 첫 판은 `409|422 → "존재"` 로 뭉갰다. 첫 실행에는 이미 존재할 것이 없는데도 초록으로
# 보였다 — 실제 409 는 `ACCOUNT_NOT_ACTIVE`(승급 전)였고 422 는 그 다음에 드러난 잔액
# 부족이었다. status 코드를 상태로 번역하지 않는다: **본문의 code 로 갈라 판정**한다.
#
# 🔵 그 잔액 부족은 2026-08-07 까지 **입금 경로가 아예 없어서** 구조적으로 풀 수 없었다
# (컨트롤러 매핑 0건 · `topUp()` 프로덕션 호출자 0건 · `@KafkaListener` 0건).
# TASK-FIN-BE-068 이 `POST /{id}/topups` 를 열었고 위 입금 단계가 그 선행이다. 아래
# `INSUFFICIENT_AVAILABLE_BALANCE` 갈래는 **이제 관측이 아니라 실패**다 — 입금이 먼저
# 성공했는데도 잔액이 모자라면 그건 회귀다.
TRANSFER_MINOR=100000
if [ -n "$ACC_A" ] && [ -n "$ACC_B" ]; then
  b_before="$(ledger_of "$ACC_B")"
  if http POST "$FIN/api/finance/accounts/$ACC_A/transfers" \
       "{\"toAccountId\":\"$ACC_B\",\"money\":{\"amount\":\"$TRANSFER_MINOR\",\"currency\":\"$CURRENCY\"},\"reason\":\"데모 이체\"}" \
       -H "Idempotency-Key: seed-fin-transfer-1"; then
    # 🔴 AC-2: 2xx 로 판정하지 않는다 — **양쪽 잔액을 다시 읽는다**.
    a_after="$(ledger_of "$ACC_A")"; b_after="$(ledger_of "$ACC_B")"
    if [ -z "$a_after" ] || [ -z "$b_after" ]; then
      seed_fail "이체 A→B — 2xx 인데 잔액을 다시 읽지 못했다"
    elif [ "$b_after" != "$b_before" ]; then
      SEED_CREATED=$((SEED_CREATED + 1))
      seed_log "생성  이체 A→B $TRANSFER_MINOR $CURRENCY (A=$a_after B=$b_before→$b_after)"
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
```

## 2) 교체 — `await_ledger` 의 실패 문구

현재 마지막 세 줄이 *"예상된 결과다 … 코드 결함이 아니라 미구현이다"* 라고 적혀 있다.
입금 경로가 생겼으므로 **그 문장은 이제 거짓**이다. 다음으로 바꾼다.

```bash
  seed_log "관측  원장 시산표 — 200 인데 15초 동안 계정 0건"
  seed_log "      🔴 이제는 예상된 결과가 아니다 — 입금과 이체가 성립했다면 전기가 따라와야 한다"
  seed_log "      갈라 볼 것: 브로커 offset(finance.transaction.completed.v1 / .DLT)과"
  seed_log "      journal_line.tenant_id 가 계좌의 tenant_id 와 같은지(FIN-BE-068 참조)"
  return 2
```

---

## 실측값 (2026-08-07, 로컬 finance 스택 7컨테이너 healthy)

| 단계 | 결과 |
|---|---|
| `topup A` | HTTP 200, ledger **0 → 500000** |
| `topup B` | HTTP 200, ledger **0 → 500000** |
| `transfer A→B 100000` | HTTP 200, A **500000 → 400000**, B **500000 → 600000** |
| `trial-balance` | **3계정**, t=5s, DR 합 = CR 합 = **1,100,000** |
| 동일 키 `topup` 재실행 | HTTP 200, ledger **400000 → 400000** (불변 — 이중 입금 없음) |
| 콘솔 `/ledger` BFF | trial-balance **array=3**, periods 0, discrepancies 0, fx-rates no-array(503) |
