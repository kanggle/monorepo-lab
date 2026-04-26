# TASK-MONO-005 — sync order-api contract with OrderSummary `firstItemName` field

## Goal

Resolve the contract drift in `OrderApiContractTest.GET /api/orders` that
blocks order-service from joining the root CI Build & Test enumeration
(see TASK-MONO-004 Outcome and TASK-MONO-008 § Out of scope). After the
fix, add `:projects:ecommerce-microservices-platform:apps:order-service:check`
to `.github/workflows/ci.yml`.

## Background

`OrderApiContractTest.getOrders_response_containsSpecFields` (line ~111)
asserts `assertFieldsMatch(content[0], Set.of("orderId", "status",
"totalPrice", "itemCount", "createdAt"), …)` per
`specs/contracts/http/order-api.md` GET /api/orders 200 § content[].

The actual response payload includes a sixth field, `firstItemName`,
which is intentionally produced by `OrderSummary.from(Order)` and
consumed by `OrderListResponse.OrderSummaryItem`. The same field is
emitted by `AdminOrderSummary` for `GET /api/admin/orders`. Both
record types declare the field as a positional component, and the
controller code path is the only producer.

The drift is therefore in the spec (and its mirror in the test
assertion), not in the code: `firstItemName` is a deliberate UI-list
optimization (preview of the first line item) that pre-dates the test's
`assertFieldsMatch` strict-set check.

`order-api.md` GET /api/admin/orders content[] omits `firstItemName`
for the same reason — the spec lags the implementation.

## Scope

**In scope:**

1. `specs/contracts/http/order-api.md`
   - GET /api/orders 200 § content[] — add `firstItemName: "string | null"`.
   - GET /api/admin/orders 200 § content[] — add `firstItemName: "string | null"` for parity (admin response uses `AdminOrderSummary` which already emits it).
2. `OrderApiContractTest.getOrders_response_containsSpecFields` — extend
   the asserted field set to include `"firstItemName"`.
3. `.github/workflows/ci.yml` — append
   `:projects:ecommerce-microservices-platform:apps:order-service:check`
   to the Build & Test gradle invocation list. Update the comment block
   to remove order-service from the deferred list.

**Out of scope:**

- Changes to `OrderSummary` / `AdminOrderSummary` records, controllers,
  service layer, or any test other than `OrderApiContractTest`.
- product-service / search-service drift (TASK-MONO-006/007).
- Any client (web-store, admin-dashboard) types — the field is additive
  and nullable, so existing clients keep working unchanged.

## Acceptance Criteria

1. `./gradlew :projects:ecommerce-microservices-platform:apps:order-service:check`
   passes locally and on CI.
2. `specs/contracts/http/order-api.md` reflects the `firstItemName`
   field for both customer and admin list endpoints.
3. `OrderApiContractTest` asserts the 6-field set for
   GET /api/orders content[].
4. CI Build & Test gradle list includes order-service `:check`.
5. The deferred-services comment block in `ci.yml` no longer mentions
   order-service / TASK-MONO-005.
6. No other ecommerce service test report regresses.

## Related Specs

- `specs/contracts/http/order-api.md` — GET /api/orders, GET /api/admin/orders.
- `tasks/done/TASK-MONO-004-...` — plumbing PR; established the
  `@Tag("integration")` filter that this task piggybacks on.
- `tasks/done/TASK-MONO-008-...` — root CI extension PR; this task
  closes one of its three deferred slots.

## Related Contracts

- `OrderSummary` (record) → produced by `OrderQueryService.getOrders` →
  consumed by `OrderListResponse.OrderSummaryItem` → JSON serialized at
  GET /api/orders.
- `AdminOrderSummary` (record) → equivalent path for GET /api/admin/orders.

## Edge Cases

- `firstItemName` is null when the order has zero items
  (`OrderSummary.from` short-circuits on `getItems().isEmpty()`). The
  spec therefore documents the type as `string | null`. Test fixtures
  use a non-null value, so the assertion remains
  `Set.of("orderId", ..., "firstItemName", ...)` — Jackson emits the
  property even when null, so the field-set assertion is unaffected.
- The customer and admin endpoints share the field name and semantics
  by design; updating both keeps `OrderSummary` ↔ `AdminOrderSummary`
  symmetric in the contract.

## Failure Scenarios

- **Other tests regress on order-service `:check`**: Most likely
  candidate would be `AdminOrderControllerTest`, which uses
  `AdminOrderSummary` but in slice-style not strict-field assertions.
  If a regression appears, scope it as a new follow-up; do not bundle
  it here.
- **CI build-and-test runtime grows materially**: order-service has
  ~30+ test classes. Combined runtime is forecast under the existing
  ~3-4 min build envelope for the 9-service subset; if it exceeds
  10 min, split the gradle invocation into two steps in a follow-up.

## Outcome (2026-04-26)

- `specs/contracts/http/order-api.md` updated:
  - GET /api/orders content[] gained `firstItemName: "string | null"`.
  - GET /api/admin/orders content[] gained `firstItemName: "string | null"` for parity.
- `OrderApiContractTest.getOrders_response_containsSpecFields`
  field-set extended to include `"firstItemName"`.
- `.github/workflows/ci.yml` Build & Test gradle list extended with
  `:projects:ecommerce-microservices-platform:apps:order-service:check`;
  the deferred-services comment block now lists product-service and
  search-service only.
- Local verify: `./gradlew :projects:ecommerce-microservices-platform:apps:order-service:check`
  → BUILD SUCCESSFUL, 260 tests, 0 failures.

Acceptance criteria 1, 2, 3, 4, 5 met by the diff. AC #6 verified by
the same local run (other ecommerce subprojects untouched). CI run on
the PR will exercise the cold path.
