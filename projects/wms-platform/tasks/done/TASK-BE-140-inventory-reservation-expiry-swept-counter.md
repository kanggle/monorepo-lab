# Task ID

TASK-BE-140

# Title

inventory-service `inventory.reservation.expiry.swept.total` Micrometer counter — ADR-MONO-005 § D5 Category D minimal compliance (cosmetic gap)

# Status

ready

# Owner

backend / wms

# Task Tags

- code
- test
- adr

---

# Goal

[ADR-MONO-005](../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) § D5 mandates that **Category D (TTL expiry sweep)** flows emit a per-tick metric `<service>.<job>.swept.total` for observability. The reference implementation — `inventory-service.ReservationExpiryJob` — currently logs the released count but does NOT expose a Micrometer counter (the gap is documented in ADR § D6 inventory row + § 5 follow-up table as **TASK-BE-140 DEFERRED, cosmetic**).

This task adds the missing counter and flips the inventory row in ADR § D6 + § 5 to Compliant. Closes the last outstanding ADR-MONO-005 follow-up.

---

# Scope

## In Scope

- Add a `Counter` named `inventory.reservation.expiry.swept.total` to `ReservationExpiryJob`. Increment by `released` (count of successfully transitioned RESERVED → RELEASED rows) at the end of each `runOnce()` tick.
- Constructor change: inject `MeterRegistry`, register the counter once.
- Test: extend `ReservationExpiryJobTest` with a `SimpleMeterRegistry` and assert counter values for the existing scenarios (no-op tick, full-batch release, mid-batch failure).
- ADR-MONO-005 updates:
  - § D6 inventory row: cosmetic gap note removed; Status remains Compliant; Follow-up → ✅ MERGED.
  - § 5 Outstanding follow-ups: TASK-BE-140 row → ✅ MERGED.
  - History line append.
  - § 1.1 audit row 7 (inventory) Dead-letter column updated to mention the counter.
- `projects/wms-platform/specs/services/inventory-service/architecture.md` § "Saga / Long-running Flow" inventory row: cosmetic gap note removed.

## Out of Scope

- Adding additional metrics (e.g. skipped count, failure breakdown). Only the ADR-mandated `swept.total` counter.
- Changing `runOnce()` return semantics or scheduler behaviour.
- Touching other Category D candidates (none exist in the monorepo today).

---

# Related Specs

- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` § D5 (Category D sub-rules), § D6 inventory row, § 5 follow-up table
- `projects/wms-platform/specs/services/inventory-service/architecture.md` § Saga / Long-running Flow

# Related Contracts

- 없음 — observability metric only, no contract surface.

---

# Acceptance Criteria

- AC-01: `inventory.reservation.expiry.swept.total` Micrometer counter is registered and increments by `released` at the end of each `runOnce()` tick.
- AC-02: `ReservationExpiryJobTest` covers (a) zero increment when no rows expired, (b) increment by N for a successful N-row sweep, (c) increment by 2 (not 3) when one row in a 3-row batch fails per existing scenario.
- AC-03: ADR-MONO-005 § D6 inventory row + § 5 follow-up + History updated; § 1.1 audit row 7 Dead-letter column references the counter.
- AC-04: `architecture.md` Saga row cosmetic note removed.
- AC-05: `./gradlew :projects:wms-platform:apps:inventory-service:test` PASS — no regression on existing 4 `ReservationExpiryJobTest` methods.
- AC-06: Conventional Commit scope: `feat(wms/inventory)+adr(mono-005)+task(be-140)`.

---

# Edge Cases

- **EC-01**: `runOnce()` on empty result returns 0 — counter increment by 0 is a no-op; `Counter.increment(0)` is safe per Micrometer contract.
- **EC-02**: Per-row release failure (StateTransitionInvalid / OL race / generic) is already isolated; counter reflects only successful releases, matching the `released` return value.
- **EC-03**: Multiple sweeper replicas → each increments locally; Micrometer registry sums across replicas at scrape time (Prometheus-side aggregation).

---

# Failure Scenarios

- **FS-01**: MeterRegistry not present in test context. Mitigation: `SimpleMeterRegistry` constructed in unit test directly (no Spring wiring).
- **FS-02**: Counter naming drift from ADR § D5 pattern. Mitigation: AC-01 specifies the exact name, matching `<service>.<job>.swept.total` template.

---

# Test Requirements

- unit: `ReservationExpiryJobTest` extended with counter assertions.

---

# Definition of Done

- [ ] AC-01..AC-06 satisfied
- [ ] inventory-service:test PASS
- [ ] ADR + architecture spec updated
- [ ] CI green

---

# Recommendation

진행 권장 (분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — single counter + 4 test method tweak + ADR/architecture row edits. ~50 LOC total. Cosmetic per ADR § 5.)
