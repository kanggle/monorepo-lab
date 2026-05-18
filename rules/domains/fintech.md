# Domain: fintech

> **Activated when**: `PROJECT.md` declares `domain: fintech`.

---

## Scope

Financial Services (비은행 금융 일반) — 송금·지갑·계좌·거래·KYC·리스크·컴플라이언스를 다루는 금융 제품. 핵심은 **자금이 움직이는 모든 연산의 정확성·멱등성·추적가능성** — 같은 요청이 두 번 들어와도 자금은 한 번만 이동하고, 모든 잔액 변동은 불변 감사 기록을 남기며, 규제(KYC/AML)가 자금 이동의 선행 게이트가 된다.

이 도메인은 "누가 얼마를 어디로 옮겼고, 그 결과 잔액이 어떻게 되었으며, 그것이 규제·회계상 일관적인가"에 집중한다. 전통 은행 기간계(`banking` — 인가 전제 core banking/예금/대출)는 스코프 밖이고, 카드사·가맹점 사이 결제 중계(`pg` — acquirer 연동/토큰화/chargeback)도 스코프 밖이며, 시장 주문/체결/포지션(`securities`)도 스코프 밖이다. fintech 는 그 셋이 아닌 **금융 제품 자체**(송금/지갑/계좌)가 핵심일 때의 도메인이다.

복식부기 원장(double-entry ledger)·총계정원장(GL)·매입채무(AP) 같은 회계 깊이는 fintech 의 *확장(ledger v2)* 영역이다 — 본 도메인 룰은 v1 의 계좌·잔액·거래·KYC 를 우선 강제하고, ledger 차원은 F2/F3 에서 forward-declare 만 한다. 실제 ledger 서비스 도입 시 본 파일에 규칙을 추가한다.

---

## Bounded Contexts (표준)

fintech 도메인 프로젝트가 일반적으로 가지는 bounded context 묶음. 실제 서비스 분할은 트래픽·소유권·규제 경계에 따라 달라진다.

| Bounded Context | 책임 |
|---|---|
| **Account** | 계좌 생성·상태기계(개설/제한/동결/해지), KYC 단계 추적, 잔액 보유(hold)·해제(release), 가용잔액 vs 장부잔액 분리 |
| **Wallet / Balance** | 사용자별 잔액 보관, 잔액 변동의 단일 진입점, hold/release 의 원자적 적용 |
| **Transaction** | 송금·충전·출금·이체 등 자금 이동 단위. 멱등 처리, 상태기계(요청→검증→승인→정산→완료/실패/반전) |
| **KYC / Compliance** | 신원확인 단계, AML 스크리닝, 제재 목록(sanction) 대조, 거래 한도, 의심거래 보고 게이트 |
| **Ledger (v2 확장)** | 복식부기 분개, 차변/대변 균형, 기간 마감, GL/AP feed — forward-declared, v1 미구현 |
| **Reconciliation** | 내부 장부 ↔ 외부(은행/PG/카드사) 정산내역 대조, 불일치 분류 |
| **Audit / Operations** | 자금 영향 연산의 불변 감사 기록, 운영자 검토 큐(reconciliation 불일치·KYC 보류·고위험 거래), 한도/정책 설정 |

각 context 는 자체 데이터 저장소를 가지며, context 간 통신은 **이벤트** 또는 **잘 정의된 내부 HTTP** 로만 이루어진다. 외부 금융기관/규제 시스템 통합은 반드시 adapter layer 를 거친다.

---

## Ubiquitous Language

- **Account** — 자금을 보유하는 계좌. 상태기계: `PENDING_KYC → ACTIVE → (RESTRICTED →) (FROZEN →) CLOSED`. KYC 미완료 계좌는 자금 이동 불가.
- **Ledger Balance (장부 잔액)** — 확정된 거래만 반영한 잔액.
- **Available Balance (가용 잔액)** — 장부 잔액에서 미확정 hold 를 차감한, 실제 사용 가능한 잔액.
- **Hold (보유/예치)** — 자금을 잠그되 아직 이동시키지 않은 상태. 승인되면 capture, 만료/취소되면 release.
- **Capture** — hold 된 자금을 실제로 차감/이동 확정.
- **Release** — hold 해제 (거래 취소·만료). 자금은 가용 잔액으로 복귀.
- **Transaction** — 자금 이동 단위. 상태기계: `REQUESTED → VALIDATED → AUTHORIZED → SETTLED → COMPLETED`, 실패 분기 `FAILED`, 보정 분기 `REVERSED`.
- **Idempotency Key** — 동일 자금 이동 요청의 중복을 막는 클라이언트 제공 키. 같은 키 재요청은 최초 결과를 반환(재실행 금지).
- **KYC (Know Your Customer)** — 신원확인 단계. 단계별로 허용 거래 한도/유형이 달라짐.
- **AML (Anti-Money-Laundering)** — 자금세탁방지 스크리닝. 거래 전·후 sanction/watchlist 대조.
- **Sanction / Watchlist** — 제재·감시 대상 목록. 매칭 시 거래 차단 + 운영자 큐 진입.
- **Minor Units** — 통화 최소 단위 정수 표현 (예: KRW 1 = 1, USD 1.00 = 100 cents). 금액은 이 정수 또는 `BigDecimal` 로만 표현.
- **Double-entry (복식부기)** — 모든 자금 변동을 차변·대변 한 쌍으로 기록, 합이 0 (v2 ledger 확장의 핵심 불변식).
- **Reconciliation** — 내부 장부와 외부 정산내역의 대조.
- **Discrepancy** — reconciliation 불일치. 자동 종결 금지, 운영자 검토 큐로.
- **Reversal (반전 거래)** — 잘못된 거래를 취소하는 별도 보정 거래. 원거래는 immutable, 반전으로만 정정.
- **Audit Trail** — 자금/상태에 영향을 준 모든 연산의 actor + timestamp + before/after 불변 기록.

이 용어들은 코드·API·문서에서 일관되게 사용되어야 한다.

---

## Standard Error Codes

fintech 도메인에서 공통으로 발생하는 에러는 [../../platform/error-handling.md](../../platform/error-handling.md) 의 전역 카탈로그에 등록되어야 한다. 본 도메인 특유의 코드:

### Account
- `ACCOUNT_NOT_FOUND` — 존재하지 않는 계좌
- `ACCOUNT_NOT_ACTIVE` — 비활성(개설대기/제한/동결/해지) 계좌에 자금 연산 시도
- `ACCOUNT_FROZEN` — 동결 계좌 자금 이동 시도
- `ACCOUNT_STATUS_TRANSITION_INVALID` — 허용되지 않는 계좌 상태 전이

### Balance / Transaction
- `INSUFFICIENT_AVAILABLE_BALANCE` — 가용 잔액 부족
- `HOLD_NOT_FOUND` — 존재하지 않는 hold 의 capture/release 시도
- `HOLD_ALREADY_SETTLED` — 이미 확정/해제된 hold 재처리 시도
- `TRANSACTION_NOT_FOUND` — 존재하지 않는 거래
- `TRANSACTION_STATUS_TRANSITION_INVALID` — 허용되지 않는 거래 상태 전이
- `TRANSACTION_ALREADY_SETTLED` — 확정된 거래의 mutation 시도 (반전으로만 정정)
- `IDEMPOTENCY_KEY_CONFLICT` — 동일 idempotency key 의 본문이 최초 요청과 다름
- `CURRENCY_MISMATCH` — 통화 불일치 연산
- `AMOUNT_INVALID` — 0 이하·소수 정밀도 위반·정수 minor-units 위반 금액

### KYC / Compliance
- `KYC_REQUIRED` — KYC 미완료 상태에서 자금 이동 시도
- `KYC_LEVEL_INSUFFICIENT` — 현재 KYC 단계가 요청 거래 한도/유형 미충족
- `AML_SCREENING_REQUIRED` — AML 스크리닝 미통과 거래 진행 시도
- `SANCTION_HIT` — 제재/감시 목록 매칭 (거래 차단, 운영자 큐)
- `TRANSACTION_LIMIT_EXCEEDED` — KYC 단계·정책상 한도 초과

### Reconciliation
- `RECONCILIATION_DISCREPANCY` — 내부 장부 ↔ 외부 정산 불일치
- `RECONCILIATION_PERIOD_LOCKED` — 잠긴 정산 기간 mutation 시도

### Ledger (v2 확장 — forward-declared)
- `LEDGER_ENTRY_UNBALANCED` — 차변·대변 합 ≠ 0 (복식부기 불변식 위반)
- `LEDGER_PERIOD_CLOSED` — 마감된 회계 기간 분개 시도

### Cross
- `PERMISSION_DENIED` — 운영자/계정 권한 없음

---

## Integration Boundaries

### 외부 (플랫폼 경계 바깥)

- **은행 / 결제망 / 송금 파트너** — 충전·출금·송금의 실제 자금 이동. **idempotency key** + **circuit breaker** + **타임아웃 분리** 필수. 응답 불명확 시 낙관적 확정 금지(미정 상태 유지 + reconciliation 으로 수렴).
- **KYC / 신원확인 제공사** — 문서 검증, 생체, 본인확인 결과 수신.
- **AML / 제재목록 제공사** — sanction/watchlist 스크리닝, 의심거래 모니터링 피드.
- **회계 / ERP (out-bound, v2)** — 정산 결과의 GL 분개 feed (ledger v2 확장 시).
- **알림 채널** — 거래 완료/실패, KYC 상태 변경, 한도 초과, 의심거래 운영 알림.

### 내부 (같은 프로젝트 내 다른 서비스)

- gateway → 모든 service: OIDC token 검증, tenant claim, `X-Account-Id` 헤더 전파.
- transaction → account/wallet: hold/capture/release 시 잔액 연산.
- transaction → KYC/compliance: 자금 이동 전 KYC 단계·AML·한도 게이트 통과 확인.
- reconciliation → transaction/ledger: 내부 기록 조회 후 외부 정산내역과 대조.
- (v2) ledger ← transaction: 확정 거래의 분개 생성.

### 내부 이벤트 카탈로그 (권장)

- `<prefix>.account.opened` / `.kyc.upgraded` / `.restricted` / `.frozen` / `.closed`
- `<prefix>.balance.held` / `.captured` / `.released`
- `<prefix>.transaction.requested` / `.authorized` / `.settled` / `.completed` / `.failed` / `.reversed`
- `<prefix>.compliance.screening.completed` / `.sanction.hit` / `.suspicious.flagged`
- `<prefix>.reconciliation.completed` / `.discrepancy.detected`
- `<prefix>.ledger.entry.posted` / `.period.closed` (v2)

---

## Mandatory Rules

### F1. 자금 영향 연산은 멱등 + 트랜잭션 보호
모든 자금 이동(충전/출금/송금/hold/capture/release)은 (a) client-supplied idempotency key 로 멱등 — 동일 키 재요청은 최초 결과를 그대로 반환하고 자금을 재차 이동시키지 않는다, (b) 잔액 변경 + 거래 상태 전이 + 이벤트 발행이 한 트랜잭션(outbox 패턴) 안에서. 부분 적용된 자금 상태 발생 절대 금지.

### F2. 잔액 무결성 — 가용/장부 분리 + (forward-decl) 복식부기
가용 잔액 = 장부 잔액 − 미확정 hold 합. 음수 가용 잔액으로의 자금 이동 금지. 잔액은 한 곳(wallet/balance context)에서만 변경한다. ledger v2 도입 시: 모든 잔액 변동은 차변·대변 한 쌍의 분개로 기록되고 분개 합은 항상 0 이어야 한다(`LEDGER_ENTRY_UNBALANCED` 가드).

### F3. 거래는 확정 후 immutable — 정정은 반전 거래로만
SETTLED/COMPLETED 거래의 금액·당사자·결과는 수정할 수 없다. 오류는 원거래를 건드리지 않고 별도의 reversal(보정) 거래로만 정정한다. 원거래와 반전 거래는 상호 참조로 연결되고 둘 다 감사 기록에 남는다.

### F4. KYC/AML 컴플라이언스 게이트는 자금 이동의 선행 조건
자금이 이동하기 전에 (a) 계좌 KYC 단계가 요청 거래 유형/한도를 충족하는지, (b) AML 스크리닝(sanction/watchlist)을 통과했는지 검증한다. 미충족 시 거래는 진행되지 않고 `KYC_*` / `AML_*` / `SANCTION_HIT` 으로 거부 + (sanction hit 은) 운영자 큐 진입. 컴플라이언스 게이트를 우회하는 자금 경로 금지.

### F5. 금액은 정수 minor-units 또는 BigDecimal — float/double 절대 금지
모든 금액은 통화 최소 단위 정수(long minor-units) 또는 `BigDecimal` 로만 표현·연산·저장한다. `float`/`double` 로 금액을 표현하거나 중간 계산하는 것은 금지(반올림 오차 = 자금 누락/과다). 통화 코드는 금액과 항상 함께 다루고, 통화 불일치 연산은 `CURRENCY_MISMATCH` 로 거부.

### F6. 자금/상태 영향 연산은 불변 감사 기록
계좌 상태 전이, 잔액 변동, 거래 확정/반전, KYC 단계 변경, 한도/정책 변경, 정산 기간 lock 등 모든 자금·규제 영향 연산은 actor + timestamp + before/after + 사유를 불변(append-only) 감사 저장소에 기록한다. 감사 기록의 사후 수정·삭제 금지. (`audit-heavy` trait 와 결합 시 외부 보존 규제 추가.)

### F7. 규제 대상 PII / 금융 식별자는 암호화 + 최소 노출
계좌번호, 신원확인 문서, 외부 금융기관 자격증명, KYC 원본 데이터 등은 secrets manager / DB 컬럼 암호화로 보관한다. 로그·이벤트·에러 응답에 평문 노출 금지(마스킹). 평문 저장·하드코딩 금지.

### F8. Reconciliation 불일치는 자동 close 금지
내부 장부와 외부(은행/PG/파트너) 정산내역 대조에서 금액/건수 불일치가 감지되면 운영자 검토 큐에 진입한다. 자동으로 정산을 종결하거나 차액을 임의 조정하지 말 것 — 자금 누락/과다·회계 부정합 위험. 잠긴 정산 기간의 데이터는 immutable, 보정은 다음 기간 보정 거래로.

---

## Forbidden Patterns

- ❌ **금액을 `float`/`double` 로 표현·계산·저장** (F5 위반) — 정수 minor-units / `BigDecimal` 만.
- ❌ **자금 이동에 idempotency key 누락** (F1 위반) — 재시도 시 이중 인출/이중 송금.
- ❌ **KYC/AML 게이트 우회 자금 경로** (F4 위반) — 컴플라이언스 미통과 이동.
- ❌ **SETTLED/COMPLETED 거래의 in-place mutation** (F3 위반) — 반전 거래로만 정정.
- ❌ **음수 가용 잔액 허용 / 잔액을 여러 곳에서 변경** (F2 위반).
- ❌ **reconciliation 불일치 자동 close / 차액 임의 조정** (F8 위반).
- ❌ **계좌번호·KYC 원본·외부 자격증명을 평문 저장/로그/이벤트 노출** (F7 위반).
- ❌ **외부 자금 이동의 불명확 응답을 성공으로 낙관 확정** — 미정 상태 유지 + reconciliation 수렴.
- ❌ **자금/규제 영향 연산의 감사 기록 누락 또는 사후 수정** (F6 위반).
- ❌ **(v2) 차변·대변 불균형 분개 허용** (F2 ledger 확장 위반).

---

## Required Artifacts

1. **계좌 상태 다이어그램** — `PENDING_KYC → ACTIVE → (RESTRICTED →) (FROZEN →) CLOSED`. 위치: `specs/services/<account-service>/state-machines/account-status.md`.
2. **거래 상태 다이어그램** — `REQUESTED → VALIDATED → AUTHORIZED → SETTLED → COMPLETED` (+ `FAILED` / `REVERSED`). 위치: `specs/services/<transaction-or-account-service>/state-machines/transaction-status.md`.
3. **잔액 모델** — 가용 vs 장부 잔액, hold/capture/release 의미와 원자성 규칙. 위치: `specs/services/<wallet-or-account-service>/data-model.md` 또는 `knowledge/architecture/balance-model.md`.
4. **KYC/AML 컴플라이언스 게이트 흐름** — KYC 단계별 허용 한도/유형, AML 스크리닝 시점, sanction hit 처리. 위치: `specs/services/<compliance-or-account-service>/workflows/compliance-gate.md`.
5. **Reconciliation 흐름** — 내부 ↔ 외부 매칭 알고리즘과 discrepancy 분류·운영자 큐. 위치: `specs/services/<reconciliation-or-account-service>/workflows/reconciliation.md`.
6. **(v2) Ledger / 복식부기 모델** — 분개 구조, 균형 불변식, 기간 마감 정책. 위치: `specs/services/<ledger-service>/data-model.md` (ledger v2 도입 시).
7. **에러 코드 등록** — 위 Standard Error Codes 가 [../../platform/error-handling.md](../../platform/error-handling.md) 에 존재.
8. **Bounded context 맵** — 위 contexts 의 데이터 소유와 통신 방향 도식. 위치: `specs/services/` 전반 또는 `knowledge/architecture/context-map.md`.

> **Library 경계**: 본 파일에는 구체 service 명을 직접 적지 않는다 — `<account-service>` 등 placeholder 또는 bounded context 이름 사용. 실제 service 명은 각 프로젝트의 `PROJECT.md` Service Map 과 `specs/services/` 가 담당.

---

## Interaction with Common Rules

- [../../platform/architecture.md](../../platform/architecture.md) 의 서비스 경계 원칙을 따르되, 위 bounded context 구분을 참조한다.
- [../../platform/error-handling.md](../../platform/error-handling.md) 에 위 Standard Error Codes 가 등록되어야 한다.
- [../traits/transactional.md](../traits/transactional.md) 의 트랜잭션·멱등성·outbox·상태기계 규칙이 F1·F2·F3 에 직접 적용.
- [../traits/regulated.md](../traits/regulated.md) 의 규제 데이터·승인 워크플로·보존 규칙이 F4·F7 에 적용 (선언 시).
- [../traits/audit-heavy.md](../traits/audit-heavy.md) 의 불변 감사 저장소·조회 API·보존 기간 규칙이 F6 에 적용 (선언 시).
- `integration-heavy` 미선언이라도 외부 자금 이동 경로의 circuit breaker / retry / idempotency 는 F1 으로 도메인 자체가 강제한다 (외부 통합이 자금 정확성의 직접 경로이기 때문).

---

## Checklist (Review Gate)

- [ ] 모든 자금 이동이 idempotency key 로 멱등 + 트랜잭션(outbox) 보호되는가? (F1)
- [ ] 가용/장부 잔액이 분리되고 음수 가용 잔액이 차단되는가? 잔액 변경 진입점이 단일한가? (F2)
- [ ] (v2) 분개 차변·대변 합이 항상 0 인가? (F2 ledger 확장)
- [ ] SETTLED/COMPLETED 거래가 immutable 하고 정정이 반전 거래로만 이루어지는가? (F3)
- [ ] 자금 이동 전 KYC 단계·AML·sanction 게이트를 통과하는가? sanction hit 이 운영자 큐로 가는가? (F4)
- [ ] 금액이 정수 minor-units / `BigDecimal` 로만 표현되고 float/double 이 전혀 없는가? 통화가 함께 다뤄지는가? (F5)
- [ ] 자금·규제 영향 연산이 불변 감사 기록(actor/time/before/after)을 남기는가? (F6)
- [ ] 규제 PII·금융 식별자·외부 자격증명이 암호화 보관 + 로그/이벤트 마스킹되는가? (F7)
- [ ] reconciliation 불일치가 자동 close 되지 않고 운영자 큐로 가는가? 잠긴 기간이 immutable 한가? (F8)
- [ ] 계좌/거래 상태 다이어그램 + 잔액 모델 + 컴플라이언스 게이트 + reconciliation 문서가 존재하는가?
- [ ] 표준 에러 코드가 플랫폼 카탈로그에 등록되어 있는가?
- [ ] 외부 금융기관/규제 제공사 통합이 별도 adapter 로 분리되어 도메인 코어에 침투하지 않는가?
