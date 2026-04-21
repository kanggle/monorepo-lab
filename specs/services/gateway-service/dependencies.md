# Service Dependencies

## Service
`gateway-service`

## Allowed Direct Dependencies
- shared technical libraries allowed by platform policy
- Redis (rate limiting state — transient data only)

## Allowed Service Interactions
- JWT secret shared with auth-service for token validation
- declarative routing to all downstream backend services via Spring Cloud Gateway configuration

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| auth-service | JWT token contract | Token validation (shared secret) |

## Publishes To
- None (gateway does not publish events or own APIs beyond proxied routes)

## Forbidden Dependencies
- direct database access (stateless gateway — no persistent data ownership)
- importing another service's internal code
- calling downstream services directly from application code (routing is declarative via configuration)
- owning business or domain logic of any kind

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
