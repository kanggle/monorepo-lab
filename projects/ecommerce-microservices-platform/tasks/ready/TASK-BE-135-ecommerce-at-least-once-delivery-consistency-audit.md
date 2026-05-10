# Task ID

TASK-BE-135

# Title

ecommerce at-least-once delivery consistency audit — outbox vs direct-publish gap

# Status

ready

# Owner

backend

# Task Tags

- spec
- adr

---

# Goal

ecommerce-microservices-platform has **inconsistent at-least-once delivery semantics** across services per TASK-BE-133 audit (PR #337):

| Service | Outbox? | Delivery semantic |
|---|---|---|
| order-service | ✅ libs/java-messaging outbox | at-least-once |
| promotion-service | ✅ outbox | at-least-once |
| review-service | ✅ outbox | at-least-once |
| shipping-service | ✅ outbox | at-least-once |
| user-service | ❌ KafkaTemplate direct (publisher uses `@TransactionalEventListener AFTER_COMMIT`) | best-effort post-commit |
| payment-service | ❌ KafkaTemplate direct | best-effort |
| notification-service | ❌ Spring Mail direct (no Kafka publish) | best-effort SMTP |

Pre-PR-#337 the gap was undocumented. After this audit task: the gap is either (a) closed by migrating direct-publishers to outbox, or (b) **explicitly accepted** with documented rationale + ADR.

After this task: ecommerce-platform delivery semantics are decided system-wide (no per-service ambiguity), recorded in an ADR.

---

# Scope

## In Scope

- For each of the 3 direct-publish services (user / payment / notification), enumerate **what events / messages it emits** and **what consumer would silently lose** on a producer crash between commit and Kafka send (the at-least-once gap):
  - **user-service**: `user.user.profile-updated`, `user.user.withdrawn` (post-BE-134 canonical names).
  - **payment-service**: `payment.payment.completed`, `payment.payment.refunded` (consumed by order-service for order-state transitions + by promotion-service).
  - **notification-service**: SMTP delivery (no downstream consumer; user-visible side effect).
- Decision per service:
  - **Migrate to outbox** (Scenario A) — file impl follow-up task with schema migration + publisher refactor.
  - **Accept best-effort + document rationale** (Scenario B) — write ADR-ECOM-### explaining why this service does NOT need at-least-once.
- Cross-service consistency check: are any 2 services in a saga where one is outbox and the other is direct-publish? (e.g., order-service outbox publishes → payment-service direct-publish responds — saga is at-least-once on first hop, best-effort on second).
- Author ADR (`projects/ecommerce-microservices-platform/docs/adr/ADR-ECOM-###-at-least-once-delivery-policy.md`):
  - Status: PROPOSED → ACCEPTED on PR merge
  - Context: per-service inconsistency surfaced by BE-133 audit
  - Decision: Scenario A list (services migrating) + Scenario B list (services accepting best-effort)
  - Consequences: per-service outbox migration tasks (links) + portfolio narrative update
- Update each affected service's `dependencies.md` § Notes to reference the ADR decision.

## Out of Scope

- Implementing the outbox migration (Scenario A items become impl follow-up tasks).
- Schema migrations (impl phase).
- Cross-project consistency (wms / scm / fan / GAP) — separate audit if needed; this is ecommerce-only.

---

# Acceptance Criteria

- [ ] Each direct-publish service has a documented decision (Scenario A or B).
- [ ] ADR-ECOM-### authored under `docs/adr/` with status PROPOSED.
- [ ] If Scenario A (any): impl follow-up task filed per service.
- [ ] If Scenario B (any): the rationale references the consumer-side impact (or its absence — e.g., notification-service has no downstream consumer so silent loss only manifests as "user did not get email").
- [ ] dependencies.md § Notes updated for each affected service to point at the ADR.
- [ ] No production code changes in this task.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/ecommerce-microservices-platform/PROJECT.md`
- `projects/ecommerce-microservices-platform/specs/services/{user,payment,notification}-service/architecture.md`
- `projects/ecommerce-microservices-platform/specs/services/{user,payment,notification}-service/dependencies.md` (BE-133, will gain Notes update)
- `platform/event-driven-policy.md`
- `rules/traits/transactional.md` § T2 (atomic state-change + outbox) / T3 (outbox table + polling)
- `projects/scm-platform/docs/adr/ADR-MONO-004-libs-java-messaging.md` (libs reference for the outbox pattern adopted by sibling services)

# Related Skills

- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/architecture/adr-authoring/SKILL.md` (if exists)

---

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/user-events.md` (post-BE-134)
- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md`

---

# Target Service

- user-service / payment-service / notification-service (audit + ADR; no production code change in this task)

---

# Implementation Notes

- For payment-service `payment.payment.completed`: order-service consumes this to transition order state (CREATED → PAID). A silent loss means the order stays in CREATED forever despite PG capture success — **high impact**, Scenario A likely.
- For payment-service `payment.payment.refunded`: order-service consumes for refund state. Same impact pattern.
- For user-service `user.user.withdrawn`: order-service cancels orders + auth-service revokes tokens. Silent loss means orphaned orders + stale auth state — **high impact**, Scenario A likely.
- For user-service `user.user.profile-updated`: no consumer in v1 (BE-134 contract). Scenario B viable.
- For notification-service: no downstream consumer. Silent loss = user did not receive notification email. Likely Scenario B (acceptable for portfolio v1) but document with retry/observability fallback expectation.

---

# Edge Cases

- A service uses `@TransactionalEventListener(AFTER_COMMIT)` (e.g., user-service) which is *almost* outbox — same `Tx commit → publish` ordering, but loses on producer crash between commit and `kafkaTemplate.send` flush. Document this nuance.
- payment-service might have manual outbox-style code that doesn't use libs/java-messaging — verify before classifying.

---

# Failure Scenarios

- Audit reveals a consumer assumes at-least-once but the producer is best-effort — saga consistency violation. Promote that pair to Scenario A regardless of cost (consistency > cost).

---

# Test Requirements

- N/A (audit + ADR only). Scenario A impl tasks each carry their own test requirements (ADR + integration tests proving outbox Tx atomicity).

---

# Definition of Done

- [ ] ADR authored
- [ ] Per-service decision documented
- [ ] dependencies.md Notes updated
- [ ] Impl follow-up tasks filed for Scenario A services
- [ ] Ready for review

---

# Provenance

Surfaced from TASK-BE-133 finding #5 (2026-05-11 ecommerce dependencies.md backfill, PR #337). Filed as separate task because the consistency decision is portfolio-wide and warrants ADR authoring beyond BE-133's per-service dependency cataloguing scope.

분석=Opus 4.7 / 구현 권장=Opus (cross-service ADR + decision + per-service note updates; complex coordination).
