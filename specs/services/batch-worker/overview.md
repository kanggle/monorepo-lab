# Service Overview

## Service
`batch-worker`

## Responsibility
Owns scheduled and batch processing tasks for platform-wide maintenance, cleanup, and data aggregation.

## In Scope
- expired session cleanup (auth-service sessions past inactivity timeout)
- stale order cancellation (orders stuck in PENDING beyond timeout)
- daily sales aggregation (order/payment summary)
- Elasticsearch index consistency check (product data sync verification)
- job execution history tracking and observability

## Out of Scope
- real-time request handling (no HTTP APIs exposed)
- business domain ownership (operates on other services' data via approved contracts)
- user-facing functionality

## Owned Data
- batch job execution history (job name, status, started_at, finished_at, error_message)

## Published Interfaces
- None (does not expose HTTP APIs)
- May publish domain events to notify other services of completed batch operations (contracts must be defined in `specs/contracts/events/` before implementation)

## Dependent Systems
- PostgreSQL (own database — job execution history only)
- Kafka (event consumption and publication)
- product-service via published HTTP contract (read-only, for index consistency check)
- search-service via published HTTP contract (read-only, for index consistency check)

For full dependency rules, see `dependencies.md`.
