# Service Architecture

## Service
`gateway-service`

## Service Type
`rest-api`

## Architecture Style
`Layered Architecture`

## Why This Architecture
This service is a WebFlux-based API gateway.

Its primary responsibilities are request routing, rate limiting, JWT authentication filtering, and request logging.

There is no domain logic — all behavior is infrastructure-oriented (routing rules, filters, security).

A layered structure keeps filter chains, configuration, and security concerns cleanly separated without the overhead of domain-driven design.

## Internal Structure Rule
This service uses a layered internal structure.

Recommended internal layers:
- filter (request/response processing pipeline)
- config (Spring configuration, bean definitions)
- security (JWT validation, authentication logic)

Package organization follows package-by-layer.

## Allowed Dependencies
- filter -> config
- filter -> security (JWT validation utilities)
- config -> framework and external libraries

## Forbidden Dependencies
- This service must not contain business/domain logic
- Filters must not call downstream services directly — routing is declarative via configuration
- This service must not own persistent data (stateless gateway)
- Security layer must not duplicate GAP (global-account-platform) authorization rules — only validate RS256 signatures, `aud=ecommerce`, and `tenant_id=ecommerce` claims

## Boundary Rules
- Filters handle cross-cutting concerns: authentication, logging, rate limiting
- Configuration declares routing rules, CORS, and rate limiter settings
- Security handles JWT token parsing and validation only
- All downstream service calls are handled by Spring Cloud Gateway routing, not application code

## Integration Rules
- Routing targets must match published service URLs
- JWT validation must follow the GAP OIDC token contract (RS256 via JWKS, `aud=ecommerce`, `tenant_id=ecommerce`); see [`../../integration/gap-integration.md`](../../integration/gap-integration.md)
- Rate limiting configuration must use Redis for distributed state
- Shared libraries may be used only under shared-library policy

## Testing Expectations
Required emphasis:
- Filter unit tests (JWT validation, request logging behavior)
- Integration tests with Redis (rate limiting)
- Route configuration tests (correct path-to-service mapping)
- Security filter chain tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
