# Task ID

TASK-MONO-821

# Title

라이브 데모의 Finance 개요 «실적» 그래프(입금 건수)가 부팅 당일 막대 하나뿐이다 — `TASK-MONO-820` 의 백필을 finance(`transactions` + `balances`)로 넓힌다

# Status

review

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

# Implementation Notes (2026-10-28 UTC)

- **AC-0 재확인 — 기안의 사실 전부 일치, 정정 1건 + 보강 3건.**
  - 일치: `TransactionJpaRepository.java:41-51` `findSettledIn`(tenant + `settledAt` 범위 — 기안의 `:43-47` 은 쿼리 본문 줄) · `TransactionMetricsService.java:74-78`(TOPUP·TRANSFER·CAPTURE / SETTLED·COMPLETED) · `FinanceOverviewScreen.tsx:321` `chartKey="topupCount"` · `ClockConfig.java:15` `Clock.systemUTC()` · account-service Flyway `V1`~`V3` 뿐(`V3` = `idx_txn_tenant_settled`) · `V1__init.sql:56-70` `balances`(`ck_balances_non_negative`) · `:102-127` `transactions` · `PostFromTransactionUseCase.java:59` `clock.now()` · `seed-finance.sh` `TOPUP_MINOR=500000` · `TRANSFER_MINOR=100000` · (z8) 추출 `verify-demo-wrapper.sh:2036`(이 PR 뒤 `:2037`) · (y2) `:1534-1561`.
  - 🔴 정정: 기안 AC-0 은 거래 목록의 정렬·페이지를 «착수 시 확인» 으로 남겼다. 코드: `TransactionJpaRepository#search` = `order by t.createdAt desc`, `TransactionController.java:160-166` `size` 기본 20 · 상한 없음, 시드는 `size=100` 한 쪽만 읽었다. ⇒ **첫 부팅에는 안전**(백필은 어제까지 · 오늘 입금이 최신)하지만, 볼륨이 살아남는 동안 500000 입금은 첫 부팅 날 한 번 생기고(이후 재생) 그 뒤 날짜의 백필 TOPUP 은 전부 더 최신이다 ⇒ A 의 TOPUP 이 100건을 넘으면 첫 쪽 밖으로 밀려 «0건» 거짓 실패. **시드의 술어를 고쳤다**(서비스는 안 건드림): `topup_txns` 가 꽉 찬 쪽(100건)이면 다음 쪽을 읽는다, 20쪽(2,000건)까지 꽉 차면 «판정 불가»(빈 문자열).
  - 보강: ① TRANSFER 는 **한 행**(원 계좌 A, `counterparty_account_id` = B — `AccountApplicationService.java:289-326`, `TransactionMetricsService` javadoc «ONE row») ⇒ 백필도 한 행. B 의 거래 목록에는 받은 이체가 안 보인다(서비스의 기존 동작). ② account-service 의 MySQL = `finance-platform-mysql` · `finance_db` · `${MYSQL_USER:-finance}`(`projects/finance-platform/docker-compose.yml:237-259`, `infra/demo/projects.sh:67` 이 그 compose 를 쓴다 — 데모 오버라이드는 mysql 을 안 바꾼다). Hibernate `jdbc.time_zone: UTC` + JDBC `serverTimezone=UTC`(`application.yml:6,20`) ⇒ DATETIME(6) = UTC 벽시계. ③ 범위 변화 없음 → 진행.
- **구현.**
  - `infra/demo/seed/lib-backfill.sh` — `bf_finance_sql <tenant> <A> <B>`: 창 28일(KST 어제까지), 하루 입금 0~3건(날짜 해시) · 이체 A→B 는 **매주 월요일 + 해시로 약 1/4 의 날**(이체 날은 입금 ≥ 1). id `00000820-6101-…`(입금) · `00000820-6102-…`(이체). 금액 입금 {80,000 … 350,000} · 이체 {20,000 · 40,000 · 60,000 · 75,000} — 500,000 · 100,000 과 분리. 이체 날 순번 0 입금 = A · 07시대, 이체 = 08시대, 이체 금액 < 입금 최솟값 ⇒ A 의 그날 누계가 모든 시점에 ≥ 0. status COMPLETED · `created_at = settled_at` · version 0.
  - **잔액 보정 방법 (새 행만):** 각 `INSERT IGNORE INTO transactions` 바로 뒤 `SET @bf_r = ROW_COUNT();`(새 행 1 · 이미 있던 id 0 — MySQL 은 INSERT IGNORE 로 무시된 행을 ROW_COUNT 에 안 센다), 다음 줄에서 `@bf_n` · `@bf_da` · `@bf_db` 에 `@bf_r × 계좌별 증분`을 더하고, 끝에서 `UPDATE balances SET ledger_minor = ledger_minor + @bf_da … AND @bf_da <> 0`(B 도 같음) — 전부 한 `START TRANSACTION … COMMIT`. 마지막 `SELECT @bf_n, @bf_da, @bf_db` 를 시드가 읽는다. 고른 이유: 임시 표 + `UNION` 은 MySQL 이 한 문장에서 임시 표를 두 번 못 연다(ERROR 1137) · 전후 개수 비교는 계좌별 금액을 모른다 · ROW_COUNT 는 세션 안·문장 단위라 경합이 없고, 각 INSERT 와 증분이 같은 두 줄에 붙어 있어 자기시험이 짝을 잴 수 있다. CHECK 위반이면 mysql 이 그 문장에서 멈추고 커밋 없이 끊겨 전체 롤백.
  - 자기시험 셀 추가(44 → 59칸): 다섯 번째 도메인으로 (4) 다른 프로세스 바이트 동일 · 같은 KST 날 다른 시각 동일 · (5) 시각 창 안 · (6) 3일 뒤 겹치는 날 행 동일 · (7) 수렴 절 · **(7c)** INSERT 마다 바로 뒤 `ROW_COUNT()` · 잔액 UPDATE 2문은 증분 변수만 · outbox/audit/원장 0 · (8) `bf-planned transactions` = INSERT 수 · (10) 빈 계좌 · A = B 거절 · **(11)** 부팅 21개(E1 부터 매일)에 걸쳐 금액 분리 · A 의 시점별 잔액(창 누계 · 그날 누계) ≥ 0 · 방향(입금 A/B · 이체 A→B 만) · `topupCount` 0 아닌 날 ≥ 10(최소 24) · 값 2종 이상 · 지난 달력 주(KST 월~일)에 이체 ≥ 1 · **(12)** 스텁 MySQL(`_bf_fin_apply` — 생성기 문장 꼴만 해석, 중복 키 = 오류, IGNORE = ROW_COUNT 0, 음수 잔액 = CHECK 오류, 모르는 문장 = 오류)에 1회차 → 같은 날 2회차 → 3일 뒤.
  - `infra/demo/seed/seed-finance.sh` — 이체 블록 **뒤**(검증·원장 대기 앞)에 `finance_backfill`: A/B id 가 비면 `생략` 한 줄(실패는 개설 단계가 이미 셌다), 두 계좌의 KRW 잔액 행이 2개가 아니면 ✗ 하고 안 넣음, `dbexec --why` 로 넣은 뒤 창 안 행 수(6101 · 6102 두 범위) = 계획 수, **두 계좌 잔액 변화 = SQL 이 보고한 증분**을 대조 → «생성 … (이번에 +N · 잔액 A +x · B +y)» / «존재 … (수렴, +0 · 잔액 불변)» / ⚠(계획 ≠ 창 안) / ✗. dbexec 출력을 받으므로 그 사유 로그 줄은 받은 뒤 다시 찍는다. `topup()` ~ `topup "계좌 A 입금"` 구간((z8) 추출 범위)에는 `topup_txns` 페이지 넘김만 들어갔다. 헤더 주석에 «/ledger 는 백필 거래를 모른다» 한 줄.
  - `infra/demo/seed/README.md` § 시각 경계 — 예외: 표에 finance 행 · «네 → 다섯» · **«FINANCE 는 잔액을 같이 맞춘다»** 절(820 의 «finance 제외» 판단이 소유자 결정 2026-10-28 UTC · TASK-MONO-821 로 뒤집혔음 · 잔액 보정 방법 · 비음수 · 금액 분리 + 술어 페이지 넘김 · **원장 한계: `/ledger` 에 과거 분개 없음**, outbox 를 안 쓰는 이유 · 양) · 배치 자리 · 시험 목록.
  - `infra/demo/verify-demo-wrapper.sh` (y2): `seed-finance.sh` 도 `lib-backfill.sh` 를 source 해야 초록(«네 → 다섯 시드»).
  - `tasks/ready/TASK-MONO-672-…` § Order 23 — 821 AC-5 측정 항목 등록(굽기 필요).
- **`--why`(검증 가능 사유):** 과거 시각으로 입금·이체하는 API 없음(`/topups` · `/transfers` 가 `ClockConfig` 의 `Clock.systemUTC()` 로 찍음, 과거 시각 요청 필드 · import · 데모 Flyway 시드 없음) · 지표는 `transactions.settled_at` 만 KST 일로 자름(`TransactionMetricsService` · `findSettledIn`) · 잔액을 같은 트랜잭션에서 ROW_COUNT 만큼 맞추는 이유(목록·잔액 정합, `ck_balances_non_negative`) · outbox · audit_log 를 안 쓰는 이유(ledger-service 가 `clock.now()` 로 분개 → 오늘에 몰림) · INSERT IGNORE 수렴.
- **증거 (로컬, Docker 데몬 없음 — 실제 MySQL 미실행):**
  - AC-1 · AC-2: `bash infra/demo/seed/lib-backfill.sh --self-test` **rc=0, ok 59칸**(finance 15칸 포함; E1=2026-10-28 03:00Z 기준 계획 67행 = 입금 53 + 이체 14). bite(스크래치 복사본에 변형, 원본 무변경): 입금 금액 하나를 500000 으로 → **rc=1**(금액 분리 셀) · 이체 시각을 05시대(같은 날 입금 앞)로 → **rc=1**(A 잔액 음수 시점 323) · `INSERT IGNORE` → `INSERT` → **rc=1**(수렴 절 67/67 + 스텁 2회차 중복 키) · 잔액 UPDATE 를 리터럴 증분으로 → **rc=1**((7c) + 스텁 모르는 문장). 스텁 하네스(scratchpad, `seed-finance.sh` 의 `finance_backfill()` 을 awk 로 그대로 떼어 와 파일 상태 가짜 MySQL 로): 1회차 rc=0 «생성 … 창 안 67 건 (이번에 +67 · 잔액 A +6295000 · B +4485000)» → 같은 날 다른 시각 2회차 rc=0 «존재 … (수렴, +0 · 잔액 불변)» 잔액 동일 → 3일 뒤 rc=0 «이번에 +9» → A id 빈 값이면 «생략» · 실패 0 → B 잔액 행 없음이면 ✗ 1 · INSERT 안 함.
  - AC-3: 위 정렬·페이지 근거(AC-0 정정). 스크래치 가짜 서버(최신 백필 TOPUP 120건 뒤에 500000 1건, `createdAt desc` 페이지)에서 새 `topup_txns` = **1**, `origin/main` 의 옛 한 쪽 술어 = **0**(거짓 실패 재현). `verify-demo-wrapper.sh` (y)+(y2) 구간 단독 실행 **rc=0**(«다섯 시드 전부 source»), (z8) 구간 단독 실행 **rc=0**(1회차 · 이체 뒤 2회차 · 미입금 실패 · 조회불가 실패). `bash -n` — `lib-backfill.sh` · `seed-finance.sh` · `verify-demo-wrapper.sh` rc=0, `infra/demo/seed/*.sh` 스윕 실패 0. 전체 `verify-demo-wrapper.sh` 는 Docker 필요 — 위 구간만 돌렸다. CI `Demo wrapper smoke` 는 PR 에서.
  - AC-4: README 예외 절 · `--why` 위와 같음.
  - AC-5: ⏳ 라이브 — 다음 굽기 뒤 창(`TASK-MONO-672` § Order 23).
  - `scripts/` 파일 추가·삭제 없음.
- **남은 위험.**
  - 실제 MySQL 에서의 실행(ROW_COUNT · CHECK · `mysql` 배치 출력 마지막 줄 파싱)은 스텁으로만 쟀다 — 첫 실측은 굽기 뒤 창(§ Order 23 첫 칸).
  - `dbexec` mysql 은 stderr 를 버린다 — 실패 원인은 SQL 을 손으로 다시 흘려야 보인다(820 과 같음).
  - 원장(`/ledger`)은 백필 거래를 모른다 — 계좌 잔액 > 원장 고객 지갑 잔액(문서화한 한계). 원장·계좌 대사 화면이 있다면 그 차이를 «불일치» 로 보일 수 있다(이 티켓에서 대사 화면은 확인하지 않았다).
  - 생성기를 나중에 바꾸면 옛 판 id 의 행은 남고(INSERT IGNORE) 새 판 id 만 들어간다 — 잔액은 새로 들어간 행만큼만 맞으므로 정합은 유지되지만 사후 대조가 ⚠ 로 어긋날 수 있다.
  - 데모 운영자가 콘솔에서 계좌 A 로 큰 이체를 하면 이후 백필 날짜가 A 를 더 쌓을 뿐 음수로 만들지는 않는다(하루 순증 ≥ 0) — 그러나 그 이체 자체의 잔액 부족은 서비스가 막는다.
