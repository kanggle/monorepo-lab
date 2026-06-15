# TASK-BE-385 — Ecommerce spec structural consistency: missing per-service files + section gaps

**Status:** done
**Type:** TASK-BE
**Analysis model:** Opus 4.8
**Recommended impl model:** Sonnet

---

## Goal

Bring the ecommerce-microservices-platform service specs into full structural consistency:
every live service must have `overview.md` + `architecture.md` + `dependencies.md` + `observability.md`;
every `architecture.md` Identity-table claim must agree with a dedicated section in the same file.

---

## Scope

**In (changes in this task):**
- CREATE `specs/services/settlement-service/overview.md`
- CREATE `specs/services/settlement-service/dependencies.md`
- CREATE `specs/services/settlement-service/observability.md`
- CREATE `specs/services/web-store/dependencies.md`
- CREATE `specs/services/web-store/observability.md`
- EDIT `specs/services/product-service/architecture.md` — add `## Outbox` section
- EDIT `specs/services/user-service/architecture.md` — add `## Outbox` section; normalize Multi-Tenancy heading; add SoT pointer; annotate auth-service decommission
- EDIT `specs/services/promotion-service/architecture.md` — normalize Multi-Tenancy heading; add SoT pointer
- EDIT `specs/services/search-service/architecture.md` — add `## Events` section (consumed topics + consumer-group id)
- EDIT `specs/services/payment-service/architecture.md` — rename `## Event Publication` → `## Outbox`
- EDIT `specs/services/batch-worker/architecture.md` — reconcile Identity "Event publication" row with Published Interfaces (no contracts exist; row changed to "none (forward-declared; contracts TBD)")

**Out (do not touch):**
- `PROJECT.md`
- `specs/services/notification-service/architecture.md` (sibling task)
- `specs/services/settlement-service/architecture.md` (sibling task)
- `specs/services/web-store/overview.md` (sibling task)
- `specs/features/multi-tenancy-and-marketplace.md` (sibling task)
- All `apps/` code (read-only for verification)

---

## Acceptance Criteria

1. Every live service (`order-service`, `payment-service`, `product-service`, `promotion-service`, `review-service`, `search-service`, `settlement-service`, `shipping-service`, `user-service`, `batch-worker`, `gateway-service`, `web-store`) has all four standard spec files: `overview.md`, `architecture.md`, `dependencies.md`, `observability.md`.
2. **Net-zero meaning**: all new/edited files describe already-implemented behaviour; no forward-declared feature is presented as live.
3. Every `architecture.md` Identity table "Event publication" or "Outbox" claim is backed by a corresponding `## Outbox` or `## Events` section in the same file.
4. `## Outbox` sections accurately reflect the actual implementation pattern: services using `libs/java-messaging` say so; services using direct Kafka publish (product-service, user-service) are documented as direct publish with a forward-declared note.
5. All Multi-Tenancy headings in ecommerce service architecture files use the canonical form `## Multi-Tenancy & Marketplace (ADR-MONO-030)` with the SoT pointer on the first body line.
6. `batch-worker/architecture.md` Identity row and `## Published Interfaces` section agree: no Kafka events in v1, forward-declared with contracts TBD.
7. `search-service/architecture.md` `## Events` section lists all five consumed topics and their consumer group id (`search-service`), matching `@KafkaListener` annotations in `apps/search-service`.
8. `user-service/architecture.md` references to auth-service as the current credential owner are annotated with "(IAM; auth-service decommissioned TASK-BE-132)".

---

## Related Specs

- `specs/services/settlement-service/architecture.md` — source of truth for settlement-service implementation facts
- `specs/services/web-store/architecture.md` — source of truth for web-store implementation facts
- `specs/features/multi-tenancy-and-marketplace.md` — canonical SoT for the multi-tenancy model
- `platform/architecture-decision-rule.md`
- `platform/service-types/event-consumer.md`, `rest-api.md`, `frontend-app.md`, `batch-job.md`

---

## Related Contracts

- `specs/contracts/events/order-events.md`
- `specs/contracts/events/payment-events.md`
- `specs/contracts/events/product-events.md`
- `specs/contracts/events/user-events.md`
- `specs/contracts/events/settlement-subscriptions.md`
- `specs/contracts/http/settlement-api.md`

---

## Edge Cases

- `product-service` and `user-service` do NOT use `libs/java-messaging` (verified: `java-messaging` absent from both `build.gradle`). Their `## Outbox` sections must document direct Kafka publish and note the forward-declared outbox improvement — do not misrepresent them as using the transactional outbox pattern.
- `settlement-service` has NO outbox in v1 (terminal consumer); its `## Outbox` section already documents this. The three new files must not contradict the existing `architecture.md`.
- `web-store` observability: no RUM agent is wired in v1; the `observability.md` must not claim more than what is implemented.
- `batch-worker` reconciliation: changing the Identity row from "Kafka (batch completion events)" to "none (forward-declared; contracts TBD)" is a correction of a forward-declaration that was presented as live — this is a net-zero fix, not a scope reduction.
- Multi-Tenancy heading normalization for `user-service` and `promotion-service`: the original task ID in the heading (`TASK-BE-367`, `TASK-BE-368`) must move into the body, not be deleted.

---

## Failure Scenarios

- **F1 — missing service file after task**: if any of the 12 live services is still missing `overview.md`, `dependencies.md`, or `observability.md`, the acceptance criteria fail.
- **F2 — Identity/section mismatch survives edit**: if the Identity table claim ("Kafka via outbox") still has no corresponding `## Outbox` section in the same file, the consistency gap is unresolved.
- **F3 — forward-declared content presented as live**: if the settlement-service `observability.md` or `overview.md` describes payout/disbursement (forward-declared) as implemented, the net-zero constraint is violated.
- **F4 — incorrect publisher pattern documented**: if product-service or user-service `## Outbox` sections claim `libs/java-messaging` is used when it is not, implementation tasks will import the wrong library.
- **F5 — search-service Events section topic count mismatch**: if the `## Events` section lists fewer than the 5 topics confirmed by `@KafkaListener` in `apps/search-service`, the spec is incomplete.
