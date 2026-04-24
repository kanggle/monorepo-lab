# Service Architecture

## Service
`review-service`

## Service Type
`rest-api`

## Architecture Style
`DDD-style Architecture`

## Why This Architecture
Review and rating management involves meaningful domain concepts: review aggregates, rating calculations, and purchase verification constraints.

Domain invariants (e.g. one review per user per product, rating must be 1-5, only purchasers can write reviews) require aggregate-level enforcement.

DDD-style keeps these rules in the domain layer and prevents them from leaking into infrastructure or presentation.

## Internal Structure Rule
This service uses a domain-driven internal structure.

Recommended internal areas:
- interface (presentation)
- application
- domain
- infrastructure

Key domain concepts:
- Aggregates: Review
- Entities: none (Review is the root)
- Value Objects: Rating (1-5), ReviewStatus (ACTIVE, DELETED)
- Domain Events: ReviewCreated, ReviewUpdated, ReviewDeleted
- Domain Services: AverageRatingCalculator
- Repositories: ReviewRepository

## Allowed Dependencies
- interface -> application
- application -> domain
- infrastructure -> domain
- infrastructure -> application ports

## Forbidden Dependencies
- domain must not depend on framework or persistence details
- application must not contain domain rules that belong in aggregates
- controllers must not bypass application services
- repositories must not contain business decisions

## Boundary Rules
- interface layer handles HTTP mapping and request validation entry
- application layer coordinates use-cases and transaction boundaries
- domain layer owns review rules and rating invariants
- infrastructure layer handles persistence, event publishing, and external adapters

## Domain Scope
- Review (user reference, product reference, rating, title, content, status, timestamps)
- Average rating per product (materialized view or cache)

## Domain Constraints
- review-service must NOT own product or order data
- One review per user per product (unique constraint)
- Rating must be between 1 and 5
- Only users who purchased the product can write reviews (verified via order-service)
- Deleted reviews are soft-deleted (status change)

## Integration Rules
- HTTP behavior must follow published contracts
- Domain events must follow published event contracts
- Purchase verification calls order-service via synchronous HTTP
- search-service consumes review events for rating index updates
- Shared libraries may be used only under shared-library policy

## Events
- Publishes: `ReviewCreated`, `ReviewUpdated`, `ReviewDeleted`
- Consumes: none

## Testing Expectations
Required emphasis:
- aggregate and domain rule tests
- application service tests
- repository integration tests
- event publishing tests
- purchase verification integration tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
