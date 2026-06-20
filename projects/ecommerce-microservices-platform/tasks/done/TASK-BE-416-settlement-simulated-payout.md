---
id: TASK-BE-416
title: settlement-service — SellerPayoutPort + 시뮬레이션 어댑터 + payout 실행 use-case/API + 메트릭
status: done
project: ecommerce-microservices-platform
service: settlement-service
type: feature
created: 2026-06-20
depends_on: TASK-BE-415
---

# TASK-BE-416 — settlement-service: 시뮬레이션 seller payout 실행

> 분석=Opus 4.8 / 구현 권장=**Sonnet** — BE-415 의 애그리거트/테이블 위에 port + 어댑터 + 상태전이 use-case + 2 엔드포인트를 얹는 비교적 정형 작업(no green-wash 규율 + 멱등만 주의).

> **선행(depends on / prerequisite): TASK-BE-415.** `settlement_period`/`seller_payout` 테이블·`SellerPayout` 애그리거트(PENDING 생성)·기간마감이 선행되어야 본 task 의 실행 전이가 성립한다. BE-415 미머지 상태에서 착수 금지.

## Goal

BE-415 가 PENDING 으로 생성한 `seller_payout` 을 **실행**해 PENDING→PAID|FAILED 로 전이하는 슬라이스를 추가한다: **`SellerPayoutPort`**(outbound 포트) + **`SimulatedSellerPayoutAdapter`**(이번 증분 유일 어댑터, `@ConditionalOnProperty(settlement.payout.mode=simulated, matchIfMissing=true)`, 합성 `payout_reference` 기록 후 PAID — **green-wash 금지**). payout 실행 use-case(멱등: `(period_id, seller_id)`) + payout API(`GET /periods/{id}/payouts`, `POST /periods/{id}/payouts/execute`) + 메트릭 `settlement_payout_total{status}` + IT.

REAL 뱅킹/PG 어댑터(`=bank`)는 **미구현 seam** — 본 task 는 슬롯만 인지하고 구현하지 않는다.

## Background

- 2-step payout(결정 4): 마감(BE-415)=payout PENDING 생성, 실행(본 task)=시뮬레이션 어댑터로 PAID|FAILED. close 와 execute 는 별개 운영자 액션.
- no green-wash 규율 선례 = erp `NoopExternalChannelAdapter`(로그만, 실 전달 주장 안 함, `matchIfMissing` 기본 stub). 시뮬레이션 어댑터도 동일하게 "시뮬레이션임"을 로그+reference prefix 로 명시하고 **실 송금 주장 금지**.
- 결정(확정): 시뮬레이션 어댑터가 유일 / `=bank` 슬롯 미구현 seam / 멱등은 `(period_id, seller_id)` 키(이미 PAID 면 재실행 무시) / 운영자 API only(no UI, 결정 5).
- spec 은 TASK-BE-414 로 선반영됨(architecture.md § Period close + simulated payout / settlement-api.md payouts 2 엔드포인트 + `PERIOD_NOT_CLOSED` / observability.md `settlement_payout_total`).

## Scope

### In Scope

- **port**: `SellerPayoutPort`(application outbound) — `execute(SellerPayout)` → 결과(PAID + `payout_reference` | FAILED + 사유).
- **어댑터(시뮬레이션)**: `SimulatedSellerPayoutAdapter implements SellerPayoutPort`, `@ConditionalOnProperty(name="settlement.payout.mode", havingValue="simulated", matchIfMissing=true)`. 합성 reference 생성(예: `SIM-{periodId}-{sellerId}-{uuid}` — 시뮬레이션 식별 prefix), 로그에 "simulated payout (NOT a real disbursement)" 명시, PAID 반환. **실 송금 주장/실패 은폐 금지**.
- **실행 use-case**: `ExecuteSellerPayoutsUseCase` — 기간(CLOSED 여야 함; OPEN → `PERIOD_NOT_CLOSED` 409) 의 PENDING payout 들에 port 적용 → `SellerPayout` 상태전이(PENDING→PAID|FAILED) + `payout_reference`/`paid_at` 스탬프. **멱등**: 이미 PAID row 는 건너뜀(`(period_id, seller_id)` 단위), 재실행 = 잔여 PENDING 만 처리.
- **상태기계**: `SellerPayout.markPaid(reference, paidAt)` / `markFailed(reason)` — PENDING 에서만 전이(이미 PAID/FAILED 재전이 가드).
- **HTTP**: `GET /api/admin/settlements/periods/{periodId}/payouts`(tenant + seller-scope ABAC 필터 — accrual read 와 동일, 404), `POST /api/admin/settlements/periods/{periodId}/payouts/execute`(200, post-execution statuses, `PERIOD_NOT_CLOSED` 409, 404). `roles ∋ ADMIN`.
- **observability**: `settlement_payout_total{status}`(Counter, tag `status ∈ {PAID, FAILED}`) — payout 해소 시 +1.
- **REAL `=bank` seam 인지**: 어댑터 인터페이스/조건부 빈 구조상 `havingValue="bank"` 어댑터를 미래에 추가할 수 있도록 port 추상화만 둠(구현 0).

### Out of Scope

- REAL 뱅킹/PG payout, 셀러 계좌, `=bank` 어댑터 구현.
- 마감/집계/period open(=BE-415).
- payout 후 추가 이벤트 발행(이번 증분 이벤트는 `settlement.period.closed.v1` 뿐 — 실행은 이벤트 미발행). 
- 부분 payout, 재시도/스케줄러 자동 실행(운영자 수동 execute only).

## Acceptance Criteria

- [ ] **AC-1 (port + 시뮬레이션 어댑터)**: `SellerPayoutPort` 정의 + `SimulatedSellerPayoutAdapter`(`@ConditionalOnProperty(settlement.payout.mode=simulated, matchIfMissing=true)`)가 합성 reference 로 PAID 반환. 로그/주석/ reference prefix 에 "시뮬레이션(실 송금 아님)" 명시 — **green-wash 부재**(erp Noop 규율 미러). `=bank` 어댑터는 부재(seam only).
- [ ] **AC-2 (실행 전이)**: `ExecuteSellerPayoutsUseCase` 가 CLOSED 기간의 PENDING payout 을 PAID 로 전이(+`payout_reference`+`paid_at`). simulated 경로에서 전부 PAID.
- [ ] **AC-3 (멱등)**: 같은 기간 execute 2회 호출 → 2회차는 잔여 PENDING 만 처리(이미 PAID 미변경, reference 안정). `(period_id, seller_id)` UNIQUE 기준 중복 지급 불가.
- [ ] **AC-4 (상태 가드)**: OPEN 기간 execute → 409 `PERIOD_NOT_CLOSED`. 이미 PAID payout 재전이 시도 → 무시(예외 아님). FAILED→PAID 등 역전이 차단.
- [ ] **AC-5 (payouts read)**: `GET /periods/{id}/payouts` 가 tenant 필터 **안에서** seller-scope ABAC(`X-Seller-Scope` 부재/`'*'`=무필터, restricted=seller 필터) 적용. cross-tenant/cross-seller → 404. `payoutReference` 는 PENDING 시 null, PAID 시 set.
- [ ] **AC-6 (메트릭)**: `settlement_payout_total{status="PAID"}` 가 PAID 해소 수만큼 증가(FAILED 경로 시 `status="FAILED"`).
- [ ] **AC-7 (IT)**: Testcontainers 로 마감(BE-415 경로)→execute→payout PAID + reference set + 메트릭 검증. 멱등 재실행 IT. OPEN execute 409 IT.
- [ ] **AC-8 (빌드)**: `:settlement-service:test` GREEN(단위+컴파일).

## Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` + `rules/common.md` + `rules/domains/ecommerce.md` + traits. Service Type `event-consumer + rest-api` → `platform/service-types/event-consumer.md` + `rest-api.md`.

- `projects/ecommerce-microservices-platform/specs/services/settlement-service/architecture.md`(§ Period close + simulated payout — `SellerPayoutPort` + 시뮬레이션/`=bank` seam + 2-step) / `observability.md`(`settlement_payout_total`)
- `projects/ecommerce-microservices-platform/specs/features/marketplace-settlement.md` §6
- `projects/erp-platform/apps/notification-service/.../NoopExternalChannelAdapter.java` — no-green-wash 규율 선례
- `projects/ecommerce-microservices-platform/tasks/ready/TASK-BE-415-settlement-period-close-outbox.md` — 선행 task

# Related Skills

- `.claude/skills/backend/` — outbound-port/adapter + `@ConditionalOnProperty` 패턴(`.claude/skills/INDEX.md`).

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/settlement-api.md`(§ Period close + payout — `GET /periods/{id}/payouts`, `POST /periods/{id}/payouts/execute`, `PERIOD_NOT_CLOSED`)

## Target Service

- `settlement-service`

## Architecture

Follow `specs/services/settlement-service/architecture.md`. `SellerPayoutPort` = application outbound port; 어댑터 = infrastructure. use-case 가 트랜잭션 경계 소유. 도메인 `SellerPayout` 이 상태전이 불변식 보호(PENDING 에서만 markPaid/markFailed).

## Implementation Notes

- 시뮬레이션 어댑터의 reference 는 결정적/식별가능해야 하나 실제 PG 참조처럼 보이게 위장 금지 — `SIM-` prefix 등으로 시뮬레이션임을 자명하게.
- seller-scope 필터는 accrual read 와 **동일 코드 경로 재사용**(isolate-then-attribute: tenant 필터 내부에 seller 필터).
- execute 는 이벤트를 발행하지 않는다(이번 증분 발행 이벤트는 close 의 `settlement.period.closed.v1` 뿐). payout 상태는 read API 로만 노출.
- `=bank` 어댑터는 **만들지 않는다** — port 추상화가 미래 슬롯을 허용한다는 사실만 주석으로 남긴다.

## Edge Cases

- **멱등 재실행**: execute 2회 → 이미 PAID 무변경, 잔여 PENDING 만(중복 지급 0).
- **OPEN 기간 execute**: 409 `PERIOD_NOT_CLOSED`(마감 전 지급 금지).
- **payout 0건 기간**(net-zero 전체): execute → 변경 0, 200(빈 결과).
- **seller-scope restricted 운영자**: 자기 셀러 payout 만 조회; 타 셀러 payout 404.
- **어댑터 mode 미설정**: `matchIfMissing=true` 로 simulated 활성(기본). `=bank` 설정 시 빈 부재 → 기동 실패(의도된 seam — 아직 미구현임을 드러냄).

## Failure Scenarios

- **green-wash**: 어댑터가 실 송금 발생/성공을 주장 → 신뢰 훼손. "시뮬레이션·실 송금 아님" 명시 강제(AC-1, erp Noop 미러).
- **중복 지급**: 멱등 부재로 재실행이 PAID 를 다시 지급 → `(period_id, seller_id)` 가드 + PENDING-only 전이(AC-3/4).
- **역전이**: FAILED/PAID → PENDING 등 잘못된 상태전이 → 도메인 가드(AC-4).
- **cross-tenant/seller 누수**: 타 tenant/seller payout 노출 → tenant+seller-scope 필터 + 404(AC-5).
- **OPEN 기간 지급**: 마감 전 execute 가 통과하면 미확정 집계 지급 → CLOSED 가드(AC-4).

## Test Requirements

- 단위: `SellerPayoutTest`(markPaid/markFailed PENDING-only 가드), `SimulatedSellerPayoutAdapterTest`(PAID + 시뮬레이션 reference + no green-wash 로그), `ExecuteSellerPayoutsUseCaseTest`(전이·멱등·메트릭 mock·OPEN 409).
- 통합(`@Tag("integration")`, Testcontainers): 마감→execute→PAID+reference, 멱등 재실행, OPEN execute 409, seller-scope read 격리.
- API: `GET /periods/{id}/payouts` + `POST …/execute` 슬라이스/통합(ADMIN 게이트·404·409).

## Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing (`:settlement-service:test` GREEN)
- [ ] Contracts honored (settlement-api.md payouts 부분)
- [ ] Specs already updated (TASK-BE-414) — no further spec change needed
- [ ] Depends-on BE-415 merged before implementation
- [ ] Ready for review
