# Task ID

TASK-BE-050

# Title

outbound-service Saga Sweeper — background recovery loop for stuck sagas

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Goal

Implement the **saga sweeper** declared in `outbound-service/architecture.md` §Saga Sweeper. A 1-minute scheduled job that finds outbound sagas stuck in non-terminal states beyond a grace period and re-emits the appropriate event so the saga can advance.

After this task is complete, a Kafka message lost between `outbound-service` and `inventory-service` (or vice versa) is recoverable via the saga's eventual-consistency guarantee — no manual intervention required.

---

# Scope

## In Scope

- New `application/saga/SagaSweeper.java` (or similar), `@Component @ConditionalOnProperty("outbound.saga.sweeper.enabled")`
- Scheduled execution every 1 minute (per spec §Saga Sweeper)
- Three recovery rules:
  1. Sagas in `REQUESTED` for > 5 minutes → re-emit `outbound.picking.requested` (idempotent at inventory consumer)
  2. Sagas in `CANCELLATION_REQUESTED` for > 5 minutes → re-emit `outbound.picking.cancelled`
  3. Sagas in `SHIPPED` for > 5 minutes without `inventory.confirmed` reply → re-emit `outbound.shipping.confirmed`
- Re-emission goes through the existing transactional outbox (per `transactional` T3) — no direct Kafka publish from the sweeper.
- Per saga, increment a `re_emit_count` column on `outbound_saga` table (Flyway migration). Cap at 5 attempts; after that, transition saga to a terminal `STUCK_RECOVERY_FAILED` state and emit alert `outbound.alert.saga.recovery.exhausted`.
- Metrics:
  - `outbound.saga.sweeper.run.count`
  - `outbound.saga.sweeper.recovery.fired{from_state}` (REQUESTED / CANCELLATION_REQUESTED / SHIPPED)
  - `outbound.saga.sweeper.exhausted.count`
- Standalone profile fallback: sweeper disabled (`outbound.saga.sweeper.enabled=false` in `application-standalone.yml`)
- Unit tests + Testcontainers IT covering all 3 recovery paths + exhaustion path

## Out of Scope

- Saga state-machine changes beyond the new `STUCK_RECOVERY_FAILED` terminal state
- TMS retry recovery (separate from saga sweeper — handled by manual retry endpoint TASK-BE-049)
- Cross-saga or fanout recovery (single-saga only)
- Distributed-lock for sweeper (runs as single instance per service replica; safe due to outbox idempotency at consumer side)

---

# Acceptance Criteria

- [ ] `SagaSweeper` runs every 1 minute when enabled.
- [ ] All 3 recovery rules fire correctly when grace period exceeded.
- [ ] Re-emission writes to outbox in a transaction with the `re_emit_count` increment.
- [ ] After 5 failed attempts, saga transitions to `STUCK_RECOVERY_FAILED` and emits alert event.
- [ ] All 3 metrics + 1 alert metric present and tagged correctly.
- [ ] Inventory consumer dedupe (existing `outbound_event_dedupe`) silently no-ops on re-emitted events that already arrived.
- [ ] Sweeper disabled by default in standalone profile.
- [ ] Unit tests for state-machine transitions; Testcontainers IT with stuck-saga fixtures + grace-period clock advance + outbox row assertion.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `rules/domains/wms.md` (Outbound bounded context)
- `rules/traits/transactional.md` (T3 outbox, T6 compensation, T8 dedupe)
- `specs/services/outbound-service/architecture.md` §Saga Sweeper, §Saga State Machine
- `specs/contracts/events/outbound-events.md` (re-emitted events follow same schema as initial emission)

# Related Skills

- `.claude/skills/backend/scheduled-tasks/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md` (post-MONO-049: use libs/java-messaging v2 outbox via this service's existing publisher)
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/outbound-events.md` — re-emitted events use same schema (no contract change)
- `specs/contracts/events/admin-events.md` — new `outbound.alert.saga.recovery.exhausted` event added (alert event spec update)

---

# Target Service

- `outbound-service`

---

# Architecture

Follow:

- `specs/services/outbound-service/architecture.md` §Saga Sweeper
- Hexagonal: sweeper lives in `application/saga/` (orchestrator concern)

---

# Implementation Notes

- Use Spring `@Scheduled(fixedDelay = 60000)` with `@ConditionalOnProperty`
- Sweeper queries `outbound_saga` via existing `SagaPersistencePort` (extend with `findStuck(...)` methods)
- Re-emission uses existing outbox writer — DO NOT add a direct Kafka publish path
- Each re-emission runs in its own transaction (`@Transactional(propagation = REQUIRES_NEW)` on the per-saga handler) — one failed re-emission must not block the rest
- Use a separate bean for the per-saga handler (cross-bean delegation) per the Spring AOP self-invocation lesson — see memory `feedback_refactor_code_baseline_it.md`
- Flyway version: find next version (already V13 used by TASK-BE-049 `tms_request_dedupe`); this would be V14 for `outbound_saga.re_emit_count`

---

# Edge Cases

- Saga is in non-terminal state but its order has been cancelled meanwhile → sweeper should detect order-state mismatch and skip recovery (alert instead).
- Multiple sweeper instances (replicas) racing on same saga → optimistic-lock on `outbound_saga.version` prevents double recovery; second instance retries on next tick.
- Clock skew between sweeper instance and DB → use DB clock (`now()` in query), not local instant.
- `inventory.confirmed` arrives DURING sweeper iteration → saga state advances; sweeper's load-then-act is on the prior state → optimistic-lock retry → noop.
- Outbox publisher backlog at re-emit time → sweeper still emits; publisher will eventually drain.

---

# Failure Scenarios

- DB unreachable during sweep → sweeper logs error, increments failure metric, retries on next tick.
- Outbox write succeeds but next sweep tick happens before publisher drains → another re-emit fires (idempotent at consumer).
- Saga repeatedly fails due to inventory-service outage → counter caps at 5, saga moves to `STUCK_RECOVERY_FAILED`, alert fires.
- Sweeper disabled in production by accident → no recovery, but no harm; manual ops re-emit available via existing admin tooling (out of scope).

---

# Test Requirements

- Unit: state-machine transitions for `STUCK_RECOVERY_FAILED`, re-emit-count cap.
- Integration (Testcontainers Postgres + Kafka):
  - Stuck `REQUESTED` saga (> 5 min) → re-emit `outbound.picking.requested` → asserted in outbox table.
  - Stuck `CANCELLATION_REQUESTED` → re-emit `outbound.picking.cancelled`.
  - Stuck `SHIPPED` → re-emit `outbound.shipping.confirmed`.
  - 5-attempt exhaustion → saga `STUCK_RECOVERY_FAILED`, alert event in outbox.
  - Idempotent at inventory consumer (re-emit of already-applied event is a no-op via `outbound_event_dedupe`).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (unit + IT)
- [ ] Tests passing locally and in CI Linux Integration job
- [ ] Flyway migration applied (V14 or next)
- [ ] Spec updated if any clarification needed (e.g., `outbound.alert.saga.recovery.exhausted` event in admin-events.md)
- [ ] Resilience4j NOT used (sweeper is internal scheduling, not external integration)
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-code wms outbound-service` dry-run (Manual Finding #3). Architecture spec §Saga Sweeper has declared this requirement since outbound-service inception; this task closes the implementation gap.

분석=Opus 4.7 / 구현 권장=Opus (saga state-machine + scheduled job + DB clock + cross-bean delegation patterns; recovery semantics).
