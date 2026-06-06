# Service Dependencies

## Service
`gateway-service`

## Allowed Direct Dependencies
- shared technical libraries allowed by platform policy
- Redis (rate limiting state — transient data only)

## Allowed Service Interactions
- OIDC token validation against GAP (iam-platform) — issuer + JWKS endpoint configuration only; no shared secret
- declarative routing to all downstream backend services via Spring Cloud Gateway configuration

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| GAP (iam-platform) | OIDC RS256 / JWKS | Token signature validation + `aud=ecommerce` + `tenant_id=ecommerce` claim enforcement (see [`../../integration/iam-integration.md`](../../integration/iam-integration.md)) |

## Publishes To
- None (gateway does not publish events or own APIs beyond proxied routes)

## Forbidden Dependencies
- direct database access (stateless gateway — no persistent data ownership)
- importing another service's internal code
- calling downstream services directly from application code (routing is declarative via configuration)
- owning business or domain logic of any kind

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
