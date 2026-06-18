# Event Contract — ecommerce subscriptions to IAM account lifecycle (← IAM `account.created` / `account.deleted`)

Implements **[ADR-MONO-037](../../../../docs/adr/ADR-MONO-037-ecommerce-account-lifecycle-projection.md)** (project the IAM account lifecycle into ecommerce) — the death/projection sibling of ADR-036's birth/provisioning.

Post-IAM ([TASK-BE-132](../../../../README.md), ecommerce `auth-service` decommissioned), ecommerce no longer has an in-tree producer of signup/withdrawal events. `user-service` and `notification-service` subscribe to the **IAM account-service** lifecycle events instead of the retired `auth.user.signed-up` (see [`auth-events.md`](auth-events.md) DEPRECATED).

Authoritative producer schemas: **IAM** [`account-events.md`](../../../iam-platform/specs/contracts/events/account-events.md) (`account.created` / `account.deleted`, schema v2). This subscription adds ecommerce consumers to those events — **no producer payload change**. The GDPR handling here is the realization of IAM's standing consumer obligation ([`account-events.md` § Consumer Obligations, TASK-BE-258](../../../iam-platform/specs/contracts/events/account-events.md) + [`consumer-integration-guide.md` § GDPR downstream](../../../iam-platform/specs/features/consumer-integration-guide.md)).

---

## Consumer Groups

| Service | Group | Subscribes |
|---|---|---|
| `user-service` | `user-service` | `account.created`, `account.deleted` |
| `notification-service` | `notification-service` | `account.created` |
| `order-service` | `order-service-account-sync` | `account.deleted` (`anonymized=true` only — order-PII cascade, ADR-MONO-037 P3-B / TASK-BE-401) |

## Subscribed Topics

| Topic | Producer event | Service / handler | Effect |
|---|---|---|---|
| `account.created` | IAM `account.created` (payload: `accountId`, `tenantId`, **`emailHash`**, `status`, `locale`, `createdAt`) | `user-service` `AccountCreatedConsumer` → `AccountCreatedHandler` | **Minimal profile.** Create `user_profiles` row keyed on `accountId` (= `profile.userId`), `status=ACTIVE`, tenant from payload `tenantId`. **`email`/`name` left NULL** — the event is emailHash-only (no raw PII); they are sourced from the OIDC id_token (`profile`/`email` scopes) at first login / via profile-update. Idempotent on `user_id` (`existsByUserId`). |
| `account.created` | (same) | `notification-service` `AccountCreatedEventConsumer` | **Onboarding WELCOME.** Sends the `WELCOME` notification keyed on `accountId`, **with no PII personalization** (the event carries no name/email). Dedup on the event id. |
| `account.deleted` (`anonymized=false`, grace entry) | IAM `account.deleted` (payload incl. `reasonCode`, `gracePeriodEndsAt`, `anonymized`) | `user-service` `AccountDeletedConsumer` → `UserProfileService.withdrawProfile` | **Phase 1 — logical/status delete.** Resolve profile by `accountId` → `status=WITHDRAWN` → publish ecommerce `UserWithdrawn` (`user.user.withdrawn`, consumed by order-service). Idempotent + fail-soft. |
| `account.deleted` (`anonymized=true`, post-grace) | (same) | `user-service` `AccountDeletedConsumer` → `UserProfileService.anonymizeProfile` | **Phase 2 — PII anonymization** (the TASK-BE-258 obligation). Clear `email`/`name`/`nickname`/`phone`/`profile_image_url`; preserve `user_id` (FK/audit/order integrity). Idempotent + fail-soft. |
| `account.deleted` (`anonymized=true`, post-grace) | (same) | `order-service` `AccountDeletedConsumer` → `OrderPiiAnonymizationService.anonymizeOrdersForAccount` | **Order-PII cascade (ADR-037 P3-B / TASK-BE-401).** Tombstone the shipping-address snapshot (`recipient`/`phone`/`zip_code`/`address1` → `[deleted]`, nullable `address2` → NULL) on **every** order for the subject (all statuses, all tenants — `findAllByUserIdAcrossTenants`); preserve `order_id`/`user_id` FK + amounts/items/status/timestamps. `anonymized=false` (grace) → **no order-PII action** (cancellation is the `UserWithdrawn` reaction's job). `eventId` dedup via `EventDeduplicationChecker`; idempotent + fail-soft. |

> **Two-phase, flag-driven.** The consumer branches on the event's own `anonymized` flag — it does **not** self-schedule on `gracePeriodEndsAt` (the IAM producer re-emits at grace end). This mirrors the IAM `consumer-integration-guide` § GDPR downstream reference consumer exactly.

## Scope boundary — order-PII cascade (IMPLEMENTED, ADR-MONO-037 P3-B / TASK-BE-401)

**Status: CLOSED** (was DEFERRED in M4). The order-PII cascade ADR-037 P3 documented as a tracked follow-up is now **implemented** by `order-service`'s own `account.deleted(anonymized=true)` consumer (group `order-service-account-sync`):

- **order-service-held PII** — the shipping-address snapshot (recipient name, phone, postal/zip code, street address) denormalized onto every order — is anonymized when the subject's account reaches the terminal `anonymized=true` phase. The identifying fields **and** the NOT-NULL `zip_code` (`orders.zip_code VARCHAR(20) NOT NULL`) are overwritten with the `[deleted]` tombstone (so every NOT-NULL column stays satisfied on the `saveAll` flush — a `NULL` would violate the constraint, roll the listener back, and route the valid event to the DLQ with the PII left unmasked); only the nullable `address2` is cleared to `NULL`.
- **FK + business data preserved** — `order_id` and `user_id` survive (masking is field-level on the embedded address, not row deletion), so audit / finance / settlement FKs are intact; amounts, line items, status, and payment/refund timestamps are untouched.
- **Retention-wide** — masking reaches **all historical orders** in any status, across every tenant the subject ordered under (the existing `UserWithdrawn` reaction only cancels *active* orders, which is correct for cancellation but insufficient for GDPR PII masking — hence a separate status-agnostic, tenant-agnostic cascade).
- **Grace-entry (`anonymized=false`) is a no-op for order PII** — active-order cancellation is already driven by the user-service `UserWithdrawn` event (`UserWithdrawnEventConsumer`); the order PII consumer does not duplicate it.
- **Outbox is a transient publish buffer, not a retention store** — the order-service outbox may transiently hold an un-masked shipping-address snapshot inside an event payload between commit and publish, but rows are purged once published (and downstream events carry only what was committed at publish time). It is therefore out of scope for the at-rest masking obligation here; only the durable `orders` row is the order-service PII store this cascade covers.

The two durable ecommerce PII stores (user-service profile + order-service `orders` row) now meet the TASK-BE-258 `anonymized=true` masking obligation; the transient outbox buffer is purged post-publish (see the note above). The GDPR deletion-handling boundary ADR-037 P3 flagged as documented-but-open is closed.

## id mapping (ADR-MONO-037 P4)

`account.created.accountId` / `account.deleted.accountId` **is** the ecommerce `profile.userId` (= OIDC `sub`). Post-IAM there is no separate ecommerce-minted user id — the IAM `accountId` is the canonical subject. (Implementation verified this equals the `sub` used for profile lookup; no translation layer.)

## Envelope & tenant derivation

IAM account events use the standard envelope (`eventId` / `eventType` / `occurredAt` / `source` / `schemaVersion`, camelCase) and carry **`tenantId` in the payload**. Consumers bind the tenant from `payload.tenantId` (falling back to the envelope, then the default tenant — M5/D8 net-zero). Deserialization is tolerant (camelCase primary + snake_case alias + ignore-unknown) for additive forward-compatibility.

## Consumer Rules

- **Fail-soft** (ADR-MONO-037 P5): a null payload / missing `accountId` is logged and skipped; a malformed message routes to the standard `<topic>.dlq`. A poison message never blocks the lifecycle partition.
- **Idempotent without a dedup store**: the reactions are naturally idempotent over the monotonic `ACTIVE → WITHDRAWN → anonymized` transition (create skips on `existsByUserId`; `withdrawProfile` no-ops on missing / already-WITHDRAWN; `anonymizeProfile` no-ops on missing and re-clears already-null fields). Kafka at-least-once re-delivery and the two `account.deleted` emissions (false then true) are therefore safe.
- Do **not** call IAM HTTP APIs to backfill missing event data (consumer rule). Onboarding PII arrives via the OIDC token, not a synchronous fetch.
- Fields not listed in the IAM producer contract must not be relied upon.
