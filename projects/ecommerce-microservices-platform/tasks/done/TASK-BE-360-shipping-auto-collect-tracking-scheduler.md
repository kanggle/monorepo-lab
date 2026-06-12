# Task ID

TASK-BE-360

# Title

shipping-service: 무인 auto-collect tracking 스케줄러 (ADR-007 D5-3, ShedLock)

# Status

done

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-BE-293/294 는 carrier 추적을 **admin pull** + **중개사 push(webhook)** 두 trigger 로 구현했고, 두 task 모두 "무인 auto-collect 스케줄러"를 명시적으로 v2 로 유보했다(ShedLock 복잡도를 분리하기 위함). 이 task 는 in-flight 배송(`SHIPPED`/`IN_TRANSIT`)을 주기적으로 폴링해 `CarrierTrackingPort`(BE-293)로 최신 상태를 당겨 전진시키는 **무인 스케줄러**를 추가한다. webhook 이 유실/지연된 배송도 결국 수렴(eventual)하게 만드는 backstop.

이 task 가 끝나면: "운영자가 누르지 않아도, 중개사 webhook 이 안 와도, in-flight 배송이 주기적으로 추적되어 DELIVERED 로 수렴한다"가 참이 된다.

---

# Scope

## In Scope (shipping-service only)

- 주기 스케줄러 빈: in-flight 상태(`SHIPPED`, `IN_TRANSIT`)이며 `trackingNumber`/`carrier` 가 있는 배송을 batch 로 조회 → 각 건 `RefreshTrackingService`(또는 `ShippingForwardAdvancer` 경유) 로 forward-only 전진.
- **ShedLock** 으로 다중 인스턴스 단일 실행 보장(`@SchedulerLock`).
- 설정: `shipping.carrier.auto-collect.{enabled,fixed-delay-ms,batch-size}` — `enabled` 기본 **false**(net-zero; 켜야 동작). `mode=mock`+blank `mock-status` 면 켜도 실질 no-op.
- 관측: 1 tick 당 처리/전진/no-op 건수 metric.
- 테스트: use-case(sweep) **직접 호출** 단위 테스트(advance/forward-only/no-op), 빈 가드(enabled=false → 미동작).

## Out of Scope

- 중개사 어댑터 매핑/credential (→ TASK-BE-362).
- gateway public-route (→ TASK-BE-359).
- webhook dedup cleanup (→ TASK-BE-361).
- 도메인 전이 규칙·`ShippingStatusChanged` 계약 변경(기존 forward-only 재사용).
- 새 carrier 호출 경로 신설(BE-293 포트 재사용).

---

# Acceptance Criteria

- [ ] AC-1 — `enabled=true`(+ carrier 응답 ahead)에서 1 tick 이 in-flight 배송을 전진시키고 각 건 `ShippingStatusChanged` 발행. forward-only.
- [ ] AC-2 — ShedLock: 두 인스턴스 동시 tick 시 1회만 실행(나머지 lock 으로 skip).
- [ ] AC-3 — `enabled=false`(기본) → 스케줄러 미동작(net-zero). `mode=mock`+blank `mock-status` → 켜도 실질 no-op.
- [ ] AC-4 — carrier outage/미매핑/not-ahead → 해당 건 no-op, 다른 건 영향 없음, 크래시 없음(best-effort).
- [ ] AC-5 — `:shipping-service:check` BUILD SUCCESSFUL; CI Build & Test GREEN.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` + `rules/common.md` + `rules/traits/{integration-heavy,batch-heavy}.md`(스케줄러는 batch 성격 — 선언된 trait 확인).

- `projects/ecommerce-microservices-platform/docs/adr/ADR-007-logistics-aggregator-carrier-integration.md` (D5-3)
- `projects/ecommerce-microservices-platform/specs/services/shipping-service/overview.md` (auto-collect 스케줄러 = 잔여 v2)
- `projects/ecommerce-microservices-platform/PROJECT.md` (traits)

# Related Skills

- `.claude/skills/backend/...` (스케줄러/ShedLock 관련 — INDEX 확인)

---

# Related Contracts

- `specs/contracts/events/shipping-events.md` (`ShippingStatusChanged` 재사용, 불변)

---

# Target Service

- `shipping-service`

---

# Implementation Notes

- **ShedLock IT 트랩(재사용 경고)**: `@SchedulerLock(lockAtLeastFor=...)` 이면 IT 가 첫 호출만 실행하고 나머지를 silent no-op 처리할 수 있다 → **IT/단위 테스트는 use-case `sweep()` 를 직접 호출**해 락을 우회하고 비즈니스 로직만 검증할 것(scm 재고보충 루프에서 확립된 패턴).

---

# Edge Cases

- in-flight 0건: tick 이 빈 batch 로 즉시 종료.
- batch_size 초과 in-flight: 다음 tick 으로 이월(또는 batch 반복) — 무한 루프 금지.
- 같은 배송에 webhook 과 스케줄러가 동시 전진 시도: forward-only + 멱등 전이로 이중 전진 무해.

---

# Failure Scenarios

- **F1 — 다중 인스턴스 중복 실행**: 같은 배송을 두 번 호출. → AC-2 ShedLock.
- **F2 — net-zero 회귀**: 기본 enabled=true 로 무인 전진 폭주. → AC-3 기본 false.
- **F3 — 한 건 실패가 batch 전체 중단**: → 건별 best-effort, 예외 격리.

---

# Test Requirements

- unit: sweep 직접 호출(advance/forward-only/no-op/예외 격리), enabled=false 가드.
- (선택) IT: Testcontainers 로 ShedLock 단일 실행 — 단, 비즈니스 검증은 sweep 직접 호출로(트랩 회피).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added & passing
- [ ] net-zero(기본 false) 확인
- [ ] ShedLock 단일 실행 확인
- [ ] Ready for review
