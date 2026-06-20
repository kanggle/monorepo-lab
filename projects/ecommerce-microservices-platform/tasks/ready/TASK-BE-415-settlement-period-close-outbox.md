---
id: TASK-BE-415
title: settlement-service — settlement_period close + outbox(settlement.period.closed.v1) + 기간 운영자 API
status: ready
project: ecommerce-microservices-platform
service: settlement-service
type: feature
created: 2026-06-20
---

# TASK-BE-415 — settlement-service: 기간마감 + outbox 도입

> 분석=Opus 4.8 / 구현 권장=**Opus** — 상태기계 + 트랜잭션 경계(집계+outbox 원자성) + 신규 outbox 도입이 얽힌 복합 도메인 작업.

## Goal

settlement-service 에 **`settlement_period` 애그리거트(OPEN→CLOSED)** 와 **기간마감 use-case** 를 추가한다. 마감은 윈도 `[from, to)` 안의 **기존 불변** `commission_accrual` row 를 셀러별로 집계해 **`seller_payout` row(PENDING)** 를 생성하고(accrual 절대 미변이 — F3 유지), 같은 `@Transactional` 안에서 **`settlement.period.closed.v1`** 을 outbox 로 발행한다. 이를 위해 settlement-service 에 **outbox 를 신규 도입**(libs:java-messaging + outbox 마이그레이션)한다. 기간 운영자 API(`POST /periods`, `POST /periods/{id}/close`, `GET /periods`)를 노출한다.

payout **실행**(PENDING→PAID|FAILED, 시뮬레이션 어댑터)은 **TASK-BE-416** 범위다 — 본 task 는 마감 시점까지(payout PENDING 생성)만 한다.

## Background

- settlement-service 는 v1 에서 **terminal consumer**(발행 없음, outbox 제외 — libs:java-messaging 미포함). 본 증분이 첫 발행 서비스로 전환한다(spec: architecture.md § Outbox / Period close 갱신 완료, TASK-BE-414).
- 선례: finance-platform ledger `AccountingPeriod`(반열림 `[from,to)`, OPEN→CLOSED, 단일 mutating 애그리거트, 하부 원장 불변) + `CloseAccountingPeriodUseCase`(1 트랜잭션: load→집계 스냅샷→close→outbox publish) + `PeriodController`. **shape/wording 를 미러**한다.
- outbox 표준 DDL = order-service/payment-service 와 **동일**(`id BIGSERIAL PK, aggregate_type, aggregate_id, event_type, payload TEXT, created_at, published_at, status` + `idx_outbox_status_created`).
- 결정(확정, 재논의 금지): 윈도는 운영자 공급·grain-agnostic / 재마감=already-closed 에러 / net-zero 셀러(`payable_net ≤ 0`) skip(결정 7) / 2-step payout(결정 4 — 마감=PENDING 생성, 실행=BE-416) / `settlement.commission.accrued.v1` 데퍼드(정의·발행 금지, 결정 6).

## Scope

### In Scope

- **Flyway `V2`** (settlement-service): `settlement_period`(period_id PK, tenant_id, period_from, period_to[exclusive], status[OPEN|CLOSED] CHECK, closed_at, closed_by, seller_count, version) + `CHECK(period_from < period_to)` + `idx(tenant_id, period_from, period_to)`; `seller_payout`(payout_id PK, period_id FK→settlement_period, tenant_id, seller_id, payable_net_minor, commission_minor, accrual_count, status[PENDING|PAID|FAILED] CHECK, payout_reference NULL, paid_at, version) + `UNIQUE(period_id, seller_id)` + `idx(period_id)` + `idx(tenant_id, seller_id)`; 표준 `outbox` 테이블(order/payment 와 동일 DDL).
- **outbox 도입**: `build.gradle` 에 `libs:java-messaging` 추가 + `OutboxAutoConfiguration`/`OutboxMetricsAutoConfiguration` 활성(현재 제외됨). 마감 경로 외 consume 경로는 발행 없음 — 무변경.
- **도메인**: `SettlementPeriod` 애그리거트(`open(periodId, tenantId, from, to)` — `from < to` 검증; `close(closedAt, closedBy, sellerCount)` — OPEN→CLOSED, 재호출 시 already-closed 예외; `covers`/`overlaps` 반열림 술어 — finance `AccountingPeriod` 미러). `SellerPayout` 애그리거트(PENDING 생성, 상태기계 보유 — 실행 전이는 BE-416). `PeriodStatus`/`PayoutStatus` enum.
- **application**: `OpenSettlementPeriodUseCase`; `CloseSettlementPeriodUseCase`(1 `@Transactional`: 기간 load[부재 404]→ 윈도 내 accrual 집계 → 셀러별 `seller_payout` PENDING 생성[net-zero skip]→ period.close[재마감 409]→ `settlement.period.closed.v1` outbox append). `QuerySettlementPeriodUseCase`(list/get).
- **집계 쿼리**: `commission_accrual` 에서 `occurred_at ∈ [period_from, period_to)` + `tenant_id` 필터로 셀러별 `Σ seller_net_minor`(payable_net), `Σ commission_minor`, count(accrual_count) 산출(repository read-only, accrual 미변이).
- **이벤트 발행**: `settlement.period.closed.v1`(envelope snake_case, `tenant_id` 봉투; payload `{period_id, tenant_id, period_from, period_to, closed_at, seller_count, payouts:[{seller_id, payable_net_minor, commission_minor, accrual_count}]}`) — `settlement-events.md` 계약 준수. topic `settlement.period.closed`.
- **HTTP**: `POST /api/admin/settlements/periods`(201, `PERIOD_WINDOW_INVALID` 422), `POST /api/admin/settlements/periods/{periodId}/close`(200, `PERIOD_ALREADY_CLOSED` 409, 404), `GET /api/admin/settlements/periods`(200, tenant-scoped, page/size). `roles ∋ ADMIN` 게이트 + tenant 컨텍스트(기존 settlement 컨트롤러 패턴 미러).
- **observability**: `settlement_period_closed_total`(Counter) — 마감 1회당 +1.

### Out of Scope

- **payout 실행**(PENDING→PAID|FAILED), `SellerPayoutPort`/시뮬레이션 어댑터, `GET …/payouts`, `POST …/payouts/execute`, `settlement_payout_total` 메트릭 — **TASK-BE-416**.
- REAL 뱅킹/PG payout, 셀러 계좌, `=bank` 어댑터.
- `settlement.commission.accrued.v1` 정의·발행(데퍼드).
- 기간 reopen, 부분/비례 clawback, multi-currency, 티어 수수료.

## Acceptance Criteria

- [ ] **AC-1 (마이그레이션)**: `V2` 가 `settlement_period`/`seller_payout`/`outbox` 3 테이블을 spec(DATA MODEL: PK/FK/CHECK/UNIQUE/idx)대로 생성. `outbox` DDL 은 order/payment-service 와 byte-동일 형태.
- [ ] **AC-2 (도메인 — 기간)**: `SettlementPeriod.open` 이 `from ≥ to` 에 invalid-window 예외; `close` 가 OPEN→CLOSED 로 전이하며 `closed_at`/`closed_by`/`seller_count` 스탬프, 2회차 close → already-closed 예외. `covers`/`overlaps` 반열림(`from ≤ t < to`, abutting 윈도 non-overlap) 단위테스트.
- [ ] **AC-3 (집계 — F3 불변)**: 마감이 윈도 내 accrual 을 셀러별로 fold(`payable_net = Σ seller_net`(ACCRUAL−REVERSAL), `commission = Σ commission`, `accrual_count`) → `seller_payout` PENDING 생성. **accrual row 는 SELECT 만**(UPDATE/DELETE 0건) — IT 로 마감 전후 accrual 동일 검증.
- [ ] **AC-4 (net-zero skip — 결정 7)**: fold 후 `payable_net_minor ≤ 0` 셀러는 payout row 미생성. `seller_count` = 양수 payable 셀러 수 = 생성된 payout row 수.
- [ ] **AC-5 (outbox 원자성)**: 마감 use-case 가 단일 `@Transactional` 안에서 period.close + payout insert + `settlement.period.closed.v1` outbox append 를 수행. 트랜잭션 롤백 시 셋 다 미반영(IT: 강제 예외 주입 → period OPEN 유지 + payout 0 + outbox 0).
- [ ] **AC-6 (이벤트 계약)**: 발행된 `settlement.period.closed.v1` 봉투/payload 가 `settlement-events.md` 와 정합(snake_case, `tenant_id` 봉투, `payouts[]` = 양수 payable 셀러만, `seller_count == payouts.length`). contract/직렬화 테스트.
- [ ] **AC-7 (API)**: `POST /periods`(201 OPEN), `POST /periods/{id}/close`(200 CLOSED + payouts PENDING), `GET /periods`(tenant-scoped list). 재마감 409 `PERIOD_ALREADY_CLOSED`, invalid window 422 `PERIOD_WINDOW_INVALID`, cross-tenant period 404. `roles ∋ ADMIN` 미보유 → 거부.
- [ ] **AC-8 (메트릭)**: `settlement_period_closed_total` 마감 1회당 +1.
- [ ] **AC-9 (빌드)**: `:settlement-service:test` GREEN(단위+컴파일). Testcontainers IT 는 CI Linux 권위.

## Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/ecommerce.md` + each `rules/traits/<trait>.md`(transactional/content-heavy/read-heavy/integration-heavy/multi-tenant). settlement-service Service Type = `event-consumer + rest-api` 하이브리드 → `platform/service-types/event-consumer.md`(primary) + `rest-api.md`.

- `projects/ecommerce-microservices-platform/specs/services/settlement-service/architecture.md`(§ Period close + simulated payout / § Outbox / § Boundary Rules / Owned Data — 본 task 전제) / `overview.md` / `observability.md`
- `projects/ecommerce-microservices-platform/specs/features/marketplace-settlement.md` §6
- `projects/finance-platform/apps/ledger-service/` — `AccountingPeriod` / `CloseAccountingPeriodUseCase` / `PeriodController` 선례(미러 대상)
- `platform/architecture-decision-rule.md` + settlement architecture.md § Change Rule(이미 TASK-BE-414 로 선반영됨)

# Related Skills

- `.claude/skills/backend/` — DDD aggregate / transactional-outbox / Flyway-migration 관련 스킬 참조(`.claude/skills/INDEX.md`).

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/settlement-events.md`(신규 producer — 본 task 가 구현)
- `projects/ecommerce-microservices-platform/specs/contracts/http/settlement-api.md`(§ Period close + payout — 본 task 는 periods 3 엔드포인트, payouts 2 는 BE-416)

## Target Service

- `settlement-service`

## Architecture

Follow `specs/services/settlement-service/architecture.md`(DDD-style 4-layer + application/port). interface→application→domain, infrastructure→domain/ports. 컨트롤러는 use-case 만 호출(JPA 직접 금지). 마감 use-case 가 트랜잭션 경계 소유.

## Implementation Notes

- finance `CloseAccountingPeriodUseCase` 의 "1 트랜잭션: load→집계→close→publish" 구조를 그대로 미러하되, finance 의 snapshot 대신 **seller_payout row 생성**이 산출물.
- `tenant_id`: 기간/payout 은 운영자 tenant 컨텍스트(gateway `X-Tenant-Id`)에서 적재 — 기존 settlement 컨트롤러의 tenant 해석 재사용.
- accrual 집계는 **read-only**. 절대 accrual 을 마감 상태로 마킹하거나 변이하지 말 것(F3). payout 이 accrual 을 "소비"하는 모델 아님 — 단순 윈도 집계.
- outbox 도입 시 다른 ecommerce 발행 서비스(order/payment)의 outbox 배선(`@Transactional` 내 append + dispatcher)을 그대로 따른다.

## Edge Cases

- **재마감 가드**: 이미 CLOSED 기간 close → 409, 멱등 아님(중복 payout/이벤트 금지).
- **net-zero 셀러 skip**: 전액 환불(reversal)로 `payable_net ≤ 0` 인 셀러는 payout row 없음.
- **빈 윈도 마감**: 윈도 내 accrual 0건 → payout 0, `seller_count=0`, 이벤트 `payouts:[]` (정상 CLOSED).
- **멱등 집계**: 마감은 OPEN→CLOSED 1회만 가능하므로 payout 정확히 1회 생성(재마감 차단으로 보장).
- **invalid window**: `from ≥ to` → 422, 기간 미생성.

## Failure Scenarios

- **outbox 원자성 위반**: payout insert 성공·outbox append 실패(또는 그 역)가 부분 커밋되면 이벤트/원장 불일치 → 단일 `@Transactional` 필수(AC-5).
- **accrual 변이**: 마감이 accrual 을 건드리면 F3(append-only 불변) 위반 → read-only 집계 강제(AC-3).
- **net-zero 셀러에 payout 생성**: 0/음수 payable payout row → 무의미한 지급 대상. skip 강제(AC-4).
- **commission.accrued.v1 우발 발행**: 데퍼드 이벤트를 정의/발행하면 계약 드리프트 → 발행 금지(결정 6).
- **cross-tenant 누수**: 타 tenant 기간이 마감/조회됨 → tenant 필터 + 404(M3).

## Test Requirements

- 단위: `SettlementPeriodTest`(open/close/covers/overlaps/재마감 예외), `CloseSettlementPeriodUseCaseTest`(집계 fold·net-zero skip·outbox append mock).
- 통합(`@Tag("integration")`, Testcontainers): `V2` 마이그레이션 적용, 마감 → seller_payout PENDING + outbox row 생성 + accrual 불변 검증, 롤백 원자성, cross-tenant 격리(M6).
- 계약: `settlement.period.closed.v1` 직렬화가 `settlement-events.md` 와 정합.
- API: `POST/GET /periods`, `POST /periods/{id}/close` 슬라이스/통합(409/422/404 + ADMIN 게이트).

## Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing (`:settlement-service:test` GREEN)
- [ ] Contracts honored (settlement-events.md / settlement-api.md periods 부분)
- [ ] Specs already updated (TASK-BE-414) — no further spec change needed
- [ ] Ready for review
