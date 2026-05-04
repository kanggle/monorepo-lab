# Service Architecture

## Service
`order-service`

## Service Type
`rest-api`

## Architecture Style
`DDD-style Architecture`

## Why This Architecture
This service owns domain rules that are central to business behavior.

Order lifecycle, invariants, aggregate consistency, and domain transitions are important.

This service requires explicit domain modeling rather than a simple CRUD-oriented structure.

## Internal Structure Rule
This service uses a DDD-style internal structure.

Recommended internal areas:
- presentation or interface
- application
- domain
- infrastructure

Recommended domain concepts:
- aggregates
- entities
- value objects
- domain services
- repositories
- domain events

Package organization should preserve aggregate boundaries and domain ownership.

## Allowed Dependencies
- interface -> application
- application -> domain
- infrastructure -> domain
- infrastructure -> application ports if defined

## Forbidden Dependencies
- domain must not depend on framework code
- domain must not depend on persistence implementation details
- application layer must not contain domain rules that belong inside aggregates or domain services
- controllers must not bypass application services
- repositories must not contain business decisions that belong to the domain

## Boundary Rules
- application layer orchestrates use-cases and transaction boundaries
- domain layer protects invariants and business rules
- infrastructure layer implements repositories and external integrations
- aggregate boundaries must be respected during modification

## Outbox

- Pattern: Transactional Outbox
- Table: `outbox` (libs/java-messaging 표준 schema)
- Polling scheduler: `OutboxPollingScheduler` (libs `com.example.messaging.outbox.OutboxPollingScheduler` base 의 concrete subclass)
- Topic 매핑:
  - `OrderPlaced` → `order.order.placed`
  - `OrderCancelled` → `order.order.cancelled`

## Integration Rules
- outbound events must follow published event contracts
- HTTP APIs must follow published HTTP contracts
- cross-service orchestration must follow use-cases and ownership rules
- shared libraries must not absorb order-domain logic

## Testing Expectations
Required emphasis:
- aggregate/domain behavior tests
- application service tests
- repository integration tests
- contract tests for published APIs/events

## Change Rule
Any architectural change to this service must be documented here first before implementation.