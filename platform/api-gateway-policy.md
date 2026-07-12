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

## Key shape

A rate limit is only as good as the thing it counts. The key decides **who gets throttled**, so it is a design rule, not a config detail.

- **Anonymous / pre-auth traffic** — key on `(clientIp, routeId)`. There is no identity to key on, and IP is the only bound available.
- **Authenticated traffic** — key on the **authenticated principal** (`sub`), not the client IP. An authenticated caller is individually identifiable; collapsing them into an IP bucket means everyone behind one NAT shares a limit while an abuser rotating IPs is never throttled per account.
- **Tenant segment** — add a tenant component **only where the tenant claim is genuinely variable** for the traffic in question.
  > **A claim that your own config pins to a constant is not a bucket.** If a gateway requires `tenant_id == <its own domain>`, then every token past its gate carries the same value, and keying on it yields **one global bucket** wearing the costume of per-tenant isolation. Verify the claim varies before you key on it. (TASK-MONO-368 — this is not hypothetical; ecommerce shipped exactly that, and every authenticated shopper shared one bucket.)
- **Key prefix** — prefix keys with the project (`rate:<project>:…`) so two domains sharing a Redis cannot collide.
- **Deviations must be recorded** in the project's `PROJECT.md` § Overrides with a reason. A gateway that silently keys differently from this section is drift, not a decision.

### Current fleet (2026-07-12)

| Gateway | Anonymous | Authenticated | Note |
|---|---|---|---|
| ecommerce | `ip` | `t:<tenant>:acct:<sub>` | tenant is real for assume-tenant tokens; account restores per-caller isolation (MONO-368) |
| wms / scm / fan / finance / erp | `ip` | `acct:<sub>` | conforms |
| iam | `ip` (login / signup — pre-auth by definition) | `acct:<sub>` (refresh) | conforms; it rate-limits *before* signature verification, so it reads `sub` from the decoded payload rather than a Spring `Jwt` |

**No recorded deviations.** wms held one until TASK-MONO-370: every wms route is authenticated, yet it keyed by IP — which was **compliance with the previous rule**, not drift, since L92 declared `(clientIp, routeId)` as the platform default. When this section was raised (MONO-368) wms was entered as an explicit deviation rather than changed silently, because it alters who gets 429'd on a live edge. MONO-370 made the decision and aligned it. The guard's `RATELIMIT_IP_ONLY_ALLOWLIST` is now **empty, and should stay that way** — an entry there is a promise that someone wrote down why.

**A degrade rule that is easy to get wrong.** When no usable principal is present (no security context, or a token with no `sub`), fall back to the **IP key**. Do not build `"acct:" + subject` unguarded: a null subject concatenates to the literal key `acct:null`, which merges every such caller into one synthetic shared bucket. In Reactor, `map` cannot express "no identity" — a `map` lambda returning null throws — so use `flatMap` + `Mono.justOrEmpty`. All five reactive gateways carried the unguarded form until TASK-MONO-370.

## Behaviour

- Exceeding the limit returns `429 RATE_LIMIT_EXCEEDED` with a `Retry-After` header.
- Rate limits are configured per route and declared in the project's gateway spec.
- Typical tier structure (a project chooses its values):

| Tier | Default Guidance |
|---|---|
| Standard (default) | ~100 req/min per **key** per route (§ Key shape — account when authenticated, IP when not) |
| Sensitive (auth, credential handling) | Stricter — ~10 req/min. These routes are pre-auth by definition, so the key **is** the IP (brute-force protection) |
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

Common gateway-level errors: the gateway emits `UNAUTHORIZED`, `FORBIDDEN`,
`RATE_LIMIT_EXCEEDED`, `SERVICE_UNAVAILABLE`, `CIRCUIT_OPEN`, and
`DOWNSTREAM_ERROR`. **Code value, HTTP status, and canonical semantics are
defined once in [`error-handling.md`](error-handling.md)** (§ Authentication /
Authorization / Rate Limiting / General — the single platform error-code
catalog, per TASK-MONO-051/052); they are intentionally not re-tabulated here
to prevent definition drift.

Gateway-specific behavioral nuance (not a code redefinition): `CIRCUIT_OPEN`
(503) is emitted when the downstream Resilience4j breaker is OPEN — the call
is shed without reaching the dependency — and is deliberately distinct from
`DOWNSTREAM_ERROR` (502), where the gateway did reach the downstream and it
returned 5xx / timed out after retries. Dashboards rely on this split to
separate "we shed load" from "we tried and it failed".

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
