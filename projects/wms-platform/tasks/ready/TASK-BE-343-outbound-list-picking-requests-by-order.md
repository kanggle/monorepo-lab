# Task ID

TASK-BE-343

# Title

outbound-service: expose `GET /api/v1/outbound/orders/{id}/picking-requests` — list picking requests (with planned lines) for an order, closing the picking-request discovery gap

# Status

ready

# Owner

claude (Opus 4.8 analysis / Sonnet 4.6 impl recommended) — wms outbound-service additive read endpoint. NOT a lifecycle/behavior change.

# Task Tags

- api
- code
- test

---

# Dependency Markers

- **맥락 (ADR-MONO-022 §D7 forward arc)**: an ecommerce purchase auto-creates an `outbound_order` (status `PICKING`, `source=FULFILLMENT_ECOMMERCE`). An operations console (TASK-PC-FE-057, the consumer of this endpoint) drives that order pick → pack → ship. §2.3 pick confirmation requires the **`pickingRequestId`** plus each line's planned **`locationId`** / **`qtyToPick`** — but no read endpoint lets a caller holding only the `orderId` discover them. This task adds that read.
- **선행 for**: TASK-PC-FE-057 (console outbound-ops screen) — its "confirm pick as planned" action calls this endpoint to pre-fill the §2.3 confirmation body.
- **spec-first**: `outbound-service-api.md` **§2.4** (this endpoint) lands before/with the code in the same PR (HARDSTOP-06 / Contract Rule). Already drafted in the contract.

# Goal

Expose the already-existing `QueryPickingRequestUseCase.findByOrderId(orderId)` read path as a REST endpoint — `GET /api/v1/outbound/orders/{id}/picking-requests` — returning the order's picking request(s) **including the planned lines** (`pickingRequestLineId`, `orderLineId`, `skuId`, `lotId`, `locationId`, `qtyToPick`), per `outbound-service-api.md` §2.4. This closes the discovery gap that otherwise makes §2.3 pick confirmation unreachable for a caller that holds only the `orderId`.

# Scope

## In Scope

### Spec-first (already drafted, ship in this PR)

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` **§2.4** — `GET /api/v1/outbound/orders/{id}/picking-requests`. `OUTBOUND_READ`; returns `{ "content": [ <picking-request incl. lines> ] }` (not paginated, bounded by the order); empty `content` (200) when the order exists but the saga has not yet created its picking request; `404 ORDER_NOT_FOUND` only when the order id does not exist.

### Code (`apps/outbound-service`)

- **`PickingRequestResult`** — add a `lines` field: `List<PickingRequestLineResult>` where `PickingRequestLineResult` carries `pickingRequestLineId`, `orderLineId`, `skuId`, `lotId` (nullable), `locationId`, `qtyToPick`. Populate it in `PickingQueryService.toResult(...)` from the domain `PickingRequest.getLines()` (the domain aggregate already exposes the lines). Existing internal callers (`ConfirmPickingService`) ignore `lines` — additive, no behavior change.
- **Response DTO** — new `PickingRequestResponse` (+ `PickingRequestLineResponse`) under `adapter/in/web/dto/response/`, mirroring the §2.1/§2.4 shape (`pickingRequestId`, `orderId`, `sagaId`, `warehouseId`, `status`, `lines[]`, `version`, `createdAt`, `updatedAt`), with a `from(PickingRequestResult)` factory. A small wrapper for the `content` array (reuse the existing list-envelope DTO if one fits a non-paginated list, else a minimal `{ content: [...] }` record).
- **`OrderQueryController`** — inject `QueryPickingRequestUseCase`; add `@GetMapping("/{id}/picking-requests")`. Verify the order exists first via `QueryOrderUseCase.findById(id)` (propagates `ORDER_NOT_FOUND` → 404), then return `findByOrderId(id)` mapped to the response, wrapped in the `content` array (empty list when the picking request does not exist yet). `OUTBOUND_READ` is enforced by the existing method-level security gate (GET ⇒ `OUTBOUND_READ|WRITE|ADMIN`).
- No new persistence method — `PickingPersistencePort.findByOrderId` already exists and is used by `PickingQueryService`.

### Tests

- **Unit**: `OrderQueryController` (or service) test — order with a picking request → 200 with the lines populated (location/qtyToPick present); order with no picking request yet → 200 `{content:[]}`; unknown order id → `ORDER_NOT_FOUND`/404. `PickingQueryService.findByOrderId` maps `lines` correctly from the domain aggregate.
- **IT** (the BE-342 CI-runnable suite): add/extend a case asserting the endpoint returns the picking request with planned lines for a seeded order (reuse the existing outbound IT fixtures). Must pass in the CI integration job.

## Out of Scope

- Any change to the picking **write** path (§2.1 create, §2.3 confirm) — unchanged.
- Pagination on the new list (bounded by the order; v1 ≤ 1 picking request — a `content` array without a `page` envelope is intentional).
- A standalone `GET /picking-requests/{id}` endpoint (§2.2) — separate; not required by the consumer (it discovers by `orderId`).
- Any console/frontend change (TASK-PC-FE-057).

# Acceptance Criteria

- [ ] `GET /api/v1/outbound/orders/{id}/picking-requests` returns `200 { "content": [ <picking-request incl. planned lines> ] }`, `OUTBOUND_READ`-gated, per `outbound-service-api.md` §2.4. Each line carries `locationId` + `qtyToPick` (the fields §2.3 confirmation needs).
- [ ] Order exists but no picking request yet → `200 { "content": [] }` (not 404). Unknown order id → `404 ORDER_NOT_FOUND`.
- [ ] `PickingRequestResult` carries `lines`; `PickingQueryService.toResult` populates them from `PickingRequest.getLines()`; `ConfirmPickingService` (internal caller) unaffected (additive).
- [ ] Spec-first: §2.4 merged before/with code in the same PR. Contract is authoritative; code matches.
- [ ] Unit + IT green locally and in CI (the outbound-service integration job, BE-342); `./gradlew :projects:wms-platform:apps:outbound-service:check` passes. No regression in the existing outbound suites.
- [ ] Scope = `projects/wms-platform/apps/outbound-service` + its contract only. Hexagonal layering preserved (controller consumes the in-port only; no persistence out-port in the adapter layer).

# Related Specs

> Target project = `wms-platform`. Target service = `outbound-service`. Follow `platform/entrypoint.md`; service-type per `outbound-service/architecture.md`.

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` §2.1/§2.2/§2.3 (picking) — this task adds §2.4
- `projects/wms-platform/specs/services/outbound-service/architecture.md` (hexagonal — controller→in-port only, AC-04 of TASK-BE-040)
- `projects/wms-platform/specs/services/outbound-service/domain-model.md` (`PickingRequest` aggregate + lines)
- `projects/wms-platform/PROJECT.md` (domain=wms; traits)

# Related Contracts

- **Changed (this task, spec-first)**: `outbound-service-api.md` **§2.4** (new read endpoint).
- **Unchanged**: all write endpoints; events; gateway routing (the new path is under the existing `/api/v1/outbound/**` route — no gateway change).

# Target Service

- `wms-platform` / `apps/outbound-service` — `OrderQueryController` new GET; `PickingRequestResult` + `PickingQueryService` lines; new response DTO. Read-only, additive.

# Architecture

- Hexagonal (AC-04 of TASK-BE-040): the controller consumes `QueryOrderUseCase` + `QueryPickingRequestUseCase` in-ports only; never the persistence out-port. The `lines` enrichment lives in the read result mapped by the query service, not in the adapter.
- Additive read — no lifecycle/state-machine/event change; no optimistic-lock or idempotency surface (GET).

# Implementation Notes

- The application read path already exists (`QueryPickingRequestUseCase.findByOrderId` + `PickingPersistencePort.findByOrderId` + `PickingRepositoryImpl`). The only real work is (a) carrying `lines` through `PickingRequestResult` and (b) the new controller endpoint + response DTO. Keep it minimal.
- `OUTBOUND_READ` is enforced by `SecurityConfig`'s HTTP-method gate (GET ⇒ read role); no per-method `@PreAuthorize` needed (matches existing GETs).
- Recommend implementation model: **Sonnet** (routine additive read endpoint mirroring existing GETs; no domain/state logic). Dispatch `Agent(subagent_type="backend-engineer", model="sonnet", ...)`. Dispatcher re-verifies: grep the new controller for the in-port (no persistence out-port import in the adapter); confirm `lines` populated with `locationId`+`qtyToPick`.
- Branch name must not contain `master`. Use e.g. `task/be-343-outbound-list-picking-requests`.

# Edge Cases

- Order exists, saga still `REQUESTED`, picking-request row not yet written → `200 {content:[]}` (honest empty, not an error).
- LOT-tracked SKU line → `lotId` present; non-LOT → `lotId` null (line still returned).
- Unknown order id → `404 ORDER_NOT_FOUND` (order existence checked before the picking-request lookup).
- v2 forward-compat: multiple picking requests per order → all returned in `content` (v1 returns ≤ 1).

# Failure Scenarios

- Endpoint returns the picking request **without** lines (the current `PickingRequestResult` shape) → the console cannot build the §2.3 confirmation body → AC + test assert `locationId`+`qtyToPick` present per line.
- Empty result rendered as `404` instead of `200 {content:[]}` → breaks the consumer's "order has no picking request yet" state → AC asserts the 200-empty vs 404-unknown distinction.
- Adapter imports the persistence out-port directly (AC-04 violation) → re-verify the controller consumes in-ports only.
- Spec not shipped with code (no §2.4) → HARDSTOP-06; §2.4 already drafted, must merge in the same PR.

# Definition of Done

- [ ] §2.4 contract + code + unit + IT in one PR; `outbound-service:check` + the CI integration job green
- [ ] Endpoint returns picking request(s) incl. planned lines; 200-empty vs 404-unknown correct; `OUTBOUND_READ`-gated
- [ ] Hexagonal layering intact; no write-path/behavior change; no regression
- [ ] Acceptance Criteria satisfied; TASK-PC-FE-057 unblocked
- [ ] Ready for review
