# API Gateway Policy

Defines the role, responsibilities, and rules of an API gateway in any project built on this platform. Every project that exposes HTTP traffic to external clients has a gateway service (declared in `PROJECT.md` with `Service Type = rest-api` and role = gateway).

This document states **what the gateway must do and must not do**. Concrete route lists, public endpoints, and rate limit tiers are declared per project — not here.

---

# Role

The gateway is the **single entry point** for all external client requests (browser, mobile, third-party integrations).

- All external traffic MUST pass through the gateway.
- No backend service may be directly exposed to external traffic.
- The gateway is owned by the project (declared as a service in `PROJECT.md`).

---

# Responsibilities

The gateway performs these responsibilities for every request:

| Responsibility | Description |
|---|---|
| **Routing** | Forward the request to the appropriate downstream service based on path / method |
| **Authentication** | Validate JWT access tokens on all non-public routes |
| **Identity Propagation** | Strip client-supplied identity headers; set verified identity from JWT claims |
| **Rate Limiting** | Apply per-client and per-route rate limits |
| **CORS** | Manage allowed origins, methods, and headers centrally |
| **Request Logging** | Log inbound request metadata (method, path, status, latency) without sensitive data |
| **Trace Context** | Generate or propagate `X-Request-Id` and OTel trace context |
| **Error Envelope** | Ensure gateway-level errors follow `error-handling.md` envelope |

The gateway MUST NOT:

- Contain business logic
- Persist domain data
- Own aggregate state
- Rewrite response bodies beyond adding correlation headers
- Accept client-supplied identity headers (`X-User-Id`, etc.) — strip and set from JWT

---

# Authentication at the Gateway

- The gateway verifies the `Authorization: Bearer <token>` header on protected routes.
- **On valid JWT**: forward the request with verified identity headers derived from the token's claims:
  - `X-User-Id` (from `sub` claim)
  - `X-User-Role` (from `role` or `roles` claim)
  - `X-User-Email` (from `email` claim, if present)
  - Additional claims as declared in the project's auth spec
- **On invalid or missing JWT**: return `401 UNAUTHORIZED` immediately without forwarding.
- **Public routes** (no JWT required) MUST be explicitly listed in the gateway route configuration. There is no implicit public access.

## Public Route Declaration (per-project)

Each project declares its public routes in:

- `specs/services/<gateway-service>/public-routes.md` OR
- Inline in the gateway's Spring Cloud Gateway config (with a comment pointing to the spec)

The platform does not prescribe which routes are public. Typical categories that a project may expose publicly:

- Health / readiness endpoints (`/actuator/health`)
- Authentication flows (signup, login, token refresh), if an auth service exists
- Explicitly read-only public catalog or content endpoints (if the domain has public-facing read data)

**Default**: no route is public. Every route requires authentication unless listed as public.

---

# Identity Header Handling

- The gateway strips any client-supplied `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-Actor-Id` headers **before** the JWT filter runs.
- The JWT filter then sets these headers authoritatively from verified token claims.
- Downstream services trust these headers only because they come from the gateway. A service MUST NOT accept these headers from any other source.

This is a security boundary — incorrect ordering (setting before stripping) creates an impersonation vulnerability.

---

# Service Trust Model

- Services behind the gateway may trust `X-User-Id`, `X-User-Role`, and `X-Request-Id` headers as set by the gateway.
- Services MUST NOT accept these headers from external clients directly.
- Services MUST still enforce their own **authorization** logic (role-based or resource-based) beyond identity — the gateway handles authentication, not authorization.

---

# Rate Limiting

- The gateway applies rate limits per `(clientIp, routeId)` tuple by default.
- Exceeding the limit returns `429 RATE_LIMIT_EXCEEDED` with a `Retry-After` header.
- Rate limits are configured per route and declared in the project's gateway spec.
- Typical tier structure (a project chooses its values):

| Tier | Default Guidance |
|---|---|
| Standard (default) | ~100 req/min per IP per route |
| Sensitive (auth, credential handling) | Stricter — ~10 req/min per IP (brute-force protection) |
| Internal-only | Higher or unlimited (internal traffic) |

- **Redis unavailable for rate limit counters**: fail **open** (allow request, log at WARN, alert). Rate limiting is a soft protection, not a correctness boundary.

---

# CORS

- Allowed origins are declared per environment (not hardcoded in code).
- Allowed methods: those declared by the project's public contracts.
- Allowed headers: at minimum `Authorization`, `Content-Type`, `X-Request-Id`, `Idempotency-Key` (when mutating endpoints use it).
- Preflight `OPTIONS` requests are handled by the CORS filter without invoking the JWT filter.

---

# Error Responses

Gateway-level errors (before reaching a service) follow the platform error response format defined in `error-handling.md`:

```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

Common gateway-level errors:

| Code | HTTP | When |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing or invalid JWT |
| `FORBIDDEN` | 403 | Valid JWT but lacks required role for route |
| `RATE_LIMIT_EXCEEDED` | 429 | Rate limit hit |
| `SERVICE_UNAVAILABLE` | 503 | Downstream service unreachable |
| `CIRCUIT_OPEN` | 503 | Circuit breaker open on downstream |
| `DOWNSTREAM_ERROR` | 502 | Downstream returned 5xx / timed out after retries |

---

# Request / Response Transparency

- The gateway is a **transparent proxy**. It does not alter:
  - Downstream response status codes
  - Downstream response bodies
  - Downstream response headers (except it adds/echoes `X-Request-Id`)
- If a downstream response body does not conform to the platform error envelope on an error status, the gateway normalizes it to the envelope (defensive only; downstream services should comply).

---

# Observability

- Every request produces a structured access log line with: method, path, status, latency, `X-Request-Id`, user id (when authenticated), client IP, user-agent.
- No access log line contains: full `Authorization` header, request body, response body, or any client-identified secret.
- Metrics: request rate, error rate by status code, latency histogram — per route and per downstream service.
- Traces: the gateway is the root span for external requests; trace context propagates to downstream services.

---

# Change Rule

Any change to gateway behavior — new filter, new rate limit tier, new public route, new identity header policy — must be documented in this file (if it affects all projects) or in the project's gateway spec (if project-specific) **before** deployment.

---

# How Each Project Configures Its Gateway

1. Declare the gateway service in `PROJECT.md` (`Service Type: rest-api`, role: gateway).
2. Write `specs/services/<gateway-service>/architecture.md` declaring the gateway's internal architecture (typically Layered for gateway services).
3. List public routes in a project-owned spec file (e.g., `specs/services/<gateway-service>/public-routes.md`).
4. Configure route definitions and filters in Spring Cloud Gateway config (YAML or programmatic).
5. Wire JWT validation using a JWKS URL or HMAC secret as declared in the project's auth strategy.
6. Configure rate limit tiers per route.
7. Follow this document's responsibilities and constraints.

References:

- `service-types/rest-api.md` — gateway is a rest-api service type
- `architecture.md` — overall system rule on gateway as sole external entry point
- `service-boundaries.md` — the gateway service type boundary rules
- `error-handling.md` — error envelope format
- `security-rules.md` — JWT validation and secret management
- `rules/traits/integration-heavy.md` — circuit breaker / retry patterns when gateway → downstream has resilience needs
