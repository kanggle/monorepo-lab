# gateway-service Observability

Defines business metrics specific to gateway-service.

Platform-wide observability rules are defined in `specs/platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `gateway_requests_routed_total` | Counter | Total requests routed by target service |
| `gateway_rate_limited_total` | Counter | Total requests rejected by rate limiter by route |
| `gateway_jwt_validation_failure_total` | Counter | Total JWT validation failures by reason (expired, invalid, missing) |
| `gateway_upstream_error_total` | Counter | Total upstream service errors (5xx) by target service |

---

# Change Rule

New gateway-service metrics must be documented here before implementation.
