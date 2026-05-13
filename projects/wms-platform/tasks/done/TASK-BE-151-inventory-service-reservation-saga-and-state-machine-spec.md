# Task ID

TASK-BE-151

# Title

inventory-service `sagas/reservation-saga.md` + `state-machines/reservation-status.md` authoring — 3 intentional (Open Item) marker closure (BE-149 carry-out, WMS `(Open Item)` 마커 0 종결)

# Status

done

# Owner

wms-platform

# Task Tags

- wms
- inventory-service
- spec
- saga
- state-machine
- backfill
- transactional
- be

---

# Goal

[TASK-BE-149](../done/TASK-BE-149-services-open-item-marker-batch-cleanup.md) (PR #492, 2026-05-14 머지) 가 services/ 의 stale `(Open Item)` 마커 46개를 17 file 범위로 일괄 cleanup 했으나, **3 marker 만 의도된 placeholder 로 보존**: 실제로 미존재 file 을 가리키므로 별 task closure 후보로 명시:

- `projects/wms-platform/specs/services/inventory-service/architecture.md:512-513` — `sagas/reservation-saga.md (Open Items)`
- `projects/wms-platform/specs/services/inventory-service/architecture.md:554-555` — `state-machines/reservation-status.md (Open Items)`
- `projects/wms-platform/specs/services/inventory-service/domain-model.md:621-622` — `state-machines/reservation-status.md (Open Item from architecture.md)`

이 상태는:

- **spec layer 7 (services) 의 internal commitment vs 실 file 부재 drift** — `architecture.md § Open Items` (L577-602) 의 #6/#7 이 두 file 을 "Before First Implementation Task" prerequisite 로 선언했으나, BE-046 admin-service bootstrap / inventory-service 가 이미 production 운영 중인 시점에서 backfill 미완 상태. CLAUDE.md § Core Principles ("Specifications are the source of truth") 위반 잠재.
- **portfolio cleanup 시리즈 (MONO-085/086 + BE-147/148/149/150) 의 자연 종결 pending** — BE-150 commit message: "combined with BE-147/148/149, wms specs `(Open Item)` marker count is now: original ~74 → **3** (intentional only)". 본 task 가 그 잔존 3 을 해소하면 WMS 전체 `(Open Item)` 마커 = **0**.

본 task = 2 신규 spec authoring (~90-110 line each) + architecture.md / domain-model.md 의 3 marker 제거 + § Open Items 의 #6/#7 ✅ 표기. **production code = 0 변경** — 기존 inline declaration 의 dedicated 파일화 (retrospective consolidation).

retrospective spec authoring 답습 패턴: [TASK-BE-145](../done/TASK-BE-145-notification-service-idempotency-spec-and-dlt-replay-runbook.md) (notification idempotency.md + runbooks/dlt-replay.md, 2 신규 file ~350 line 동시 backfill, BE-043 bootstrap merged 후 retrospective). [TASK-BE-147](../done/TASK-BE-147-tms-shipment-api-vendor-wire-spec.md) (tms-shipment-api.md vendor wire-level, BE-049 production code 의 retrospective spec). 본 task = same-day single-PR closure 12번째 entry 후보 (BE-141/142/FAN-BE-006/MONO-084/BE-281/BE-145/146/147/148/149/150 precedent).

---

# Scope

## In Scope

### A. 신규 spec authoring: `inventory-service/sagas/reservation-saga.md`

대상 path: `projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md` (directory 신설).

답습 sibling pattern: `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md` (1100+ line). **방향 inverse** — outbound 가 orchestrator (state-keeper) 인 반면 inventory 는 choreographed participant 이므로 section 구성 압축.

표준 section (~90-110 line, ~9 section):

1. **`# inventory-service — Reservation Saga (Participant View)`** + 1-2 paragraph 도입 (inventory 의 saga 내 역할, choreographed participant 명시, 본 file = canonical reference, implementation must match).
2. **`## 1. Saga Overview`** — 1.1 Purpose (Reservation 의 lifecycle, W4/W5 deliveries) / 1.2 Participation Pattern (callee, never orchestrator — sibling outbound-saga.md § 1.4 인용) / 1.3 Owned vs Foreign Aggregates (Reservation + ReservationLine + Inventory 가 owned; outbound's PickingRequest / Order / OutboundSaga 가 foreign).
3. **`## 2. Operations (per-event)`** — 3 sub-section:
   - 2.1 **Reserve** — trigger=`outbound.picking.requested`, atomic actions inside `@Transactional` (Reservation insert + per-line Inventory.reserve(qty, reservationId) + 2N Movement rows + Outbox `inventory.reserved`), idempotency=eventId via `event_dedupe`, failure=INSUFFICIENT_STOCK → Inventory.adjusted{reason=INSUFFICIENT_STOCK} (RESERVE_FAILED outcome).
   - 2.2 **Release** — trigger=`outbound.picking.cancelled` OR TTL job OR INVENTORY_ADMIN manual, atomic actions (Reservation.release(reason) + per-line Inventory.release(qty, reservationId, reason) + 2N Movements + Outbox `inventory.released`), idempotency (same picking_request_id → no-op if already RELEASED), 3 reason enum (CANCELLED / EXPIRED / MANUAL).
   - 2.3 **Confirm** — trigger=`outbound.shipping.confirmed`, atomic actions (Reservation.confirm() + per-line Inventory.confirm(qty, reservationId) + N Movement rows + Outbox `inventory.confirmed`), idempotency, RESERVATION_QUANTITY_MISMATCH 강제 (v1 no partial shipments).
4. **`## 3. TTL Expiry (Category D — ADR-MONO-005 § D6)`** — Inventory v1 의 유일한 Category D reference implementation. job interval 60000ms + batch 200 + per-row `@Transactional` (one failure does not abort batch) + OL race retry next tick + `inventory.reservation.expiry.swept.total` counter (ADR § D5). domain-method 호출 = `ReleaseReservationService.releaseExpired()`, reason=`EXPIRED`.
5. **`## 4. Compensation`** — single rule: 모든 non-confirmed Reservation 의 compensation = Release (W4 의 atomic property). 한 번 CONFIRMED 면 compensation 불가 — outbound 의 RMA inbound 가 별 saga 로 모델링 (v2). cross-aggregate compensation 없음 (Reservation 만 보상하면 Inventory bucket 은 follow).
6. **`## 5. Concurrency / Idempotency Guarantees`** — Reservation `version` (OL retry per use-case) + `event_dedupe` table (T8 eventId dedupe, 30d retention) + `picking_request_id` UNIQUE (T4 — replay 시 no-op) + Inventory bucket OL (rare race, retry next tick). saga step 사이 cross-service sync RPC 0.
7. **`## 6. Failure Modes`** — 4 case table: INSUFFICIENT_STOCK / event order race (out-of-order `picking.cancelled` 도착 시) / outbox publish 실패 (TX 동일 → retry) / Postgres outage (consumer fail-closed retry / DLT).
8. **`## 7. Observability`** — Micrometer counter + saga-correlated trace span attribute (`saga.id`, `picking_request_id`) + log structure (`reservation_id`, `event_type`, `outcome`).
9. **`## 8. References`** — `architecture.md § Saga Participation (v1 light)` (line 494-510) + `domain-model.md § 3 Reservation` (line 251-353) + `state-machines/reservation-status.md` (companion, 본 task B) + sibling outbound-saga.md (orchestrator side) + ADR-MONO-005 § D6 (TTL Category D) + `rules/traits/transactional.md` Required Artifact 2 (saga compensation spec) + `rules/domains/wms.md` Required Artifact 5 + `specs/contracts/events/inventory-events.md` § 4 / § 5 / § 6 (inventory.reserved / .released / .confirmed).

### B. 신규 spec authoring: `inventory-service/state-machines/reservation-status.md`

대상 path: `projects/wms-platform/specs/services/inventory-service/state-machines/reservation-status.md` (directory 신설).

답습 sibling pattern: `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md`.

표준 section (~80-100 line, ~6 section):

1. **`# inventory-service — Reservation State Machine`** + 1-2 paragraph 도입 (canonical SM for Reservation aggregate, implementation must match, T4 enforcement — direct status UPDATE 금지, domain method `confirm()` / `release()` 만 transition).
2. **`## States`** table (3 row: RESERVED / CONFIRMED / RELEASED) — 각 row 의 컬럼: state / terminal / triggered by / description.
3. **`## Transitions`** — ASCII diagram (architecture.md L533-543 의 inline diagram 답습 + mermaid 추가) + per-transition table (5 row: RESERVED→CONFIRMED via confirm() / RESERVED→RELEASED via release(CANCELLED) / RESERVED→RELEASED via release(EXPIRED) / RESERVED→RELEASED via release(MANUAL) / forbidden: 모든 terminal regression).
4. **`## Invariants`** — terminal-once (no reactivation, ever) + `expires_at > created_at` + `picking_request_id` UNIQUE + cross-line: 모든 line 은 같은 warehouse_id + 모든 line 의 inventory_id 는 본 reservation 에 unique + RESERVED 상태 동안 `inventory.reserved_qty >= line.quantity` 보장.
5. **`## Forbidden Transitions / Patterns`** — direct status UPDATE 금지 (T4) / CONFIRMED→RELEASED 금지 / RELEASED→CONFIRMED 금지 / 어떤 terminal→initial regression 금지 / line 의 status 독립 mutation 금지 (aggregate 통째로 transition).
6. **`## References`** — `domain-model.md § 3 Reservation` (line 251-353, 특히 § State Machine 와 § Invariants) + `sagas/reservation-saga.md` (companion, 본 task A) + `architecture.md § State Machines § Reservation lifecycle` (line 529-547) + sibling outbound-service saga-status.md (template) + `rules/traits/transactional.md` T4 / T7 + `specs/contracts/events/inventory-events.md` § 4-6.

### C. `architecture.md` / `domain-model.md` (Open Item) marker 제거

대상:

- `architecture.md:512-513` "Full saga document: `specs/services/inventory-service/sagas/reservation-saga.md`\n(Open Items)." → "(Open Items)." 제거 + markdown link 활성화.
- `architecture.md:554-555` "Diagram in: `specs/services/inventory-service/state-machines/reservation-status.md`\n(Open Items)." → 동일.
- `domain-model.md:621-622` "The standalone diagram lives at `specs/services/inventory-service/state-machines/reservation-status.md`\n(Open Item from `architecture.md`)." → 동일.
- `architecture.md § Open Items` (L577-602) 의 #6 (sagas/reservation-saga.md) + #7 (state-machines/reservation-status.md) → ✅ 마커 + 신규 file link.
- `domain-model.md § Open Items` (L676-690) 의 sagas/reservation-saga.md 와 state-machines/reservation-status.md 항목 → 동일 ✅.

### D. WMS-specific concerns

- inventory-service 가 portfolio 의 Category D reference implementation (ADR-MONO-005) — TTL expiry sweeper 가 본 spec 의 § 3 의 source of truth 가 됨. ADR § D6 의 inventory 행과 cross-ref 정합 필요.
- Reservation 의 W4/W5 deliver 가 portfolio 의 transactional trait 의 핵심 demo — `outbound-service/sagas/outbound-saga.md` 의 inventory-side 참조 행이 본 spec 의 § 2 / § 3 / § 4 와 byte-identical 해야 (드리프트 0).

## Out of Scope

- production code 변경 — domain method / repository / consumer / TTL job 무수정. 본 task = spec authoring only.
- `inventory-service/database-design.md` (architecture.md § Open Items #1) — 별 task 후보. tables/indexes/partial-unique/role grants 의 physical 명세.
- `inventory-service/external-integrations.md` (architecture.md § Open Items #8) — integration-heavy Required Artifact 1, v1 vendor 0 state declaration. 별 task.
- `platform/error-handling.md` 의 신규 error code 등록 (architecture.md § Open Items #9) — 별 task.
- `gateway-service` 의 `/api/v1/inventory/**` 라우트 등록 (architecture.md § Open Items #10) — 별 task.
- Reservation 의 v2 partial-confirm — `domain-model.md § 3 Reservation` 가 v1 단일 atomic 만 declared, 본 spec 도 동일하게 v1 scope 유지.

---

# Acceptance Criteria

### Impl PR

- [ ] `projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md` 신규 file (~90-110 line, 9 section 표준).
- [ ] `projects/wms-platform/specs/services/inventory-service/state-machines/reservation-status.md` 신규 file (~80-100 line, 6 section 표준).
- [ ] 신규 file 들의 inline content 가 `architecture.md § Saga Participation` (L494-510) + `domain-model.md § 3 Reservation` (L251-353) 의 inline declaration 과 byte-identical (전이 / domain method 이름 / event 이름 / reason enum / TTL 파라미터).
- [ ] `architecture.md:512-513` "(Open Items)." 제거 + markdown link 활성화.
- [ ] `architecture.md:554-555` "(Open Items)." 제거 + markdown link 활성화.
- [ ] `domain-model.md:621-622` "(Open Item from `architecture.md`)." 제거 + markdown link 활성화.
- [ ] `architecture.md § Open Items` (L577-602) #6 / #7 ✅ 마커 + link.
- [ ] `domain-model.md § Open Items` (L676-690) 의 sagas/reservation-saga.md + state-machines/reservation-status.md 항목 ✅ 마커 + link.
- [ ] cross-ref 검증 — 신규 spec 의 인용 path (outbound-saga.md / inventory-events.md / domain-model.md / ADR-MONO-005 / traits) 모두 dead-reference 0.
- [ ] HARDSTOP-03 PASS — wms project-specific spec (shared paths 무관).
- [ ] CI markdown-only path-filter (15 SKIP + 1 changes PASS) — TASK-MONO-075 자연 검증.
- [ ] `grep -rn "(Open Item" projects/wms-platform/specs/` → **0 결과** (WMS 전체 marker 0 확정).
- [ ] task lifecycle ready → review 직접 (in-progress 우회, same-day single-PR closure 12번째 entry — BE-147/148/149/150 immediate precedent).
- [ ] wms `tasks/INDEX.md` 동기 (`## ready` 제거 / `## review` append).

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] wms `tasks/INDEX.md` `## review` 제거, `## done` append outcome.

---

# Related Specs

- `projects/wms-platform/specs/services/inventory-service/architecture.md` § Saga Participation (v1 light) (L494-510), § Saga / Long-running Flow (ADR-MONO-005) (L517-525), § State Machines § Reservation lifecycle (L529-547), § Open Items #6 #7 (L577-602).
- `projects/wms-platform/specs/services/inventory-service/domain-model.md` § 3 Reservation (L251-353, 특히 § State Machine L297-308 / § Invariants L320-339 / § Quantity-mismatch Handling L340-345), § State Machines (Cross-reference) (L617-628), § Open Items (L676-690).
- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md` — orchestrator-side counterpart (inventory 가 participant). Choreographed pattern (§ 1.4) + Reserve / Release / Confirm 의 cross-service event flow.
- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md` — sibling state-machine pattern (template).
- `projects/wms-platform/specs/contracts/events/inventory-events.md` — § 4 `inventory.reserved` / § 5 `inventory.released` / § 6 `inventory.confirmed` event schemas.
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` — § D6 inventory TTL Category D reference implementation.
- `rules/traits/transactional.md` — Required Artifact 2 (saga compensation spec) + T4 (no direct status UPDATE) + T7 (saga atomicity) + T8 (event dedupe).
- `rules/domains/wms.md` — W4 (reserve→confirm), W5 (no decrement until shipped), Required Artifact 5.

# Related Contracts

- (참조만, 변경 없음) `projects/wms-platform/specs/contracts/events/inventory-events.md` — 본 spec 의 § 2 (Operations) 가 인용하는 3 event 의 schema 가 contract 의 § 4-6 와 정합.
- (참조만, 변경 없음) `projects/wms-platform/specs/contracts/events/outbound-events.md` — outbound 의 picking.requested / picking.cancelled / shipping.confirmed 의 schema 가 본 spec 의 § 2 의 trigger event 이름과 정합.
- 답습 sibling = `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md` + `state-machines/saga-status.md`.

# Edge Cases

- **out-of-order event delivery**: `outbound.picking.cancelled` 가 `outbound.picking.requested` 보다 먼저 도착 (rare due to single-partition ordering) — § 6 Failure Modes 가 명시: consumer 가 cancel 의 reservation_id 를 못 찾으면 `RESERVATION_NOT_FOUND` → DLT (별 retry 시도 후). request 가 늦게 도착하면 정상 Reserve 실행 → 이후 manual release 또는 TTL 으로 정리.
- **partial confirm**: `outbound.shipping.confirmed` 가 reserved 수량 < shipped 수량 (over-ship) — `RESERVATION_QUANTITY_MISMATCH` 강제 throw, consumer 가 DLT 전환. v1 simplification — partial shipment 가 upstream 에서 release + new picking request 로 모델링됨 (§ 2.3 / § 4 명시).
- **double cancel**: same `picking_request_id` 의 cancel 이 2번 publish (vendor retry) — `event_dedupe` table 이 dedupe (T8). state machine 측면 RELEASED→RELEASED transition 은 forbidden 이므로 domain method 가 no-op (idempotent).
- **TTL race with manual cancel**: TTL job 이 RESERVED row 잠그는 순간 manual cancel event consumer 가 같은 row 잠금 시도 — OL retry (`version` field) 로 한 transaction 만 성공, 다른 한 transaction 은 retry 후 RELEASED 상태 발견 → no-op.
- **CONFIRMED row 에 TTL 이 도달**: TTL job 의 query 가 `WHERE status = 'RESERVED' AND expires_at < NOW()` — CONFIRMED 는 select 에서 제외되어 race 회피. § 3 명시.

# Failure Scenarios

- **inline declaration 과 spec 의 drift**: 본 task 는 architecture.md / domain-model.md 의 inline 선언을 source 로 사용 — spec 작성 시점에 inline source 를 직접 reference 하여 byte-identical 보장. 추후 inline 이 변경되면 spec 갱신은 변경 PR 의 의무.
- **(Open Item) marker 의 의도하지 않은 잔존**: architecture.md / domain-model.md 의 3 위치를 explicit 하게 정리 — Acceptance Criteria 의 3 checkbox 가 강제.
- **신규 spec 의 dead reference**: 인용한 sibling spec path / contract path / ADR path 가 모두 실재해야 — Acceptance Criteria 의 cross-ref 검증 checkbox 가 강제 (TASK-MONO-085/086 의 dead-reference batch 학습 답습).
- **scope creep — 다른 Open Item closure 시도**: architecture.md § Open Items #1 (database-design.md), #8 (external-integrations.md), #9 (error codes), #10 (gateway route) 는 본 task 변경 없음 — 별 task 후보.

# Notes

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (retrospective spec authoring routine, sibling pattern 답습 가능, BE-145/147 precedent). 단 byte-identical 검증 + cross-ref accuracy 가 핵심 risk 라 backend-engineer 또는 api-designer subagent dispatch 권장.
- 본 task 의 single-PR closure 시 D4 churn freeze 영향 0 (project-internal spec, project-specific only).
- `project_mono_085_dead_reference_batch.md` 메모리 § "남은 backlog" 의 단일 entry **이미 BE-147 closure 로 0 갱신** — 본 task 는 BE-149 의 carry-out (다른 backlog) 해소.
- 답습 same-day single-PR closure 패턴 (BE-141/142/FAN-BE-006/MONO-084/BE-281/BE-145/146/147/148/149/150) — 본 task = 12번째 entry.
- 본 task 머지 후 `project_mono_085_dead_reference_batch.md` 메모리 또는 신규 메모리 entry 에 "WMS (Open Item) 마커 = 0 종결" 명시 적절.
