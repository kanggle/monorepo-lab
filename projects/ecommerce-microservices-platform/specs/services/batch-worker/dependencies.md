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
- **one internal system-command exception** — order-service `POST /api/internal/orders/confirm-paid-stale` (`client_credentials` Bearer, `ecommerce-internal-services-client`). This is the single non-read-only cross-service call batch-worker is authorized to make (TASK-BE-410 decision). order-service evaluates the predicate + performs the transition server-side; batch-worker only triggers it. NOT a database write into order's store.

## Consumes From

| Source | Contract | Purpose | Auth |
|---|---|---|---|
| product-service | `product-api.md` | Source data for Elasticsearch index consistency check | none (public read) |
| search-service | `search-api.md` | Index data for Elasticsearch index consistency check | none (public read) |
| order-service | `specs/contracts/http/internal/order-confirm-paid-stale.md` (`POST /api/internal/orders/confirm-paid-stale`) | Stale paid-order forward-confirm (`PENDING AND payment_id IS NOT NULL` → `CONFIRMED`); recovery for a lost confirm event. Disjoint from BE-138 (`payment_id IS NULL`). | `client_credentials` Bearer JWT (`ecommerce-internal-services-client`) |

## Publishes To
- None at current scope (may publish batch completion events in the future — contracts must be defined in `specs/contracts/events/` before implementation)

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- owning primary domain state (batch-worker operates on other services' data via published contracts only)
- calling non-public endpoints — **except** the one explicitly contracted order-service internal system-command above (`order-confirm-paid-stale.md`, `client_credentials`); no other internal/non-public endpoint may be called

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
