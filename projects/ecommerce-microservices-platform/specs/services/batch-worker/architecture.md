# Service Architecture

## Service
`batch-worker`

## Service Type
`batch-job`

## Architecture Style
`Layered Architecture`

## Why This Architecture
This service handles scheduled and batch processing tasks — periodic cleanup, data aggregation, and maintenance jobs.

Business rules are simple and repetitive. The primary concern is reliable execution, idempotency, and observability of each job run.

Layered Architecture provides clear separation between job scheduling, business logic, and infrastructure access without unnecessary complexity.

## Internal Structure Rule
This service uses a layered internal structure.

Recommended internal layers:
- scheduling (job definitions, cron triggers)
- application (job execution logic, orchestration)
- domain (business rules, shared value objects)
- infrastructure (persistence, external service clients, messaging)

Package organization may follow package-by-layer or package-by-feature if the layered dependency rule is preserved.

## Allowed Dependencies
- scheduling -> application
- application -> domain
- application -> infrastructure (via domain-defined interfaces only; concrete implementations are injected by the framework)
- infrastructure -> domain
- infrastructure -> framework and external libraries

## Forbidden Dependencies
- scheduling must not access persistence directly
- scheduling must not contain business rules
- domain must not depend on scheduling framework code
- domain must not depend on infrastructure utility classes directly

## Boundary Rules
- scheduling layer handles cron/trigger configuration and job entry points
- application layer coordinates job steps and transactions
- domain contains business rules and value objects
- infrastructure handles persistence, HTTP clients, and messaging adapters

## Owned Data
- `batch_job_execution_history` — job execution history (job name, status, started_at, finished_at, error_message)

## Published Interfaces
- None (batch-worker does not expose HTTP APIs)
- May publish domain events to notify other services of completed batch operations (contracts must be defined in `specs/contracts/events/` before implementation)

## Consumed Interfaces
- Consumes other services' published HTTP contracts (read-only) for data verification
- Consumes domain events from other services for batch processing triggers
- Must not access other services' databases directly (per `service-boundaries.md`)

## Dependencies
- PostgreSQL (own database — job execution history only)
- Kafka (event consumption/publication)
- product-service via published HTTP contract (read-only, for index consistency check)
- search-service via published HTTP contract (read-only, for index consistency check)

For full dependency rules, see `dependencies.md`.

## Key Jobs (planned)
- Expired session cleanup (auth-service sessions past inactivity timeout)
- Stale order cancellation (orders stuck in PENDING beyond timeout)
- Daily sales aggregation (order/payment summary)
- Elasticsearch index consistency check (product data sync verification)

## Integration Rules
- Each job must be idempotent — safe to re-run without side effects
- Jobs must log execution start, completion, and failure with structured logging
- Failed jobs must not block other job executions
- Events published by batch jobs must follow published event contracts

## Testing Expectations
Required emphasis:
- Job logic unit tests (application layer)
- Integration tests for database queries
- Idempotency verification tests
- Scheduling configuration tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
