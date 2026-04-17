# Service Type: REST API

Normative requirements for any service whose `Service Type` is `rest-api`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

A `rest-api` service exposes synchronous HTTP endpoints to clients (browsers, mobile, other services). Examples in this monorepo: `auth-service`, `user-service`, `product-service`, `order-service`, `payment-service`, `promotion-service`, `review-service`.

---

# Mandatory Requirements

## Contract First
- Every endpoint MUST have a contract in `specs/contracts/http/<service>-api.md` before implementation
- Implementation that does not match the contract is forbidden
- Contract changes require updating the contract file first (per CLAUDE.md Contract Rule)

## Gateway Routing
- All external traffic MUST enter through `gateway-service`
- Direct external exposure of internal `rest-api` services is forbidden
- See `platform/api-gateway-policy.md`

## Versioning
- Use URI path versioning for public endpoints (`/v1/`, `/v2/`)
- Follow `platform/versioning-policy.md` and `cross-cutting/api-versioning.md`

## Error Handling
- Use the project-wide error envelope defined in `platform/error-handling.md`
- Never leak stack traces or internal paths in responses

## Authentication and Authorization
- All endpoints except explicitly public ones require JWT bearer token validation
- Authorization decisions live at the application service layer, not the controller
- See `backend/jwt-auth.md`, `backend/gateway-security.md`

## Idempotency
- All non-idempotent endpoints (POST that creates resources, PUT/PATCH that mutates state) MUST accept an `Idempotency-Key` header
- Repeated calls with the same key MUST return the same response
- TTL: 24 hours minimum

## Pagination
- All list endpoints MUST paginate via `PageQuery` / `PageResult`
- Unbounded list responses are forbidden
- See `backend/pagination.md`

## Observability
- Every endpoint emits request rate, error rate, and latency metrics (see `cross-cutting/observability-setup.md`)
- Trace context propagated via OTel headers across all outbound calls
- Structured JSON logs with `traceId`, `userId`, `requestId` MDC

---

# Allowed Patterns

- Synchronous HTTP request/response
- Asynchronous publishing of domain events via outbox (`messaging/outbox-pattern.md`)
- Subscribing to events as a secondary capability (document under Integration Rules)
- Caching reads via Redis (`cross-cutting/caching.md`)

---

# Forbidden Patterns

- Long-running synchronous endpoints (> 5 seconds) — use a job + status polling
- WebSocket or SSE on a `rest-api` service — promote to a dedicated streaming service
- Direct DB access from another service — use the public HTTP contract
- Bypassing the gateway for external traffic

---

# Testing Requirements

- Controller slice tests (`@WebMvcTest`) for every controller
- Contract tests against `specs/contracts/http/` for every public endpoint
- Integration tests with Testcontainers for end-to-end happy paths and key error cases
- See `testing/contract-test.md`, `testing/e2e-test.md`

---

# Default Skill Set

When implementing a `rest-api` service or feature:

`backend/springboot-api`, matched architecture skill, `backend/exception-handling`, `backend/validation`, `backend/dto-mapping`, `backend/transaction-handling`, `backend/pagination`, `backend/jwt-auth`, `cross-cutting/api-versioning`, `cross-cutting/observability-setup`, `backend/testing-backend`, `service-types/rest-api-setup`

---

# Acceptance for a New REST API Service

- [ ] `specs/contracts/http/<service>-api.md` exists and is reviewed
- [ ] `specs/services/<service>/architecture.md` declares `Service Type: rest-api`
- [ ] Gateway route configured in `gateway-service`
- [ ] Authentication and authorization wired
- [ ] Idempotency keys honored on mutating endpoints
- [ ] Pagination on all list endpoints
- [ ] Metrics, logs, traces emitted
- [ ] Contract tests pass
