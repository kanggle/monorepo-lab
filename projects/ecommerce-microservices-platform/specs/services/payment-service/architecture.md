# Service Architecture

## Service
`payment-service`

## Service Type
`rest-api`

## Architecture Style
`Hexagonal Architecture`

## Why This Architecture
This service depends heavily on external systems and integration boundaries.

Payment processing requires clear separation between business logic and external adapters.

Testability, adapter isolation, and boundary control are critical.

## Internal Structure Rule
This service uses a ports-and-adapters structure.

Recommended internal areas:
- inbound adapters
- application
- domain
- outbound ports
- outbound adapters

Business logic must remain independent from specific framework or vendor integrations.

## Allowed Dependencies
- inbound adapters -> application
- application -> domain
- application -> ports
- outbound adapters -> ports
- outbound adapters -> external SDKs or infrastructure

## Forbidden Dependencies
- domain must not depend on framework or vendor SDK code
- application must not depend directly on external payment vendor implementations
- adapters must not own business policy
- controllers or message consumers must not bypass application services

## Boundary Rules
- inbound adapters translate external input into application commands
- application layer coordinates use-cases through ports
- domain contains payment rules and decision logic
- outbound adapters implement external gateway, database, messaging, or notification integrations

## Integration Rules
- external payment provider integration must be isolated behind outbound ports
- HTTP and event contracts must follow published contracts
- retry, timeout, and failure behavior must be implemented through adapter/application coordination
- shared libraries may support technical concerns only

## Testing Expectations
Required emphasis:
- application service tests
- port contract tests
- outbound adapter tests
- integration tests for provider interaction boundaries
- failure and retry scenario tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.