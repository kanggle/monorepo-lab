# Task ID

TASK-BE-361

# Title

shipping-service: carrier webhook dedup-marker retention/cleanup sweep (ADR-007 D5-4)

# Status

review

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

TASK-BE-294 는 carrier webhook 멱등을 위해 `processed_carrier_webhooks`(V5) 테이블에 `deliveryId` dedup-marker 를 영구 적재한다. 이 테이블은 **무한 증가**하며, 의도적으로 `processed_events`(이벤트 dedup) 와 분리돼 있어 기존 event cleanup 스케줄러가 건드리지 않는다(보존 정책이 다름). 이 task 는 webhook dedup-marker 전용 **retention/cleanup sweep** 을 추가해, 멱등 보장 윈도우를 넘긴 오래된 marker 를 주기적으로 제거한다.

이 task 가 끝나면: "webhook dedup 테이블이 보존 기간(retention window)을 넘은 marker 를 주기적으로 정리해 무한 성장하지 않으면서도, 윈도우 내 재전송(carrier retry)은 여전히 멱등하게 무시한다"가 참이 된다.

---

# Scope

## In Scope (shipping-service only)

- `processed_carrier_webhooks` 전용 cleanup 스케줄러: `processed_at`(또는 등가 컬럼) 이 retention window 보다 오래된 row 삭제. 윈도우는 carrier 재전송 최대 지연보다 충분히 길게(설정값).
- 설정: `shipping.carrier.webhook.cleanup.{enabled,fixed-delay-ms,retention-days,batch-size}` — `enabled` 기본값은 운영 안전선에서 결정(증가 억제가 목적이므로 true 후보; 단 net-zero 원칙상 기본 false + 문서로 운영 활성화 권고도 가능 — 구현 시 ADR/운영 관점 한 줄 근거 명시).
- 다중 인스턴스 단일 실행(ShedLock) — sweep 중복 삭제 무해하지만 부하 절감 위해 권장.
- 대량 삭제는 batch 로(한 트랜잭션 폭주 금지).
- 관측: tick 당 삭제 건수 metric.
- (필요 시) `processed_at` 인덱스 추가 마이그레이션(삭제 쿼리 효율).

## Out of Scope

- 중개사 어댑터/credential (→ TASK-BE-362).
- gateway public-route (→ TASK-BE-359).
- auto-collect tracking 스케줄러 (→ TASK-BE-360).
- `processed_events`(이벤트 dedup) 테이블 — 별도 보존 정책, 건드리지 않음.
- 멱등 검증 로직(BE-294 `registerIfFirst`) 변경.

---

# Acceptance Criteria

- [ ] AC-1 — retention window 초과 marker 가 sweep 으로 삭제된다(배치 단위).
- [ ] AC-2 — 윈도우 **내** marker 는 보존 → carrier 재전송(같은 `deliveryId`)이 여전히 DUPLICATE no-op(멱등 유지).
- [ ] AC-3 — `processed_events` 테이블은 영향 없음(분리 확인).
- [ ] AC-4 — 다중 인스턴스 동시 tick 시 단일 실행(ShedLock) 또는 중복 삭제 무해 입증. 대량 삭제가 batch 로 제한.
- [ ] AC-5 — `:shipping-service:check` BUILD SUCCESSFUL; CI Build & Test GREEN. 기본 설정에서 기존 동작 회귀 0.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` + `rules/common.md` + `rules/traits/{integration-heavy,batch-heavy}.md`.

- `projects/ecommerce-microservices-platform/docs/adr/ADR-007-logistics-aggregator-carrier-integration.md` (D5-4)
- `projects/ecommerce-microservices-platform/specs/services/shipping-service/overview.md`
- `projects/ecommerce-microservices-platform/PROJECT.md` (traits)

# Related Skills

- `.claude/skills/backend/...` (스케줄러/cleanup 관련 — INDEX 확인)

---

# Related Contracts

- (없음 — 내부 보존 정책. 외부 계약 변경 없음)

---

# Target Service

- `shipping-service`

---

# Implementation Notes

- **ShedLock IT 트랩(재사용 경고)**: IT 비즈니스 검증은 sweep use-case 를 **직접 호출**해 락을 우회할 것(TASK-BE-360 동일 주의).
- retention window 는 carrier/중개사의 최대 재전송 지연 + 안전 여유로 산정 — 너무 짧으면 AC-2(멱등) 위반.

---

# Edge Cases

- 빈 테이블 / 삭제 대상 0건: 즉시 종료.
- 매우 큰 누적: batch_size 로 분할, tick 반복으로 점진 정리(한 번에 전부 삭제 시도 금지).
- 경계값(정확히 retention 경계의 row): 일관된 비교(>= vs >) 명시.

---

# Failure Scenarios

- **F1 — 멱등 윈도우 침해**: 너무 짧은 retention 으로 윈도우 내 marker 삭제 → carrier 재전송이 재처리(이중 전진). → AC-2 + 충분한 window.
- **F2 — 대량 삭제 락/부하**: 단일 트랜잭션 대량 DELETE. → AC-4 batch.
- **F3 — 잘못된 테이블 정리**: `processed_events` 오삭제. → AC-3 분리 확인.

---

# Test Requirements

- unit: sweep 직접 호출(윈도우 초과 삭제 / 윈도우 내 보존 / batch 제한).
- (선택) IT: Testcontainers 로 실제 삭제 + 멱등 유지 검증(sweep 직접 호출).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added & passing
- [ ] 멱등 윈도우 유지 확인(AC-2)
- [ ] processed_events 미영향 확인
- [ ] Ready for review
