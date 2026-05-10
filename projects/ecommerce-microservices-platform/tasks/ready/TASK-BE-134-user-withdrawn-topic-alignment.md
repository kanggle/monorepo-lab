# Task ID

TASK-BE-134

# Title

`user.user.withdrawn` topic name alignment + user-events.md contract topic enumeration

# Status

ready

# Owner

backend

# Task Tags

- code
- event

---

# Goal

Fix the **silent event loss** in user-service: `KafkaUserProfileEventPublisher` publishes `UserWithdrawn` events to topic `user.user-withdrawn` (hyphen separator between `user` and `withdrawn`) while every consumer (`order-service` `UserWithdrawnEventConsumer`, `auth-service` `UserWithdrawnEventConsumer`) subscribes to `user.user.withdrawn` (dot separator). Production behavior: `UserWithdrawn` events are emitted but no service receives them — `order-service` does not cancel the user's pending orders, and `auth-service` does not revoke the user's refresh tokens.

After this task: `UserWithdrawn` events flow end-to-end (publisher → Kafka → both consumers), the topic name is canonical and matches the `<bounded-context>.<aggregate>.<event>` pattern used by every other ecommerce event topic (`order.order.placed`, `payment.payment.completed`, etc.), and `user-events.md` contract explicitly enumerates each topic name.

---

# Scope

## In Scope

- Change `apps/user-service/.../KafkaUserProfileEventPublisher.java` topic constant from `user.user-withdrawn` → `user.user.withdrawn` (align to consumer side, matches `<context>.<aggregate>.<event>` convention).
- Audit `KafkaUserProfileEventPublisher` for any other topic constants and verify against consumer subscriptions across the repo (notably `user.user-profile.updated` — likely also drift; reconcile in the same PR if low effort).
- Update `projects/ecommerce-microservices-platform/specs/contracts/events/user-events.md` to explicitly enumerate every published topic name in a table (`UserWithdrawn` → `user.user.withdrawn`, `UserProfileUpdated` → resolved canonical name).
- Add an integration test in `user-service` that publishes `UserWithdrawn` and a stub consumer that asserts receipt on the canonical topic.
- Cross-check `application.yml` of all 3 services (user-service, order-service, auth-service) for any `spring.kafka.consumer.topics` overrides that might pin the wrong name.

## Out of Scope

- Refactoring outbox pattern adoption for user-service (filed as TASK-BE-135 candidate from BE-133 finding 5).
- Changing the `UserWithdrawn` event payload schema.
- Replaying historical lost events (data-side recovery is a separate operations task; v1 portfolio scope = forward-only).
- auth-events.md DEPRECATED status cleanup (BE-133 finding 3 — separate task).

---

# Acceptance Criteria

- [ ] `KafkaUserProfileEventPublisher` topic constants match consumer subscriptions byte-for-byte.
- [ ] `user-events.md` contract has a "Topics" table enumerating each event's canonical topic name.
- [ ] New integration test (`user-service`) demonstrates: publish `UserWithdrawn` → consume via Kafka → assert payload integrity.
- [ ] No deprecation/transition shim — direct rename (no v1→v2 dual publish; cost not justified for portfolio scope).
- [ ] All affected services pass `./gradlew :apps:<service>:test :apps:<service>:integrationTest`.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/ecommerce-microservices-platform/PROJECT.md`
- `projects/ecommerce-microservices-platform/specs/services/user-service/architecture.md`
- `projects/ecommerce-microservices-platform/specs/services/user-service/dependencies.md` (TASK-BE-133)
- `projects/ecommerce-microservices-platform/specs/services/order-service/dependencies.md` (TASK-BE-133)
- `platform/event-driven-policy.md`
- `rules/traits/transactional.md` § T8 (idempotent consumer)

# Related Skills

- `.claude/skills/messaging/event-naming/SKILL.md` (if exists)
- `.claude/skills/messaging/outbox-pattern/SKILL.md`

---

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/user-events.md` (target — add Topics table)
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (cross-reference for naming pattern)
- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md` (cross-reference)

---

# Target Service

- user-service (publisher fix)
- order-service (no code change — consumer already correct, verify only)
- auth-service (no code change — consumer already correct, verify only)

---

# Architecture

Follow each service's `architecture.md`. user-service publishes via `KafkaUserProfileEventPublisher` (direct `KafkaTemplate`; outbox migration is BE-135's scope, not this task).

---

# Implementation Notes

- The fix is a one-line constant change in user-service. The bulk of the work is the integration test (proves the silent-loss bug is gone) and the contract update.
- Naming pattern verification: every other ecommerce event topic uses `<context>.<aggregate>.<event>` with dot separators. `user.user-withdrawn` (hyphen on the right side) is the lone outlier — fix the publisher, do NOT propagate the hyphen pattern to consumers.
- Verify `user.user-profile.updated` topic at the same time. If consumer-side is `user.user.profile.updated` (dot), fix publisher; if both already align on hyphen, leave it (cross-check actual production behavior).
- No backwards compatibility window required (events were silently lost — no consumer was listening to the broken topic, so there is nothing to drain).

---

# Edge Cases

- `application.yml` of one of the 3 services has the topic name overridden via `${SOME_TOPIC:default}` indirection — search for `kafka.topics.user-withdrawn` keys across all 3 services.
- A second publisher path exists somewhere (e.g., direct `KafkaTemplate.send(...)` call inline in a service class) that wasn't refactored to use `KafkaUserProfileEventPublisher` — grep `user.user-withdrawn` and `user.user.withdrawn` repo-wide before commit.
- Test fixtures or stub consumers in `src/test/` depend on the broken name — update them as part of this PR.

---

# Failure Scenarios

- Integration test discovers the consumer's `@KafkaListener` topic resolution is parameterized via `application.yml` and the wms-platform / fan-platform / scm-platform have copy-pasted the same key — verify cross-project (likely OK since this is ecommerce-only event, but grep to confirm).
- A separate event (`UserProfileUpdated`) has the inverse drift (publisher dot, consumer hyphen) — bundle in the same PR for symmetry.

---

# Test Requirements

- Unit test: topic constant in `KafkaUserProfileEventPublisher` matches the canonical name from `user-events.md`.
- Integration test (Testcontainers Kafka): publish `UserWithdrawn` → consumer receives → payload deserializes correctly.
- Integration test (cross-service): `user-service` triggers withdrawal → `order-service` cancels pending orders + `auth-service` revokes refresh tokens (E2E happy path through the corrected topic).

---

# Definition of Done

- [ ] Publisher topic constant fixed
- [ ] user-events.md Topics table added
- [ ] Integration test demonstrating end-to-end delivery added + passing
- [ ] No production code changes outside user-service publisher (consumers were already correct)
- [ ] `./gradlew :apps:user-service:check` + `:apps:order-service:check` + `:apps:auth-service:check` all pass
- [ ] Ready for review

---

# Provenance

Surfaced from TASK-BE-133 finding #1 (2026-05-11 ecommerce dependencies.md backfill audit). The user-service publisher emits to `user.user-withdrawn` (hyphen), every consumer (`order-service` `UserWithdrawnEventConsumer.java:24`, `auth-service` `UserWithdrawnEventConsumer.java:21`) subscribes to `user.user.withdrawn` (dot). Discovered during code-evidence audit; classified as production bug (silent event loss) for portfolio reviewer visibility.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (one-line code fix + contract enumeration + integration test; small surface).
