# TASK-BE-039 — Fix issue found in TASK-BE-037

## Goal

Fix issue found in TASK-BE-037 (outbound-service Order domain + receive/cancel/query). The TASK-BE-037 review identified missing unit tests required by AC-16 and a contract-shape divergence on the cancel endpoint, plus N+1 query patterns in the list endpoint and dead-code path matchers in the security filter chain.

## Scope

**Target service**: `outbound-service` (`projects/wms-platform/apps/outbound-service/`)

**In scope:**

1. **Add the unit tests AC-16 explicitly requires:**
   - `OrderQueryServiceTest` — covers `findById` happy path, `findById` ORDER_NOT_FOUND, and `list` with at least one filtered query, asserting the returned `sagaState` enrichment is correct.
   - `InventoryReleasedConsumerTest` — fresh-event branch advances saga `CANCELLATION_REQUESTED → CANCELLED`; duplicate-event branch via `EventDedupePort` skips the coordinator; missing-saga payload is tolerated. Pattern after `InventoryReservedConsumerTest`.
   - `InventoryConfirmedConsumerTest` — fresh-event branch advances saga `SHIPPED → COMPLETED`; duplicate-event branch via `EventDedupePort` skips the coordinator. Pattern after `InventoryReservedConsumerTest`.

2. **Fix cancel response contract (`outbound-service-api.md` §1.4):**
   - Introduce a `CancelOrderResponse` (or extend `OrderResponse`) carrying `previousStatus`, `cancelledReason`, `cancelledAt`, `cancelledBy`, plus the fields the create-response also returns.
   - Either thread the cancel-time metadata through `OrderResult` (preferred — add nullable `previousStatus`, `cancelledReason`, `cancelledAt`, `cancelledBy` fields populated only by `CancelOrderService`) or return a dedicated cancel result type.
   - Update `OrderController.cancelOrder` to map to the new shape.
   - Add a controller-or-service test asserting the response JSON contains `previousStatus`, `cancelledReason`, `cancelledAt`, `cancelledBy`.

3. **Eliminate N+1 in the list path (`OrderQueryService.list` + `OrderPersistenceAdapter.findSummaries`):**
   - Project `(orderId, orderNo, source, customerPartnerId, warehouseId, status, lineCount, totalQty, requiredShipDate, createdAt, updatedAt, sagaState)` in a single JPQL or native query in `OrderRepository`. Use `LEFT JOIN OutboundSagaEntity` + a `(SELECT count(*), sum(requestedQty) ...)` derived projection (or two grouped joins) so the list endpoint runs O(1) queries irrespective of page size.
   - Drop the per-row `lineRepo.findByOrderIdOrderByLineNumberAsc` call in `OrderPersistenceAdapter.toSummary` and the per-row `sagaPersistence.findByOrderId` call in `OrderQueryService.enrichWithSagaState`.
   - Update `OrderQueryServiceTest` to fail if the saga lookup is invoked per-row (e.g., a `FakeSagaPersistencePort` that increments a counter on `findByOrderId`).

4. **`expectedVersion` enforcement on cancel:**
   - In `CancelOrderService.cancel`, after loading the order, compare `order.getVersion() == command.expectedVersion()` and raise `OptimisticLockingFailureException` (mapped to 409 `CONFLICT` already) when they diverge. This delivers the early-fail behaviour the API contract describes ("optimistic lock check") rather than relying on JPA's deferred check.
   - Add a `CancelOrderServiceTest` case proving 409 surfaces when the command's version is stale.

5. **SecurityConfig path-matcher cleanup:**
   - Either correct the matchers to reflect the actual URLs (`/api/v1/outbound/orders/*:cancel` and `/api/v1/outbound/shipments/*:retry-tms-notify`) using path patterns that handle the `:cancel` suffix, or delete the matchers entirely and document that role enforcement happens in the application service. The current dead-code matchers mislead reviewers.

6. **`OrderMapper` visibility:**
   - Make `OrderMapper` package-private (drop `public`), aligning with the architecture rule "Mappers package-private inside persistence adapter" (`architecture.md` §Layer Rules, item 5).

## Acceptance Criteria

**AC-01** `OrderQueryServiceTest`, `InventoryReleasedConsumerTest`, `InventoryConfirmedConsumerTest` exist and exercise both fresh and duplicate paths (where applicable) using port fakes only (no Mockito/Testcontainers).

**AC-02** `POST /api/v1/outbound/orders/{id}:cancel` response body matches `outbound-service-api.md` §1.4: includes `previousStatus`, `cancelledReason`, `cancelledAt`, `cancelledBy`, `sagaState`, `version`, `orderId`, `orderNo`, `status`. A controller or service test asserts each field is populated on success.

**AC-03** `GET /api/v1/outbound/orders` list endpoint executes a constant number of DB queries regardless of page size. A unit test on `OrderQueryService` (or persistence-adapter Testcontainers test) demonstrates the saga and line lookups run at most once total, not per row.

**AC-04** `CancelOrderService` rejects stale `expectedVersion` with `OptimisticLockingFailureException` (→ 409 `CONFLICT`). A new test case in `CancelOrderServiceTest` covers this.

**AC-05** SecurityConfig matchers either match the real URLs or are removed; no dead-code path matchers remain.

**AC-06** `OrderMapper` is package-private; no client outside `adapter.out.persistence` references it.

**AC-07** `./gradlew :projects:wms-platform:apps:outbound-service:test` passes.

## Related Specs

- `projects/wms-platform/specs/services/outbound-service/architecture.md` §Layer Rules
- `projects/wms-platform/specs/services/outbound-service/domain-model.md` §1
- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md`
- `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md`
- `projects/wms-platform/tasks/review/TASK-BE-037-outbound-order-domain-receive-cancel-query.md`

## Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` §1.1, §1.2, §1.3, §1.4
- `projects/wms-platform/specs/contracts/events/outbound-events.md` §1, §2, §3, §4

## Edge Cases

- **List with empty result**: query must still run only the projection query plus its count — no per-row enrichment loop attempted.
- **Cancel with version equal to current**: succeeds. Version mismatch surfaces 409 even before the JPA flush.
- **InventoryReleasedConsumer with duplicate eventId**: `EventDedupePort` returns `IGNORED_DUPLICATE`; coordinator must NOT be invoked. Test must assert this with a counter or saga-state check.
- **InventoryConfirmedConsumer with saga not in SHIPPED**: coordinator's existing logic raises `StateTransitionInvalidException`; the consumer test should cover at least the happy path; impossible-state behaviour can stay as integration-test territory.
- **OrderQueryService.list when no saga exists for a row** (e.g., legacy bootstrap row): `sagaState` returns null without an extra query.

## Failure Scenarios

- **Cancel with stale version**: `OptimisticLockingFailureException` → 409 `CONFLICT`. No outbox row written, no saga mutation, full rollback.
- **List query syntax error**: surfaces as 500 INTERNAL_ERROR (no fallback to per-row loop).
- **Test environment missing fakes**: build fails — no production behaviour change.
