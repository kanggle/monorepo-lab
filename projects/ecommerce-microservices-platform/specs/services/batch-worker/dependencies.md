# Service Dependencies

## Service
`batch-worker`

## Allowed Direct Dependencies
- shared technical libraries allowed by platform policy
- own database (job execution history only)
- messaging infrastructure (Kafka — event consumption and publication)

## Allowed Service Interactions
- through published HTTP contracts (read-only)
- through published event contracts
- **internal-endpoint exceptions — exactly these, no others:**
  1. order-service `POST /api/internal/orders/confirm-paid-stale` (`client_credentials` Bearer, `ecommerce-internal-services-client`) — system-command (TASK-BE-410 decision). order-service evaluates the predicate + performs the transition server-side; batch-worker only triggers it. NOT a database write into order's store.
  2. order-service `POST /api/internal/orders/existence` (same credential) — **read-only** (TASK-INT-028).
  3. promotion-service `POST /api/internal/coupons/stale-used` — **read-only**, internal network (TASK-INT-028).
  4. promotion-service `POST /api/internal/coupons/{couponId}/release` — system-command, internal network, sent with the coupon's own `X-Tenant-Id` (TASK-INT-028). Only for a coupon whose order order-service has just answered as **not existing**.

  Items 2–4 are authorized by the owner decision recorded in `TASK-INT-028` AC-1 (「배치 대조」), which widened this boundary from one internal call. Non-read-only calls remain 1 and 4 only.

## Consumes From

| Source | Contract | Purpose | Auth |
|---|---|---|---|
| product-service | `product-api.md` | Source data for Elasticsearch index consistency check | none (public read) |
| search-service | `search-api.md` | Index data for Elasticsearch index consistency check | none (public read) |
| order-service | `specs/contracts/http/internal/order-confirm-paid-stale.md` (`POST /api/internal/orders/confirm-paid-stale`) | Stale paid-order forward-confirm (`PENDING AND payment_id IS NOT NULL` → `CONFIRMED`); recovery for a lost confirm event. Disjoint from BE-138 (`payment_id IS NULL`). | `client_credentials` Bearer JWT (`ecommerce-internal-services-client`) |
| order-service | `specs/contracts/http/internal/order-existence.md` (`POST /api/internal/orders/existence`) | Orphan-coupon reconciliation: which coupon-holding order ids exist in any tenant (read-only) | `client_credentials` Bearer JWT (`ecommerce-internal-services-client`) |
| promotion-service | `specs/contracts/http/promotion-api.md` § `POST /api/internal/coupons/stale-used` and § `POST /api/internal/coupons/{couponId}/release` | Orphan-coupon reconciliation: list coupons `USED` longer than the floor, then release those whose order does not exist | none — internal network only (as for order-service's release call) |

## Publishes To
- None at current scope (may publish batch completion events in the future — contracts must be defined in `specs/contracts/events/` before implementation)

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- owning primary domain state (batch-worker operates on other services' data via published contracts only)
- calling non-public endpoints — **except** the four explicitly contracted internal endpoints listed under Allowed Service Interactions; no other internal/non-public endpoint may be called
- releasing a coupon on anything but an explicit "does not exist" answer — a failed, partial, empty-bodied or otherwise unreadable existence answer is **unknown** and releases nothing (TASK-INT-028 AC-4)

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
