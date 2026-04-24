# Service Architecture

## Service
`search-service`

## Service Type
`rest-api`

## Architecture Style
`Hexagonal Architecture`

## Why This Architecture
This service depends critically on Elasticsearch as an external system.

Business logic (relevance rules, filter mapping, index document structure) must remain independent from the specific Elasticsearch client version or SDK.

Hexagonal architecture isolates the search engine behind outbound ports, making the service testable and replaceable.

## Internal Structure Rule
This service uses a ports-and-adapters structure.

Recommended internal areas:
- inbound adapters (HTTP query handlers, event consumers)
- application
- domain
- outbound ports
- outbound adapters (Elasticsearch adapter, event consumer adapter)

Business logic must remain independent from Elasticsearch SDK details.

## Allowed Dependencies
- inbound adapters -> application
- application -> domain
- application -> ports
- outbound adapters -> ports
- outbound adapters -> Elasticsearch SDK or messaging infrastructure

## Forbidden Dependencies
- domain must not depend on Elasticsearch SDK
- application must not reference Elasticsearch query DSL directly
- adapters must not own search ranking or filter business rules
- HTTP handlers must not bypass application services

## Boundary Rules
- inbound HTTP adapter translates search requests into application queries
- inbound event consumer adapter translates product events into index commands
- application layer coordinates search and indexing use-cases through ports
- domain contains search relevance rules, filter logic, and index document structure
- outbound adapter implements Elasticsearch operations behind ports

## Integration Rules
- product-service events must be consumed through published event contracts
- Search query HTTP API must follow published HTTP contracts
- Elasticsearch dependency must be isolated behind outbound ports
- Index schema changes must be managed through a versioned migration strategy

## Testing Expectations
Required emphasis:
- application service tests (with mocked ports)
- outbound adapter tests against real Elasticsearch (Testcontainers)
- event consumer integration tests
- search query accuracy tests
- index sync failure and retry scenario tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
