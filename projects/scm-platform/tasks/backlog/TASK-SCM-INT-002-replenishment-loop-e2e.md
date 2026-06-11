# Task ID

TASK-SCM-INT-002

# Title

Replenishment-loop E2E — Testcontainers cross-service (simulated wms alert → demand-planning suggestion → procurement DRAFT PO) **plus** federation-stack live proof (real wms alert → scm DRAFT PO). Closes ADR-MONO-027 Phase 2. impl/test.

# Status

backlog

# Owner

backend

# Task Tags

- test
- e2e
- integration

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **선행 (prerequisite)**: [TASK-SCM-BE-024](TASK-SCM-BE-024-demand-planning-service-bootstrap.md) + [TASK-SCM-BE-025](TASK-SCM-BE-025-demand-planning-procurement-materialization.md) merged. **backlog → ready after both.**
- **sibling**: [TASK-SCM-INT-001](../done/TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md) (scm's first cross-service E2E — env/seed patterns to reuse) + federation-hardening-e2e (root) wiring precedent.

# Goal

Prove the full replenishment loop end to end, with a **deterministic PR-gated** leg (Testcontainers) and a **federation live** leg, so the cross-project coupling is guarded against silent regression (federation-e2e is nightly-only — memory `project_adr023_plane_separation_fed_e2e` trap #1: a nightly-only guard lets regressions leak in).

# Scope

## In Scope

### 1. Deterministic Testcontainers cross-service E2E (PR-gated)

`apps/.../e2e` (scm-platform module, `@Tag` per ADR-MONO-010): bring up demand-planning + procurement + Postgres + Kafka; seed `reorder_policy` + `sku_supplier_map`; **publish a wms-shaped `inventory.low-stock-detected`** to `wms.inventory.alert.v1` (simulating wms — no wms container needed); assert: suggestion `SUGGESTED` raised → operator `approve` → procurement DRAFT PO created with `origin=DEMAND_PLANNING` + `sourceSuggestionId`. Plus: dedup idempotency, unmapped-SKU→DLT, open-guard re-suggest after MATERIALIZED, tenant fail-closed.

### 2. Federation-stack live proof (the real cross-project leg)

`tests/federation-hardening-e2e` (root): add demand-planning to the federation compose stack; a scenario where a **real wms inventory mutation drops stock below threshold → wms emits the real alert → scm demand-planning raises a suggestion → operator approves → procurement DRAFT PO appears** (Playwright + DB assertion, mirroring the ADR-022 Option-B live proof shape). Reuse the dedicated federation tenant/seed convention (memory: federation-e2e fixed-band seed traps).

### 3. CI wiring

- scm Integration/E2E job includes the deterministic leg (PR-gated, the authoritative guard).
- federation leg runs in the nightly `federation-hardening-e2e.yml`; document the post-merge `gh run list --workflow=federation-hardening-e2e.yml` check (rename/Flyway nightly-only regression precedent).

## Out of Scope

- Demand forecasting / supplier-service scenarios — v2.
- Multi-warehouse routing — v2.
- The customer-order fulfillment loop (ADR-022) — separate.

# Acceptance Criteria

- **AC-1** Deterministic Testcontainers E2E green: simulated alert → suggestion → approve → procurement DRAFT PO, all asserted in one run; PR-gated in scm CI.
- **AC-2** Idempotency proven: duplicate alert eventId → one suggestion; re-approve → one PO.
- **AC-3** Unmapped-SKU alert → DLT + ops signal, no suggestion (fail-closed).
- **AC-4** Open-guard: after a suggestion MATERIALIZED, a second below-threshold alert for the same SKU+warehouse raises a fresh suggestion.
- **AC-5** Federation live leg green in the nightly stack: real wms alert produces a scm DRAFT PO; Playwright + DB confirm.
- **AC-6** Post-merge federation nightly verified green (no silent cross-project regression).

# Related Specs

- [ADR-MONO-027](../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) (§5 consequences: nightly-only guard risk)
- [TASK-SCM-INT-001](../done/TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md) (scm cross-service E2E env/seed patterns)
- [ADR-MONO-010](../../../../docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md) / [ADR-MONO-011](../../../../docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md) (tag taxonomy + nightly cadence)

# Related Contracts

- [`replenishment-subscriptions.md`](../../specs/contracts/events/replenishment-subscriptions.md), `demand-planning-api.md`, `procurement-api.md`, wms [`inventory-events.md`](../../../wms-platform/specs/contracts/events/inventory-events.md) §7

# Edge Cases

- **federation seed band collision**: reuse a dedicated tenant + fixed Flyway V-band (memory: globex/initech/umbrella precedent) to avoid production-seed renumber collisions.
- **outbox poller warmup flake**: write-heavy E2E before Kafka warmup can 500 (memory MONO-207/210 pattern) — add a warmup gate + transient-500 retry if observed.
- **Docker on Windows local**: npipe + `DOCKER_API_VERSION=1.44`, alpine `preferIPv4Stack`, redis timeout (memory `project_testcontainers_docker_desktop_blocker`).

# Failure Scenarios

- **nightly federation RED only** (PR green): the deterministic leg is the contract guard; on nightly RED, a separate fix-task restores green (CI-RED-at-merge → main regression rule). Document the `gh run list` check in the close chore.
- **simulated alert shape drifts from real wms envelope**: the deterministic leg must use the exact wms camelCase envelope (consumer-driven contract test) so it stays a faithful proxy for the live leg.

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (cross-project E2E + federation wiring + flake-class handling).
- The deterministic leg is the PR-gated authority; the federation leg is the live demonstration. Both required for "federation E2E 실증까지" scope (user decision).
