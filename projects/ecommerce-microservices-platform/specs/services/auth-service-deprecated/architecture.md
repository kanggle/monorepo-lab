# Service Architecture

## Service
`auth-service`

## Service Type
`rest-api`

## Architecture Style
`Layered Architecture`

## Why This Architecture
This service is primarily CRUD-oriented and request/response-driven.

Business rules are important but relatively straightforward compared to domain-heavy services.

Maintainability, clarity, and fast operational development are prioritized.

## Internal Structure Rule
This service uses a layered internal structure.

Recommended internal layers:
- presentation
- application
- domain
- infrastructure

Package organization may follow package-by-layer or package-by-feature if the layered dependency rule is preserved.

## Allowed Dependencies
- presentation -> application
- application -> domain
- application -> infrastructure (via domain-defined interfaces only; concrete implementations are injected by the framework)
- infrastructure -> domain
- infrastructure -> framework and external libraries

## Forbidden Dependencies
- presentation must not access persistence directly
- presentation must not contain business rules
- domain must not depend on web framework code
- domain must not depend on controller/request classes
- repositories must not be called directly from controllers
- application must not import infrastructure utility classes directly (e.g., hashing, encoding utilities)
- application must access infrastructure behavior only through domain-layer interfaces or their return types

## Boundary Rules
- controllers handle HTTP mapping, validation entry, and response conversion
- application layer coordinates use-cases and transactions
- domain contains core business rules and entities
- infrastructure handles persistence, security integration, and framework adapters

## Integration Rules
- HTTP behavior must follow published contracts
- events, if any, must follow published event contracts
- persistence rules must follow service ownership boundaries
- shared libraries may be used only under shared-library policy

## Testing Expectations
Required emphasis:
- controller/API tests
- application service tests
- repository integration tests
- security-related tests where applicable

## Change Rule
Any architectural change to this service must be documented here first before implementation.