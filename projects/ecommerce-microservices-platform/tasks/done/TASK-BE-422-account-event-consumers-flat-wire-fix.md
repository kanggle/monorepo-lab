# TASK-BE-422 — Fix nested-DTO deserialization defect in ecommerce account.* consumers (flat wire)

**Status:** done
**Type:** bug fix (production-inert consumers — GDPR-relevant)
**Severity:** HIGH — shipped account-lifecycle projections are silently inert in production.
**Discovered:** during TASK-BE-421 review (2026-06-20).

## Goal

Every ecommerce consumer of IAM `account.created` / `account.deleted` models a **nested
`payload` envelope** DTO, but the IAM producer emits a **FLAT** payload (fields at the JSON
root, no `payload` wrapper, no `eventId`/`eventType`/`source`/`schemaVersion` envelope). Against a
real IAM message these consumers deserialize `payload` to `null` and hit their null-payload guard →
**silently no-op**. The account-lifecycle projection (profile create, profile withdraw, GDPR
anonymize, order-PII cascade) is therefore **inert in production**. The unit tests pass only because
they feed **fabricated nested JSON** that does not match the wire. Reconcile every consumer DTO to
the flat shape and add real-wire deserialization tests.

## Evidence (verified 2026-06-20)

- **Producer is flat**: `AccountEventPublisher.save()` → `BaseEventPublisher.saveEvent(... event.payload())`
  serializes the payload `Map` directly (`saveEvent` javadoc: "serializes payload directly, no
  envelope"). `AccountEventFactory.createdEvent/deletedEvent/statusChangedEvent` build a flat
  `Map<String,Object>` (accountId/tenantId/... at top level). `account-service` has NO
  `writeEvent`/`AbstractOutboxPublisher`/`EventEnvelope` producer.
- **Relay is verbatim**: `OutboxPublisher.publishPendingEvents` sends `entry.getPayload()` as-is.
- **Authoritative contract is flat**: `iam-platform/specs/contracts/events/account-events.md`
  §§ account.created / account.deleted / account.status.changed document top-level payloads.
- **Working reference is flat**: the IAM-internal `security-service` `AccountDeletedAnonymizedConsumer`
  reads `root.path("accountId")` (root fallback), i.e. treats the wire as flat.
- **TASK-BE-421** already fixed `account.status.changed` → `product-service` with a flat DTO +
  real-wire test; `account-lifecycle-subscriptions.md` § Envelope now documents the flat truth and
  flags this defect.

## Scope (affected consumers — all nested → flat)

| Service | DTO | Consumer | Effect when inert |
|---|---|---|---|
| user-service | `AccountCreatedEvent` | `AccountCreatedConsumer` → `AccountCreatedHandler` | no `user_profiles` row on signup |
| user-service | `AccountDeletedEvent` | `AccountDeletedConsumer` → `UserProfileService.withdraw/anonymizeProfile` | no profile withdraw / **no GDPR PII anonymize** |
| order-service | `AccountDeletedEvent` | `AccountDeletedConsumer` → `OrderPiiAnonymizationService` | **no order-PII anonymize cascade** |
| notification-service | `AccountCreatedEvent` | `AccountCreatedConsumer` | no welcome/onboarding reaction |

For each: convert the DTO to a flat record (top-level fields, `@JsonIgnoreProperties(ignoreUnknown=true)`,
camelCase + snake_case `@JsonAlias`), update the consumer's field access (`event.accountId()` etc.,
drop `event.payload()`), and **replace the fabricated nested test JSON with the exact flat shape
from `account-events.md`** (incl. the dedicated `AccountLifecycleEventDeserializationTest` in
user-service). Add a real-wire `onMessage(flatJson)` assertion per consumer.

## Acceptance Criteria

- [ ] **AC-1** — Each affected DTO is a flat record matching `account-events.md`; no nested `payload`.
- [ ] **AC-2** — Each consumer reads fields from the root; the null-guard keys off `accountId` (not `payload`).
- [ ] **AC-3** — Each consumer has a real-wire deserialization test using the EXACT flat JSON from `account-events.md` (the test that would have caught this). Fabricated nested fixtures removed.
- [ ] **AC-4** — `eventId`-based dedup (order-service) still works (eventId is absent from the flat account.deleted payload — confirm the dedup key source; if it relied on a non-existent envelope `eventId`, redesign the dedup key, e.g. `accountId` + phase, or accept at-least-once idempotency).
- [ ] **AC-5** — All affected services' builds GREEN; CI Linux runs the @Tag("integration") IT.

## Related Specs / Contracts

- `iam-platform/specs/contracts/events/account-events.md` (authoritative flat producer schema)
- `ecommerce-microservices-platform/specs/contracts/events/account-lifecycle-subscriptions.md` (§ Envelope — defect flagged)
- ADR-MONO-037 (account lifecycle projection)

## Edge Cases / Failure Scenarios

- **AC-4 is the trap**: the flat account.deleted payload has NO `eventId` (only `account.locked` carries one). Any consumer using `eventId` for dedup (order-service `EventDeduplicationChecker`) must be re-keyed or rely on idempotent reactions. Verify before shipping.
- snake_case forward-compat retained via `@JsonAlias`.
- Confirm against a captured real `account.created`/`account.deleted` message (federation e2e) before closing — do not trust unit fixtures (that is exactly what masked the bug).
