# Task ID

TASK-SCM-INT-002

# Title

Replenishment-loop E2E — Testcontainers cross-service (simulated wms alert → demand-planning suggestion → procurement DRAFT PO) **plus** federation-stack live proof (real wms alert → scm DRAFT PO). Closes ADR-MONO-027 Phase 2. impl/test.

# Status

done

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

# Closure

- **leg 1 (deterministic Testcontainers, PR-gated)** — merged PR #1315 squash `ca12217f1` (prior session). scm `tests/e2e` ReplenishmentLoopE2ETest: simulated wms alert → demand-planning suggestion → approve → procurement DRAFT PO + dedup/unmapped/open-guard edges. **AC-1..AC-4 satisfied.**
- **leg 2 (federation live proof, nightly)** — merged PR #1324 squash `e78f21d2b` (3-dim verified: state=MERGED · origin/main tip = `e78f21d2b` · pre-merge `gh pr checks` 0 failing required). Dedicated event overlay `docker-compose.federation-e2e.replenishment.yml` (redpanda dual-listener + scm-dp-postgres + scm-demand-planning-service; procurement repointed at redpanda; base byte-unchanged per TASK-MONO-170 invariant). `specs/scm-replenishment-loop.spec.ts`: a domain-facing IAM operator (SUPER_ADMIN base access token) seeds policy+mapping → the canonical wms `inventory.low-stock-detected` envelope is published to the real broker → the REAL demand-planning consumer raises a suggestion → approve → procurement DRAFT PO, Playwright + REST asserted. **AC-5 satisfied** (branch `workflow_dispatch` run 27347926040 GREEN, 18 passed; post-merge main run AC-6).
- **Decision (user-directed):** zero-wms-change (ADR-027 §D1/§5) — wms-inventory-service is not booted; the only wms low-stock trigger is a role-gated REST mutation (`ROLE_INVENTORY_WRITE`) the production IAM token model never mints, so a committed nightly spec injects the canonical envelope (fidelity guarded by leg-1's KafkaTestProducer) rather than relaxing wms authz. This is the FIRST committed nightly spec to drive a real Kafka event path in the otherwise event-free federation stack.
- **AC-6** post-merge federation nightly verified green on main (`gh run list --workflow=federation-hardening-e2e.yml`).
