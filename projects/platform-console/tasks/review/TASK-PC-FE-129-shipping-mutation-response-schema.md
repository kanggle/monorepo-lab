# TASK-PC-FE-129 — shipping mutation response schema (3-field projection)

- **Status**: review
- **Project**: platform-console
- **Area**: console-web / features/ecommerce-ops (shippings operator surface)
- **Type**: bug fix (consumer contract drift)
- **Related task**: TASK-PC-FE-088 (shippings console absorption), TASK-PC-FE-121 (shipping read-shape nullable)

## Goal

Fix the false "상태를 변경하지 못했습니다." failure when an operator changes a
shipping status (e.g. `demo-ship-0002` SHIPPED → IN_TRANSIT) in the console
E-Commerce 배송 screen. The backend transition **succeeds and commits**, but the
console mis-parses the producer's mutation response and reports a fake failure.

## Root cause

The producer `shipping-service` returns a **3-field projection** for the two
shipping mutations:

```
PUT  /api/shippings/{id}/status            → UpdateShippingStatusResponse { shippingId, status, updatedAt }
POST /api/shippings/{id}/refresh-tracking  → UpdateShippingStatusResponse { shippingId, status, updatedAt }
```

The console consumer (`shippings-api.ts` server client AND
`use-ecommerce-shippings.ts` client hook) parses both responses with the full
`ShippingSchema`, which **requires `orderId` and `createdAt`**. Those fields are
absent in the projection → `ShippingSchema.parse()` throws a ZodError →
`callEcommerce` swallows it into `EcommerceUnavailableError('NETWORK_ERROR')` →
the route maps it to a 503/network error → the UI shows the generic fallback
message — even though the backend already committed the transition.

Observed in the local federation-hardening-e2e demo:
- shipping-service log: `Shipping status updated: shippingId=demo-ship-0002, SHIPPED -> IN_TRANSIT` (200, committed)
- console-web log: `ecommerce_shipping_ok status:200 /shippings/demo-ship-0002/status` immediately followed by `ecommerce_shipping_error /shippings/demo-ship-0002/status` (post-200 parse failure)

The existing `ecommerce-shippings-api.test.ts` green-washed this: it mocked the
mutation responses with a **full Shipping** fixture (`orderId`, `createdAt`, …),
which does not match the real wire shape, so the broken parse passed in tests.

## Scope

- Add `UpdateShippingStatusResponseSchema` (`{ shippingId, status, updatedAt? }`,
  `.passthrough()`, tolerant) to `features/ecommerce-ops/api/shipping-types.ts`,
  matching the producer's actual `UpdateShippingStatusResponse`.
- Parse both mutations (`updateShippingStatus`, `refreshTracking`) with the new
  schema in `shippings-api.ts` and `use-ecommerce-shippings.ts`.
- Update `ecommerce-shippings-api.test.ts`: mutation-response mocks use the real
  3-field projection; add a regression test asserting a projection-shaped
  response (NO `orderId`/`createdAt`) parses successfully (would have failed before).

Out of scope: the producer contract (it is the authority; the contract §2.4.10.3
does not pin the mutation response shape — this is consumer-side tolerance, the
same TOLERANCE invariant already documented in `shipping-types.ts`).

## Acceptance Criteria

- AC-1: `updateShippingStatus` / `refreshTracking` resolve successfully for a
  producer response of `{ shippingId, status, updatedAt }` (no `orderId`/`createdAt`).
- AC-2: SHIPPED → IN_TRANSIT in the console 배송 screen no longer shows a false
  error when the backend returns 200.
- AC-3: existing resilience behaviour (401/403/404/400/409/503/timeout/tolerant
  unknown status) is preserved.
- AC-4: `pnpm lint` + `tsc` + `vitest` all GREEN.

## Related Specs / Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.10.3
- producer: `ecommerce-microservices-platform` `shipping-service`
  `ShippingController` / `UpdateShippingStatusResponse`

## Edge Cases

- Unknown/future `status` enum still parses (tolerant string).
- `updatedAt` optional (producer always sends it, but tolerate absence).

## Failure Scenarios

- Backend 4xx/5xx error envelopes still map to the §2.5 resilience taxonomy
  (unchanged — the error path never reaches the success-parse).
