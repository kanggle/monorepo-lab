# Task ID

TASK-INT-001

# Title

Wire gateway-service route to master-service

# Status

ready

# Owner

integration

# Task Tags

- api
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Add a route in `gateway-service` that proxies `/api/v1/master/**` requests to
`master-service`, applying the standard gateway responsibilities (JWT auth, header
enrichment, rate limiting, request logging, CORS). This unlocks external clients
(admin UI, other services via the gateway) to reach every master-service endpoint.

On completion:

- External callers can successfully hit every master-service endpoint through
  the gateway (`https://{gateway}/api/v1/master/**`).
- All master-service endpoints are **protected** (JWT required) — there are no
  public master-service routes in v1.
- Gateway forwards verified identity headers (`X-User-Id`, `X-User-Role`,
  `X-Request-Id`, `X-Actor-Id`) to master-service.
- Rate limiting applies per gateway policy.
- Failure modes (master-service down, JWT invalid) are handled with the correct
  HTTP status and platform-common error envelope.

This task is a sibling of `TASK-BE-001-master-service-bootstrap.md`. It may be
implemented in parallel but its own acceptance depends on master-service being
bootable (at minimum `/actuator/health` + the Warehouse endpoints). End-to-end
verification requires both merged.

---

# Scope

## In Scope

- `gateway-service` bootstrap (Spring Cloud Gateway) if not already present —
  Gradle module, base configuration, health endpoint
- Route definition for `/api/v1/master/**` → `master-service`
- JWT validation filter applied to the route (all master-service paths require
  `Authorization` bearer token)
- Header enrichment filter:
  - Generate / echo `X-Request-Id` (UUID v4) if absent
  - Extract JWT claims to set `X-User-Id`, `X-User-Email`, `X-User-Role`,
    `X-Actor-Id` headers on the forwarded request
- Rate limiting per `platform/api-gateway-policy.md` default (100 rpm per IP;
  master endpoints use the default tier, not the stricter auth-endpoint tier)
- CORS configuration (allowed origins from environment, methods including
  `PATCH`, allowed headers including `Idempotency-Key`)
- Error handling: gateway-level errors (JWT invalid, circuit open, master-service
  unreachable) return the platform-common error envelope
- Structured request logging: method, path, status, latency, `requestId`,
  `userId`; excludes sensitive headers (no `Authorization`, no request body)
- OTel trace context propagation across the gateway hop
- Health/readiness probes suitable for Kubernetes
- `gateway-service` Docker image build
- Integration test: end-to-end request through the gateway to master-service
  (at least one GET, one POST with `Idempotency-Key`)

## Out of Scope

- Routes to any other service (inventory, inbound, outbound, notification, admin,
  master-write-only admin surface, etc.) — each gets its own TASK-INT
- Public (auth-free) routes — v1 wms has none
- Canary / traffic splitting
- Response caching at the gateway
- mTLS to downstream services (internal cluster assumption)
- User-facing login / token issuance (no `auth-service` in current PROJECT.md
  service map — flag this gap in review, see Implementation Notes)
- Updating `platform/api-gateway-policy.md` content (the public-route list in
  that doc still references old ecommerce paths; doc-cleanup is a separate task)

---

# Acceptance Criteria

- [ ] `apps/gateway-service/build.gradle` builds cleanly: `./gradlew :apps:gateway-service:build`
- [ ] `docker-compose up` + `./gradlew :apps:gateway-service:bootRun` boots the gateway locally
- [ ] `GET {gateway}/actuator/health` returns `200 UP`
- [ ] `GET {gateway}/api/v1/master/warehouses` without `Authorization` header returns `401 UNAUTHORIZED` with platform error envelope; request **never reaches** master-service (verify via master-service metrics / logs)
- [ ] `GET {gateway}/api/v1/master/warehouses` with a valid JWT forwards to master-service and returns `200` with the master-service response body
- [ ] `POST {gateway}/api/v1/master/warehouses` with valid JWT + `Idempotency-Key` reaches master-service, creates the warehouse, and returns `201` with the resource
- [ ] Forwarded request carries `X-User-Id`, `X-User-Role`, `X-Request-Id`, `X-Actor-Id`, `Idempotency-Key` (verified by a capture test on master-service side)
- [ ] Forwarded request **strips** client-supplied `X-User-Id` / `X-User-Role` / `X-Actor-Id` headers — gateway sets them authoritatively from the JWT, not from the incoming request
- [ ] Rate limiting: sending > 100 requests in 60s from the same IP returns `429 RATE_LIMIT_EXCEEDED` with `Retry-After` header
- [ ] When master-service is down, gateway returns `503 SERVICE_UNAVAILABLE`; when master-service times out per the configured timeout, gateway returns `504` or `503 DOWNSTREAM_ERROR` (choose one and document)
- [ ] OTel trace context propagates: a single trace spans `gateway` → `master-service`
- [ ] CORS preflight (`OPTIONS`) for `/api/v1/master/**` returns allowed headers including `Idempotency-Key`, `Authorization`, `Content-Type`, `X-Request-Id`
- [ ] Sensitive data (full `Authorization` token, request body) is absent from access logs
- [ ] Integration test passes: `e2e-gateway-master.spec` exercising the happy path and the 401 / 429 / 503 paths
- [ ] Contract usage is consistent with `specs/contracts/http/master-service-api.md` — the gateway never rewrites paths, status codes, or error envelopes beyond adding `X-Request-Id`
- [ ] Docker image builds via the platform image workflow

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/api-gateway-policy.md` (note: the Public Routes list is stale for wms — flag in review)
- `platform/architecture.md` (stale service table — flag in review; PROJECT.md wins)
- `platform/service-types/rest-api.md` — Gateway Routing section
- `platform/error-handling.md` — platform-common error envelope
- `platform/observability.md` — trace propagation and logging requirements
- `specs/services/master-service/architecture.md` — upstream target
- `rules/traits/integration-heavy.md` — circuit breaker / retry / timeout patterns for the gateway → master hop

# Related Skills

- `.claude/skills/backend/gateway-security/SKILL.md`
- `.claude/skills/backend/jwt-auth/SKILL.md`
- `.claude/skills/backend/rate-limiting/SKILL.md`
- `.claude/skills/service-types/rest-api-setup/SKILL.md`
- `.claude/skills/cross-cutting/observability-setup/SKILL.md`
- `.claude/skills/cross-cutting/security-hardening/SKILL.md`
- `.claude/skills/testing/e2e-test/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`
- `.claude/skills/infra/docker-build/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/master-service-api.md` — the contract the gateway exposes transparently; the gateway adds `X-Request-Id` but does not rewrite otherwise

---

# Participating Components

- `gateway-service` (this task's primary deliverable)
- `master-service` (downstream; bootstrapped by `TASK-BE-001`)
- Redis (for rate-limiting counters — same cluster as master-service's idempotency Redis is acceptable; separate logical DB recommended)
- JWT issuer / key source (see Implementation Notes on the auth-service gap)

---

# Trigger

Any external HTTP request matching path prefix `/api/v1/master/`.

---

# Expected Flow

1. Client sends `{method} /api/v1/master/...` with `Authorization: Bearer <jwt>` and `Idempotency-Key` (for mutating endpoints).
2. Gateway assigns / echoes `X-Request-Id`.
3. Gateway's rate-limit filter checks the counter for the client IP + route; if over limit → `429 RATE_LIMIT_EXCEEDED`.
4. Gateway's JWT filter validates signature, expiry, and claims. On failure → `401 UNAUTHORIZED`.
5. Gateway strips any client-supplied identity headers and sets `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-Actor-Id` from verified JWT claims.
6. Gateway forwards the request to `master-service` via Spring Cloud Gateway's routing with trace context propagated through OTel headers.
7. `master-service` processes (including its own idempotency check, authorization, domain logic) and returns a response.
8. Gateway returns the response unchanged (except that `X-Request-Id` is always present).
9. Access log entry emitted: method, path, status, latency, `X-Request-Id`, user id (if known), IP, user-agent. No sensitive payload.

---

# Edge Cases

- Client omits `X-Request-Id` → gateway generates one (UUID v4) and sets it on both request and response.
- Client provides `X-Request-Id` → gateway echoes it back on the response (idempotent correlation).
- Client sends conflicting identity headers (e.g., `X-User-Id` that does not match the JWT subject) → gateway strips and overwrites with JWT-derived values. Do **not** reject; this is a benign misuse by bad/legacy clients.
- JWT valid but missing the `role` claim → populate `X-User-Role` as empty string; master-service authorization layer decides.
- JWT signed by an unknown key → `401 UNAUTHORIZED`.
- JWT expired → `401 UNAUTHORIZED` with a specific message; do **not** leak the expiry timestamp.
- Client sends `Idempotency-Key` header → pass through to master-service unchanged.
- Extremely long request body (> 10 MB) → gateway rejects with `413 PAYLOAD_TOO_LARGE`. Default body size cap set in config.
- Client sends `OPTIONS` preflight → CORS filter responds without invoking JWT filter.

---

# Failure Scenarios

- `master-service` returns `5xx` → gateway passes status through but normalizes body to the platform error envelope if master did not already comply.
- `master-service` connection refused → gateway returns `503 SERVICE_UNAVAILABLE`.
- `master-service` timeout (> 10s configured) → gateway returns `504 DOWNSTREAM_ERROR` (or `503`; choose and document consistently).
- `master-service` circuit-breaker opens (integration-heavy trait I1/I2) → subsequent calls return `503 CIRCUIT_OPEN` until the half-open probe.
- Redis unavailable for rate-limit counter → fail **open** for rate limiting only (allow requests; log at WARN and alert). This is the opposite of idempotency's fail-closed policy; justified because rate limiting is a soft-protection layer, not a correctness layer.
- JWT key source unavailable → fail closed: return `503 SERVICE_UNAVAILABLE`. Cannot validate tokens → cannot serve traffic.
- Gateway crashes mid-request → client sees TCP reset; client retries with the same `Idempotency-Key` and reaches master-service again (which replays).

---

# Test Requirements

## Unit / Slice Tests

- Gateway filter tests (in isolation): JWT filter, header enrichment filter, rate-limit filter. Cover valid / invalid / expired JWT; rate-limit under and over threshold; header stripping.

## Integration Tests

- Testcontainers-based end-to-end:
  1. Gateway + master-service + Postgres + Kafka + Redis in one docker network.
  2. Hit `GET /api/v1/master/warehouses` via gateway with valid JWT → 200.
  3. Hit same path without JWT → 401, and assert master-service did not receive the request (by checking master-service access log or metrics counter).
  4. Create a warehouse through the gateway → 201 with resource.
  5. Rate limit: fire 120 requests in rapid succession → first 100 succeed, remainder return 429.
  6. Pause master-service container → gateway returns 503.
  7. Validate OTel trace spans both services (use a test tracer exporter).

## Contract Tests

- The gateway MUST NOT alter status codes or bodies of master-service responses (apart from `X-Request-Id`). Verify by replaying each of the master-service contract tests through the gateway and asserting identical response bodies.

## Security Tests

- Client cannot impersonate another user by setting `X-User-Id`; gateway overrides.
- Request logs contain no full `Authorization` header and no request bodies.

---

# Implementation Notes

- **Auth-service gap**: `PROJECT.md` service map does not yet include an `auth-service`. For this task, the JWT validation uses a **static JWKS URL** or a local HMAC secret provided via environment variable (choose one; document the choice in `gateway-service/README.md`). Introducing a full `auth-service` is a separate future decision. Document this as a known limitation in the task's review note.
- **Route declaration**: use Spring Cloud Gateway route DSL (YAML or programmatic). YAML is preferred for explicitness.
- **Header stripping order matters**: strip client-supplied identity headers **before** the JWT filter sets new ones, so malicious clients cannot slip headers through.
- **Rate limit key**: `(clientIp, routeId)`. IP is taken from `X-Forwarded-For` if a trusted proxy is in front (document the trust config).
- **Timeouts**: downstream connect timeout 2s, read timeout 10s (override via env). Signal expiry via the circuit breaker.
- **Circuit breaker**: use Resilience4j per the integration-heavy trait rules. Config lives with the route in gateway config; defaults: sliding window 20 requests, error threshold 50%, half-open after 30s.
- **No response body rewriting** beyond adding `X-Request-Id` header. The gateway is a transparent proxy, not a response transformer. If in doubt, do less.
- **Known spec inconsistency**: `platform/architecture.md` and `platform/api-gateway-policy.md` still reference ecommerce paths. Do **not** fix in this task; flag in review for a separate doc-cleanup task.

---

# Definition of Done

- [ ] Integration flow implemented
- [ ] Contracts updated first if needed (none required — master-service contract is unchanged)
- [ ] Failure handling covered (401, 429, 503, 504, circuit open)
- [ ] Tests added (unit, integration, contract, security)
- [ ] Tests passing in CI
- [ ] Docker image builds
- [ ] Review notes flag the `platform/api-gateway-policy.md` public-routes debt and the auth-service gap
- [ ] Ready for review
