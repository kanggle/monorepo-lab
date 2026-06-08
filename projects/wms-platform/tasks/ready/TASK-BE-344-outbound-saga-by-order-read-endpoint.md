# Task ID

TASK-BE-344

# Title

outbound-service: implement the documented-but-missing `GET /api/v1/outbound/orders/{id}/saga` (§5.1) — surfaced by the TASK-PC-FE-057 live console integration

# Status

ready

# Owner

claude (Opus 4.8 analysis / Sonnet 4.6 impl) — wms outbound-service additive read endpoint closing a contract↔implementation gap.

# Task Tags

- api
- code
- test

---

# Dependency Markers

- **surfaced by**: the TASK-PC-FE-057 live console integration (ADR-MONO-022 §D7 on-screen pick/pack/ship). The console order-drill calls `GET /orders/{id}/saga` (per the contract §5.1) and got **500 `NoResourceFoundException`** — the endpoint is **documented in `outbound-service-api.md` §5.1 but was never implemented** (Spring fell through to static-resource handling). The Docker-free unit suite mocked the §5.1 response, so it never caught the gap (the §14 "unit mocks the contract, integration reveals reality" pattern).
- **consumer**: TASK-PC-FE-057 console `features/wms-outbound-ops` order-drill (needs the saga `state` to gate the Pick action).
- **spec**: `outbound-service-api.md` §5.1 already documents this endpoint — this task IMPLEMENTS the existing contract (no contract change).

# Goal

Implement `GET /api/v1/outbound/orders/{id}/saga` exactly as documented in `outbound-service-api.md` §5.1: `OUTBOUND_READ`; returns the order's `OutboundSaga` (`sagaId`, `orderId`, `state`, `failureReason`, `startedAt`, `lastTransitionAt`, `version`); `404 ORDER_NOT_FOUND` when the order id does not exist.

# Scope

## In Scope

- **`application/result/SagaResult.java`** (new) — read record (sagaId, orderId, state, failureReason, startedAt, lastTransitionAt, version).
- **`application/port/in/QuerySagaUseCase.java`** (new) — `Optional<SagaResult> findByOrderId(UUID)`.
- **`application/service/SagaQueryService.java`** (new) — `@Service` implementing the in-port via the existing `SagaPersistencePort.findByOrderId`, mapping the `OutboundSaga` domain (`status().name()` → `state`).
- **`adapter/in/web/dto/response/SagaResponse.java`** (new) — wire DTO + `from(SagaResult)`.
- **`adapter/in/web/controller/OrderQueryController.java`** (edit) — inject `QuerySagaUseCase`; add `@GetMapping("/{id}/saga")` that asserts order existence via `queryOrder.findById(id)` (→ `ORDER_NOT_FOUND`/404 with the canonical envelope) then returns the saga. In-port only (no persistence out-port in the adapter).
- Tests: unit (controller slice + service) + IT (seed order+saga, GET → 200 with `state`). The `OrderQueryPickingRequestsControllerTest` gains the new `QuerySagaUseCase` mock (the controller constructor changed).

## Out of Scope

- Any saga **mutation** or force-saga operation (read-only).
- The standalone `GET /picking-requests/{id}` (§2.2) — separate.
- Any console/frontend change (TASK-PC-FE-058 handles the console-side envelope fix).

# Acceptance Criteria

- [ ] `GET /api/v1/outbound/orders/{id}/saga` returns `200` with the §5.1 shape (`state` = `SagaStatus` name), `OUTBOUND_READ`-gated; unknown order → `404 ORDER_NOT_FOUND` (canonical envelope). Matches `outbound-service-api.md` §5.1.
- [ ] Hexagonal: controller consumes `QueryOrderUseCase` + `QuerySagaUseCase` in-ports only; the saga read maps in the query service (no persistence out-port in the adapter).
- [ ] Unit + IT green locally and in CI (`outbound-service:check` + the wms integration job). No regression in existing outbound suites (the picking-requests controller test updated for the new mock).
- [ ] Scope = `projects/wms-platform/apps/outbound-service` only. No contract change (§5.1 already documented).

# Related Specs

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` §5.1 (the endpoint — implemented here) / §1.2 / §2.4
- `projects/wms-platform/specs/services/outbound-service/architecture.md` (hexagonal — controller→in-port only)
- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md` (saga states)

# Related Contracts

- **Consumed/implemented (no change)**: `outbound-service-api.md` §5.1.

# Target Service

- `wms-platform` / `apps/outbound-service` — read-only, additive; `OrderQueryController` new GET + saga query in-port/service + response DTO.

# Architecture

- Hexagonal: the saga read uses the existing `SagaPersistencePort.findByOrderId` via a new in-port (`QuerySagaUseCase`) + `SagaQueryService`. The controller never imports the out-port.
- Additive read — no lifecycle/state-machine/event change.

# Edge Cases

- Order exists, saga present (always — saga is created atomically with the order) → 200.
- Unknown order id → 404 ORDER_NOT_FOUND.
- Saga in any state (RESERVED, SHIPPED, …) → `state` = the enum name (tolerant string on the wire).

# Failure Scenarios

- Endpoint left unmapped (the bug this fixes) → `NoResourceFoundException`/500 → the console drill degrades → AC asserts a 200 with `state`.
- Adapter imports the persistence out-port (hexagonal violation) → re-verify the controller consumes in-ports only.

# Definition of Done

- [ ] §5.1 endpoint implemented + unit + IT; `outbound-service:check` + CI integration job green
- [ ] 200/404 correct; `OUTBOUND_READ`-gated; hexagonal intact; no regression
- [ ] Acceptance Criteria satisfied
- [ ] Ready for review
