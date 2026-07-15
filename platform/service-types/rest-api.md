# Service Type: REST API

Normative requirements for any service whose `Service Type` is `rest-api`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

A `rest-api` service exposes synchronous HTTP endpoints to clients (browsers, mobile, other services). This is the default service type for backend services that respond to HTTP calls. Each project's `PROJECT.md` declares which services are of type `rest-api`.

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
- **Canonical: [`platform/versioning-policy.md`](../versioning-policy.md) § HTTP API Versioning.** That section fixes the mandatory `/api/` prefix, the `/api/v{n}/{resource}` form, and when the `v{n}` segment must be introduced. This section is a pointer — do not restate the rule here. (A restatement here is exactly how the two files came to contradict each other: this file asserted the `/api/` prefix was mandatory *per* `versioning-policy.md`, while that file's own bullet read as though the prefix was omitted. TASK-MONO-411.)
- Also follow `.claude/skills/cross-cutting/api-versioning/SKILL.md`

## Error Handling
- Use the project-wide error envelope defined in `platform/error-handling.md`
- Never leak stack traces or internal paths in responses

## Authentication and Authorization
- All endpoints except explicitly public ones require JWT bearer token validation
- Authorization decisions live at the application service layer, not the controller
- See `.claude/skills/backend/jwt-auth/SKILL.md`, `.claude/skills/backend/gateway-security/SKILL.md`

## Idempotency
- **Gated by the `transactional` trait. Canonical: [`rules/traits/transactional.md`](../../rules/traits/transactional.md) T1** — that file outranks this one (CLAUDE.md § Source of Truth Priority: `rules/traits/` is layer 4, `platform/` is layer 5), and [`platform/error-handling.md`](../error-handling.md) § Transactional Trait already registers `IDEMPOTENCY_KEY_REQUIRED` / `DUPLICATE_REQUEST` as trait-activated codes. This section is a pointer.
- **When the project declares `transactional`** in `PROJECT.md`: every state-changing public endpoint accepts and honors `Idempotency-Key` per T1 (key scope, storage, and the 24-hour minimum TTL are defined there).
- **When it does not**: this file imposes no idempotency requirement of its own. Note this does not license dropping the header — a `rest-api` service that forwards a mutating call into a `transactional` project's API still propagates the caller's `Idempotency-Key`, because that obligation comes from the downstream contract, not from this section.
- This section formerly stated an unconditional MUST for *all* `rest-api` services, contradicting the trait gate it sits below (TASK-MONO-411).

## Pagination
- All list endpoints MUST paginate via `PageQuery` / `PageResult`
- Unbounded list responses are forbidden
- See `.claude/skills/backend/pagination/SKILL.md`

## Observability
- Every endpoint emits request rate, error rate, and latency metrics (see `.claude/skills/cross-cutting/observability-setup/SKILL.md`)
- Trace context propagated via OTel headers across all outbound calls
- Structured JSON logs with `traceId`, `userId`, `requestId` MDC

---

# Allowed Patterns

- Synchronous HTTP request/response
- Asynchronous publishing of domain events via outbox (`.claude/skills/messaging/outbox-pattern/SKILL.md`)
- Subscribing to events as a secondary capability (document under Integration Rules)
- Caching reads via Redis (`.claude/skills/cross-cutting/caching/SKILL.md`)

---

# Forbidden Patterns

- Long-running synchronous endpoints (> 5 seconds) — use a job + status polling
- WebSocket or SSE on a `rest-api` service — promote to a dedicated streaming service
- Direct DB access from another service — use the public HTTP contract
- Bypassing the gateway for external traffic

---

# Testing Requirements

- Unit tests for domain / service-layer logic (the base of the `platform/testing-strategy.md` pyramid)
- Controller slice tests (`@WebMvcTest`) for every controller
- Contract tests against `specs/contracts/http/` for every public endpoint
- Integration tests with Testcontainers for end-to-end happy paths and key error cases
- See `platform/testing-strategy.md` (full five-level pyramid), `.claude/skills/testing/contract-test/SKILL.md`, `.claude/skills/testing/e2e-test/SKILL.md`

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
- [ ] Idempotency keys honored on mutating endpoints — **if the project declares the `transactional` trait** (§ Idempotency)
- [ ] Pagination on all list endpoints
- [ ] Metrics, logs, traces emitted
- [ ] Contract tests pass

---

# Change Rule

Changes to the mandatory requirements, allowed/forbidden patterns, or testing expectations for `rest-api` services must be documented in this file before applying to existing services. New constraints affecting deployed services require an ADR (`docs/adr/` for monorepo-wide impact or `projects/<project>/docs/adr/` for project-scoped impact) per [`architecture-decision-rule.md`](../architecture-decision-rule.md).
