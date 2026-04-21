# Service Architecture

## Service
`user-service`

## Service Type
`rest-api`

## Architecture Style
`Layered Architecture`

## Why This Architecture
This service is primarily CRUD-oriented, managing user profile data.

Domain rules are simple: profile field validation, address format checks, and basic constraints (e.g., maximum addresses per user).

There are no complex aggregates or external system integrations that would justify DDD or Hexagonal architecture.

Maintainability and fast development are prioritized.

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
- application must not import infrastructure utility classes directly
- application must access infrastructure behavior only through domain-layer interfaces or their return types

## Boundary Rules
- controllers handle HTTP mapping, validation entry, and response conversion
- application layer coordinates use-cases and transactions
- domain contains core business rules and entities
- infrastructure handles persistence, security integration, and framework adapters

## Domain Scope
- User profile (nickname, email, phone, profile image URL)
- User addresses (shipping addresses, default address designation)
- Profile status (active, suspended, withdrawn)
- Wishlist (user-product associations, product info fetched from product-service at query time)

## Domain Constraints
- user-service must NOT own authentication credentials (owned by auth-service)
- user-service must NOT replicate or cache auth tokens
- user ID is issued by auth-service; user-service receives it as an external identifier
- profile data is exposed only through published contracts

## Integration Rules
- HTTP behavior must follow published contracts
- events must follow published event contracts
- persistence rules must follow service ownership boundaries
- shared libraries may be used only under shared-library policy
- auth-service creates the user ID; user-service creates the profile record upon receiving a UserSignedUp event

## Events
- Consumes: `UserSignedUp` (from auth-service) — triggers initial profile creation
- Publishes: `UserProfileUpdated`, `UserWithdrawn`

## Testing Expectations
Required emphasis:
- controller/API tests
- application service tests
- repository integration tests
- event consumer tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
