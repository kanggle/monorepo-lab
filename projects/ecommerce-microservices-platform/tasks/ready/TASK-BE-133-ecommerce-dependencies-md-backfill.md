# Task ID

TASK-BE-133

# Title

ecommerce 7 service dependencies.md spec backfill

# Status

ready

# Owner

backend

# Task Tags

- spec

---

# Goal

7 of the 12 ecommerce backend services lack a `dependencies.md` spec file (notification, order, payment, promotion, review, shipping, user). The pattern from `batch-worker/dependencies.md` (and now also `gateway-service/dependencies.md` per PR #326) defines the canonical structure: Service / Allowed Direct Dependencies / Allowed Service Interactions / Consumes From / Publishes To / Forbidden Dependencies / Notes.

After this task: every ecommerce service's dependency surface is explicit in spec, and library / cross-service / database boundaries are documented uniformly.

---

# Scope

## In Scope

- Author `dependencies.md` for each of: notification-service, order-service, payment-service, promotion-service, review-service, shipping-service, user-service.
- Use `projects/ecommerce-microservices-platform/specs/services/batch-worker/dependencies.md` as the canonical structure reference.
- Each `dependencies.md` should be grounded in production code evidence:
  - `apps/<service>/build.gradle` — direct dependencies
  - `apps/<service>/src/main/java/.../client/` or `infrastructure/` — outbound HTTP/Kafka calls
  - `apps/<service>/src/main/java/.../listener/` or `consumer/` — Kafka subscriptions
  - `apps/<service>/.../@Entity` or migration files — database ownership
- Group services into logical batches if helpful (e.g., 1 PR per 2-3 services), or single PR for all 7 — case-by-case judgment.

## Out of Scope

- Production code changes.
- Library policy changes (already in `platform/shared-library-policy.md`).
- Frontend services (web-store, admin-dashboard).
- Updating existing `dependencies.md` files unless drift surfaces during the audit.

---

# Acceptance Criteria

- [ ] 7 new `dependencies.md` files exist (one per listed service).
- [ ] Each file follows the canonical 7-section structure (Service / Allowed Direct Dependencies / ... / Notes).
- [ ] Each file is grounded in production code evidence (no speculative dependencies).
- [ ] Cross-references to `platform/shared-library-policy.md` and any service contracts (`specs/contracts/http/<service>-api.md`).
- [ ] No production code changes.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/ecommerce-microservices-platform/specs/services/batch-worker/dependencies.md` (canonical reference)
- `projects/ecommerce-microservices-platform/specs/services/gateway-service/dependencies.md` (post-PR #326 reference for cross-project pattern)
- `platform/shared-library-policy.md`
- `platform/dependency-rules.md`

---

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/<service>-api.md` (per service)
- `projects/ecommerce-microservices-platform/specs/contracts/events/<event-bus>.md` (per service that publishes events)

---

# Target Service

- ecommerce: notification / order / payment / promotion / review / shipping / user (7 services, spec only)

---

# Implementation Notes

- For services that publish events (order, payment, review, shipping, etc.), check `OutboxPollingScheduler` extensions and Kafka topic configuration in `application.yml`.
- For services that consume events (notification listens for many topics), enumerate sources from `@KafkaListener` annotations.
- For database ownership: follow Flyway migration files (`db/migration/V*.sql`) to enumerate owned tables.

---

# Edge Cases

- Service has zero outbound calls (e.g., a pure read service) → still author `dependencies.md` with explicit "no outbound HTTP" note.
- Cross-cutting concerns like `redis` for rate-limit / cache → document under "Allowed Direct Dependencies".

---

# Failure Scenarios

- Code-evidence audit reveals an undeclared cross-service call (e.g., order-service directly calling user-service via HTTP) → record as a finding in PR body and file separately as a boundary-violation cleanup task.

---

# Test Requirements

- N/A (spec-only).

---

# Definition of Done

- [ ] 7 `dependencies.md` files authored
- [ ] No production code changes
- [ ] Cross-references verified
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-spec all` (2026-05-11) audit Finding [Ecommerce 3]. Skipped from PR #326 because /refactor-spec scope does not author new spec files.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (audit + 7 routine docs files; can be 1 PR or 2-3 split PRs).
