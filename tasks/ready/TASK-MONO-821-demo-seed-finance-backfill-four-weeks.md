# Task ID

TASK-MONO-821

# Title

라이브 데모의 Finance 개요 «실적» 그래프(입금 건수)가 부팅 당일 막대 하나뿐이다 — `TASK-MONO-820` 의 백필을 finance(`transactions` + `balances`)로 넓힌다

# Status

ready

# Owner

devops

# Task Tags

- demo
- seed
- data

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (잔액 불변식 · 시드 멱등 술어 · 가드 앵커와 엮인 생성기)
> Drafted 2026-10-28 UTC. 출처: 소유자 질문 «샘플데이터나 디비데이터가 그래프를 보기에 충분해?» → 36차 창 실측(Finance 이번 주 이체 ₩100,000 · 직전 ₩0, 막대 = 부팅 당일뿐) → 조사(아래 AC-0) → **소유자 결정 2026-10-28 UTC: Finance = 직접 SQL 백필(`TASK-MONO-820` 의 «finance 제외» 를 뒤집는다) · 이커머스 = 그대로 둔다.**

# Dependency Markers

- 선행: `TASK-MONO-820`(done) — 같은 생성기 `infra/demo/seed/lib-backfill.sh` 와 README § 「시각 경계 — 예외: 최근 4주 과거 이력」을 넓힌다. 820 의 Out of Scope 첫 줄(«finance … 소유자 결정으로 제외»)을 이 티켓이 소유자 결정으로 바꾼다.
- 관련: `TASK-PC-FE-378`(같은 그래프의 **익명 샘플**판 — `topupCount` 0 아닌 날 6).
- 범위 밖으로 남는 것: 이커머스(소유자 결정 «그대로 둔다» — 데이터가 `ecommerce` 테넌트라 `demo-corp` 시청자에겐 어차피 ₩0 · `EcommerceOrderDemoSeedTest` 의 주문 DB 직접 쓰기 금지 · 배송/정산/WMS 불일치).

# Goal

로그인 콘솔 `demo-corp` 의 Finance 개요 «실적» 일별 그래프(`chartKey="topupCount"`)가 부팅 시각 기준 지난 28일(어제까지) 중 여러 날에 막대를 보이고, 운영자 통합 개요 Finance 카드의 «지난 주» 이체 금액이 0 이 아니다 — 계좌 잔액(`balances`)은 거래 이력과 맞는 채로.

# Scope

## In Scope

- **AC-0 사실 (기안 중 조사 — 2026-10-28 UTC, 조사 에이전트 보고 + 메인 세션 코드 대조; 🔴 착수 시 file:line 재확인):**
  - 지표는 account-service MySQL `transactions` **한 테이블**만 읽는다: `TransactionJpaRepository` 의 `findSettledIn` — `tenant_id = :tenantId and settledAt >= :from and settledAt < :to`(`projects/finance-platform/apps/account-service/src/main/java/com/example/finance/account/infrastructure/persistence/jpa/TransactionJpaRepository.java:43-47`). 셈 규칙(`TransactionMetricsService`): type ∈ TOPUP · TRANSFER · CAPTURE, status ∈ SETTLED · COMPLETED, `settled_at` 있음. KST 일은 자바에서 `settled_at` 으로 자른다. `balances` · 원장 · 일 집계 표는 읽지 않는다.
  - 콘솔 그래프 키 = `topupCount`(`projects/platform-console/apps/console-web/src/features/finance-overview/components/FinanceOverviewScreen.tsx:321`). 운영자 개요 Finance 카드 = 이체 금액(36차 창 화면 «이번 주 이체 금액 (KRW)»).
  - 시각은 `ClockConfig` 의 `Clock.systemUTC()`(`…/account/infrastructure/config/ClockConfig.java:15`) — 과거 시각을 받는 요청 필드 · import 엔드포인트 · `migration-dev`/`R__` 시드 없음(account-service Flyway 는 `db/migration` V1~V3 뿐).
  - 스키마: `transactions`(V1 `:102-127` — `ck_txn_type` · `ck_txn_status` CHECK, `counterparty_account_id` 는 TRANSFER 입금 계좌) · `balances`(V1 `:56-70` — `uq_balances_account_currency`, **`ck_balances_non_negative`**: `ledger_minor >= 0 AND ledger_minor - held_minor >= 0`).
  - 시드 `infra/demo/seed/seed-finance.sh`: tenant `demo-corp`(`:45` `operator_token demo-corp`) · 계좌 A/B 는 API 로 개설해 **런타임 UUID**(`ACC_A` · `ACC_B`, `:118-120`) · 입금 `TOPUP_MINOR=500000`(`:183`) · 이체 A→B `TRANSFER_MINOR=100000`(`:230`) · 통화 KRW.
  - 🔴 시드의 입금 멱등 술어는 **잔액이 아니라 «`TOPUP_MINOR` 금액의 TOPUP 거래가 있는가»** 다(`:150-165` 주석 — TASK-MONO-556 의 교훈, `topup_txns`). 백필 TOPUP 이 같은 금액이면 그 술어가 오염된다. 그리고 그 술어는 거래 목록 API 를 읽으므로 백필 행이 오늘 입금을 첫 페이지 밖으로 밀면 거짓 실패가 난다.
  - 원장(ledger-service, 별도 DB)은 이벤트로만 분개하고 시각은 소비 시각이다(`…/ledger/application/PostFromTransactionUseCase.java:59` `clock.now()`) ⇒ 과거 날짜 분개는 어떤 길로도 안 생긴다. `/ledger` 화면이 «투영 동작의 증거» 라는 `seed-finance.sh:13-23` 의 설계는 백필 거래에는 해당하지 않는다(⇒ 한계로 문서화).
  - 가드: `infra/demo/verify-demo-wrapper.sh` 의 (z8) 이 `seed-finance.sh` 의 `ledger_of()` · `topup()` · `TOPUP_MINOR=` 를 앵커로 뽑아 가짜 서버로 돌린다(조사 보고 `:2036` 이후 — 착수 시 재확인). (y2) 가 `lib-backfill.sh --self-test` 를 돌린다.
- **구현 방향 (소유자 결정 범위 안):** `lib-backfill.sh` 에 `bf_finance_sql` 을 더하고 `seed-finance.sh` 가 계좌 A/B · 오늘 입금 · 이체가 끝난 **뒤** `dbexec --why` 로 넣는다.
  - `transactions`: 날짜·순번에서 유도한 결정적 id(820 의 `bf_uuid` 꼴), `INSERT IGNORE` 로 수렴. 하루 TOPUP 0~3건(날마다 다름 · 0 아닌 날 다수) + TRANSFER A→B 일부 날. status `COMPLETED`, `created_at = settled_at` = 그날 KST 시각. 금액은 `TOPUP_MINOR`(500000) · `TRANSFER_MINOR`(100000)과 **다른 값**만.
  - `balances.ledger_minor`: **새로 들어간 행만큼만** 같은 SQL 안에서 보정(A += 백필 TOPUP(A) − 백필 TRANSFER, B += 백필 TOPUP(B) + 백필 TRANSFER) — 두 번째 실행은 +0. 생성기가 «A 의 매 시점 잔액 ≥ 0» 을 자체 시험으로 보장(이체는 그 시점까지의 입금 합 안에서만).
  - outbox · audit_log · 원장: **쓰지 않는다**(`--why` 에 이유).
  - README § 시각 경계 — 예외 를 finance 로 넓히고(무엇을 넣고 무엇을 안 넣는가 · 원장에 과거 분개가 없는 이유), 820 Out of Scope 와 모순되는 문장을 정정.

## Out of Scope

- 이커머스(소유자 결정 «그대로 둔다»).
- account-service · ledger-service 코드 · 계약 · 시계 변경, 데모 전용 과거 시각 API(소유자가 고르지 않은 갈래).
- 원장 과거 분개 · 시산표의 과거 기간.
- 콘솔 코드 변경.

# Acceptance Criteria

- **AC-0** 위 사실을 착수 시점 코드로 file:line 재확인하고, 어긋난 것이 있으면 이 절 아래 Implementation Notes 에 정정 기록 후 진행(범위가 바뀌면 STOP · 보고).
- **AC-1** `seed-finance.sh` 가 지난 28일(KST 어제까지) TOPUP · TRANSFER 행을 `dbexec --why` 로 넣고, 같은 날 두 번 실행해도 `transactions` 행 수와 두 계좌 `ledger_minor` 가 같다(수렴 — 생성기 self-test + 스텁 하네스 또는 로컬 두 번 실행).
- **AC-2** 생성기 self-test 가 단언한다: 결정성(같은 부팅 시각 → 같은 SQL 바이트) · 백필 금액이 `TOPUP_MINOR` · `TRANSFER_MINOR` 와 겹치지 않음 · 계좌 A 의 시점별 잔액 ≥ 0 · 계획 행 수 = INSERT 수 · `topupCount` 0 아닌 날 ≥ 10 · 이체가 있는 날 중 지난 주 구간에 ≥ 1. bite 변형(금액 충돌 · 잔액 음수 · 수렴 절 제거)이 빨강.
- **AC-3** 시드의 입금 멱등 술어(`topup_txns`)와 (z8) 가드가 백필 뒤에도 초록이다 — 오늘 입금이 거래 목록의 판정 범위 안에 남는다(정렬·페이지를 코드로 확인하고 근거를 적는다). `bash -n` 스윕 · (y2) · (z8) · CI `Demo wrapper smoke` 초록.
- **AC-4** README 예외 절이 finance 를 포함하고 «원장에 과거 분개 없음» 을 한계로 적는다. `--why` 가 검증 가능한 사유(과거 시각 API 없음 · 지표가 읽는 열 · 잔액을 같이 맞추는 이유 · outbox 를 안 쓰는 이유)를 적는다.
- **AC-5 (라이브, 다음 굽기 뒤 창)** 로그인 콘솔 `demo-corp`: `/finance` «실적» 월간 `topupCount` 막대 ≥ 10일(🔴 달력월 — 매월 11일 이후 측정, 아니면 28일 계열을 API 로), 운영자 개요 Finance 카드 «지난 주» 이체 금액 > 0, 계좌 A/B 잔액 = 그 계좌 거래 이력 합(API 로 대조). 못 재면 ⚪ + 사유. `TASK-MONO-672` 에 등록.

# Related Specs

- `infra/demo/seed/README.md` § 시각 경계 — 예외: 최근 4주 과거 이력
- `projects/finance-platform/specs/contracts/http/account-api.md` § `GET /api/finance/accounts/metrics`

# Related Contracts

- `projects/finance-platform/specs/contracts/http/account-api.md` — 읽기만. **변경 없음.**

# Edge Cases

- 백필 TOPUP 금액이 `TOPUP_MINOR` 와 같으면 시드의 입금 판정이 «이미 있음» 으로 오판한다 → 금액 집합 분리(AC-2).
- 거래 목록 API 가 `created_at` 내림차순이 아니거나 첫 페이지가 작으면 오늘 입금이 밀려 거짓 실패 → AC-3 에서 확인.
- 부팅이 KST 자정 직후 · 3일 뒤 재부팅: 겹치는 날 행은 같은 id 라 수렴, 새 날만 더해진다(820 과 같은 규칙).
- 계좌 A/B 가 없으면(개설 실패) 백필을 건너뛰고 `seed_fail` 이 아니라 원인 줄을 남긴다(앞 단계가 이미 실패를 보고).
- 홀드(`held_minor`)가 있으면 `ledger_minor - held_minor >= 0` 도 지켜야 한다 — 시드는 홀드를 만들지 않지만 생성기 단언에 넣는다.

# Failure Scenarios

1. 거래 행만 넣고 잔액을 안 맞춘다 — `/finance/accounts` 의 잔액과 거래 내역 합이 갈라진다(820 이 finance 를 뺀 바로 그 이유).
2. 잔액을 매 실행마다 백필 합계만큼 더한다 — 두 번째 부팅부터 잔액이 부풀어 오른다(보정은 «새로 들어간 행» 만).
3. 이체가 A 잔액을 음수로 만든다 — `ck_balances_non_negative` 로 SQL 이 통째로 실패하고 시드가 ✗.
4. outbox 행을 넣는다 — ledger 가 오늘 날짜로 과거 거래를 분개해 시산표가 오늘 하루에 몰린다.
