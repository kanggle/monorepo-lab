# Task ID

TASK-BE-286

# Title

inventory-service reservation-saga.md + reservation-status.md sibling shape backfill (refactor-spec Tier 2 F-01+F-02 closure)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- inventory-service
- spec
- sibling-emulation
- missing-section
- refactor-spec

---

# Goal

`/refactor-spec all --dry-run` non-deadref **Tier 2 F-01 + F-02 paired closure** — inventory-service 의 saga + state-machine spec 이 sibling shape 와 divergent.

## Finding analysis

**F-01 reservation-status.md (state-machine)** — sibling 4 file (asn-status / order-status / saga-status / shipment-status) 답습 결과 5 sections 누락:

| Sibling section | reservation-status 현재 | Backfill 결정 |
|---|---|---|
| `## States` | ✓ 보유 | unchanged |
| `## Transitions` | ✓ 보유 | unchanged |
| `## Transition Rules` | ❌ (`## Invariants` 가 일부 cover) | **신규 author** (transition-level precondition + side-effect rule) |
| `## Guard Conditions` | ❌ (`## Forbidden Patterns (in code)` 가 inverse cover) | **신규 author** (pre-transition guard) |
| `## Concurrency` | ❌ | **신규 author** (version + OL retry, reservation-saga.md cross-ref) |
| `## Reverse / Compensation Flows` | ❌ | **신규 author** (RELEASED/EXPIRED reverse 불가 명시) |
| `## Error-Code Mapping` | ❌ | **신규 author** (RESERVATION_NOT_FOUND / RESERVATION_ALREADY_RELEASED / etc.) |
| `## Test Requirements` | ❌ | **신규 author** (reservation-saga.md § Test Matrix cross-ref + state-level unit test) |
| `## Invariants` | ✓ 보유 (reservation-specific) | unchanged (sibling 에 없는 reservation-only section) |
| `## Forbidden Patterns (in code)` | ✓ 보유 (reservation-specific) | unchanged |
| `## References` | ✓ 보유 | unchanged |

**F-02 reservation-saga.md (saga)** — sibling outbound-saga.md 답습 결과 2 sections 누락 + 일부 rename (rename 보존 결정):

| Sibling section (outbound-saga) | reservation-saga 현재 | Backfill 결정 |
|---|---|---|
| `## 1. Saga Overview` | ✓ § 1 | unchanged |
| `## 2. Saga Steps` | `## 2. Operations (per-event)` | **rename 보존** (event-driven choreography 명시, sibling 의 orchestrated 와 다른 architectural reality) |
| `## 3. Compensation Paths` | `## 4. Compensation` | rename 보존 |
| `## 4. Saga Recovery (Sweeper)` | (없음 — Category D TTL 으로 대체) | **skip** (architectural difference — reservation 은 TTL expiry, sweeper 없음) |
| `## 5. Concurrency Handling` | `## 5. Concurrency / Idempotency Guarantees` | rename 보존 (Idempotency 추가) |
| `## 6. Failure Taxonomy` | `## 6. Failure Modes` | rename 보존 |
| `## 7. Observability` | ✓ § 7 | unchanged |
| `## 8. Test Matrix` | ❌ | **신규 author** (TTL expiry test + concurrency OL retry test + idempotency test + compensation test) |
| `## 9. Open Questions` | ❌ | **신규 author** ("no open questions" 또는 v2 candidate marker) |
| `## 10. References` | `## 8. References` | renumber to § 10 (마지막) |

## Judgment rationale

- **renames 보존**: reservation 의 event-driven choreography (Operations per-event) + idempotency guarantees + failure modes 의 의미 차별화는 architectural reality. sibling 답습이 의미 손실. **renames stay**.
- **state-machine missing sections backfill**: 5 sections 모두 backfill (Test Requirements + Error-Code + Concurrency + Guard Conditions + Transition Rules + Reverse Flows = 6 sections). sibling 답습이 state-machine implementer 가 reservation 만 단독으로 읽을 때 contract 명확화. saga 의존 부분은 cross-ref 명시.
- **0 production code change**: 본 task 는 spec authoring only (additive missing-section). refactor-spec slash-command § Constraints 의 "no requirement changes / no contract changes / no new decisions" 준수 — 기존 implementation 의 implicit 한 rules 를 explicit spec 으로 정리만.

# Scope

## In Scope

- `projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md` — 신규 § 8 Test Matrix + § 9 Open Questions sections (sibling outbound-saga § 8/9 답습 + reservation-specific test content) + § References renumber to § 10.
- `projects/wms-platform/specs/services/inventory-service/state-machines/reservation-status.md` — 신규 6 sections (Transition Rules / Guard Conditions / Concurrency / Reverse Flows / Error-Code Mapping / Test Requirements) sibling 답습 + reservation-specific content. 기존 § Invariants + § Forbidden Patterns 보존.

## Out of Scope

- saga + state-machine 의 architectural rule 변경 (rename 보존 결정).
- 다른 inventory-service spec file (architecture.md / domain-model.md / database-design.md / etc.) — 본 cycle 의 F-01+F-02 finding 만.
- Tier 2 잔여 F-06+F-07 outbox/processed_events schema authority (HIGH risk, 별 cycle).
- production code 변경 (Reservation aggregate / ReservationService / Flyway V1-V5).

# Acceptance Criteria

- [ ] reservation-status.md 에 6 신규 sections 추가 (Transition Rules / Guard Conditions / Concurrency / Reverse Flows / Error-Code Mapping / Test Requirements).
- [ ] reservation-saga.md 에 2 신규 sections 추가 (§ 8 Test Matrix / § 9 Open Questions) + References § 8 → § 10 renumber.
- [ ] 모든 신규 sections 가 sibling shape 답습 + reservation-specific content (TTL Expiry, Category D, idempotency-key).
- [ ] reservation-saga.md ↔ reservation-status.md cross-reference 명시 (state-machine 의 test/concurrency 가 saga 로 위임 시).
- [ ] Production code / Reservation aggregate / Flyway / business logic 0 변경 (spec authoring only).
- [ ] `grep -n "^## " projects/wms-platform/specs/services/inventory-service/state-machines/reservation-status.md` = ≥ 10 H2 sections (sibling asn-status 9, order-status 9, saga-status 10 평균).

# Related Specs

- `projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md` (target)
- `projects/wms-platform/specs/services/inventory-service/state-machines/reservation-status.md` (target)
- `projects/wms-platform/specs/services/inventory-service/architecture.md` (reservation domain context)
- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md` (sibling shape reference — 10 H2)
- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md` (sibling shape reference, 10 H2)
- `projects/wms-platform/specs/services/inbound-service/state-machines/asn-status.md` (sibling shape reference, 9 H2)
- `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md` (sibling shape reference, 9 H2)
- ADR-MONO-005 § D6 Category D TTL Expiry sweep (reservation-saga.md § 3 reference)
- `.claude/commands/refactor-spec.md` § Operational Patterns (Tier 2 judgment closure pattern)

# Related Contracts

해당 없음 — saga + state-machine spec 본문 polish, contract 변경 0.

# Target Service

inventory-service (saga + state-machine spec authoring).

# Edge Cases

- A: 신규 § Concurrency 가 reservation-saga.md § 5 와 중복 가능성 — state-machine 은 state-transition level concurrency (version + OL retry), saga 는 cross-service idempotency-key. 명확히 cross-ref + 분담 표시.
- B: 신규 § Reverse Flows 가 v1 forbidden 인 경우 sibling asn-status / order-status 의 "(v1: forbidden)" 마커 답습 — RELEASED / EXPIRED 는 forward-only.
- C: 신규 § Test Requirements 가 reservation-saga.md § Test Matrix 와 중복 가능성 — state-machine 은 state transition unit test (unit only), saga 는 integration (Testcontainers) 분담. 명확히 분리.

# Failure Scenarios

- A: 신규 author 가 기존 production code 의 implicit rule 와 sync 안 됨 — Reservation aggregate / ReservationStatus enum / ReservationService 의 method 들 확인 + spec 에 정확한 implementation reference. mitigate: production code anchor 명시.
- B: rename 보존 vs sibling 답습 trade-off — F-02 의 renames (Operations / Compensation / Concurrency Guarantees / Failure Modes) 가 architectural difference 반영. backfill 시 sibling shape 으로 rename 시 의미 손실. mitigate: rename 보존 + 누락 sections backfill 결합.

# Validation Plan

1. Edit 후 `grep -cn "^## " projects/wms-platform/specs/services/inventory-service/state-machines/reservation-status.md` ≥ 10 (sibling asn 9 + order 9 + saga 10 평균).
2. Edit 후 `grep -cn "^## " projects/wms-platform/specs/services/inventory-service/sagas/reservation-saga.md` = 10 (sibling outbound 10 match).
3. `git diff --stat` ~2 file / ~300+ line additive insert.
4. 신규 sections 의 sibling shape 답습 검증 — random 3 sibling section 과 형식 비교.
5. Production code / Flyway / domain model / business logic 0 변경 검증.

# Implementation Notes

- 2 commit / 1 branch: (1) ready/ task author, (2) 2 file additive author + lifecycle move ready/ → review/.
- branch name `task/be-286-inventory-reservation-saga-status-shape-backfill` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- TASK-BE-165 / BE-283 / SCM-BE-013 / BE-284 / BE-285 / MONO-091 precedent (6 refactor-spec cycle siblings). 7번째 cycle task / Tier 2 judgment-additive.
- judgment-required Tier 2 라 author 시 sibling content 정확히 답습 + reservation-specific content 명시.

# Outcome

(완료 후 갱신)
