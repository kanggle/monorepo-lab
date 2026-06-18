# TASK-BE-401 — order-service PII cascade on account deletion (ADR-MONO-037 P3-B)

- **Status:** ready
- **Service:** order-service (`projects/ecommerce-microservices-platform/apps/order-service/`)
- **ADR:** [ADR-MONO-037](../../../../docs/adr/ADR-MONO-037-ecommerce-account-lifecycle-projection.md) P3-B (the documented deferred follow-up)
- **Sibling implemented:** TASK-BE-388 M3 (user-service `AccountDeletedConsumer` → `anonymizeProfile`) — this task MIRRORS that pattern for the order store.
- **분석=Opus 4.8 / 구현 권장=Opus** (compliance-sensitive PII handling, cross-service event projection)

---

## Goal

Implement the deferred **P3-B** follow-up of ADR-MONO-037: have `order-service` react to the IAM
`account.deleted(anonymized=true)` lifecycle event by **anonymizing order-held PII** (the shipping-address
snapshot denormalized onto historical orders), closing the GDPR boundary that ADR-037 P3 explicitly
documented as deferred — the standing TASK-BE-258 consumer obligation for the order store.

Before this task, ecommerce met the TASK-BE-258 `anonymized=true` masking obligation **only for the
user-service profile store** (TASK-BE-388 M3). order-service held its own PII (recipient name, phone,
shipping address) on every order row and never masked it. This task wires the order-store half.

## Scope

1. **order-service consumes `account.deleted`** with `anonymized=true` → anonymize order-held PII fields
   (`ShippingAddress`: recipient, phone, zipCode, address1, address2) on **all historical orders** for that
   `accountId`/`userId`, while **PRESERVING** `orderId` / `userId` FK and all order business data (amounts,
   line items, status, payment/refund timestamps).
2. **Idempotent + fail-soft consumer** (never block the partition); monotonic — re-delivery safe. Reuse the
   order-service `EventDeduplicationChecker` (`processed_event` store already present) for the `eventId`
   dedup, mirroring the existing `UserWithdrawnEventConsumer`; the anonymize reaction itself is naturally
   idempotent (re-clearing already-tombstoned fields is a no-op).
3. **`anonymized=false` (grace-entry) → NO order-PII change in this slice.** Grace-entry order handling is
   already covered by the existing `user.user.withdrawn` reaction (`UserWithdrawnEventConsumer` →
   `cancelOrdersForWithdrawnUser`, which cancels active orders). This task adds **only** the terminal
   `anonymized=true` PII cascade; it does NOT duplicate the withdraw/cancel step.
4. **Contract:** add an ecommerce order-service subscription contract for `account.deleted` under
   `specs/contracts/`. Update the existing `account-lifecycle-subscriptions.md` scope-boundary section to
   record that the order-PII cascade is now IMPLEMENTED (no longer deferred).
5. **Spec:** update order-service `architecture.md` (Event consumption row) + the ADR-037 boundary note in
   the subscription contract — the order-PII cascade was "tracked, not hidden"; it is now closed.

## Constraints

- **Lifecycle PROJECTION only** — NO identity/authorization/role change; do NOT re-decide
  ADR-032/034/035/036/037. Consume the existing IAM `account.deleted` contract (schema v2).
- Stay inside **order-service** + the named spec/contract files. Do NOT modify user-service / iam / payment.
- `accountId` (event) **is** `order.userId` (= OIDC `sub`, ADR-037 P4) — verified: orders are keyed on the
  same subject the IAM account events carry; no translation layer. The order `userId` column is a `String`;
  the event `accountId` is a UUID → resolve by `accountId.toString()`.
- **Anonymize ALL historical orders, not just active ones** — GDPR masking is retention-wide; the existing
  `cancelOrdersForWithdrawnUser` only touches PENDING/CONFIRMED, which is correct for *cancellation* but
  insufficient for *PII masking*. A new tenant-agnostic, status-agnostic lookup is required (masking spans
  every tenant the subject ordered under; the order's immutable `tenant_id` is preserved across the update).
- Tests: unit tests for the order PII anonymizer (domain + application service) + consumer idempotency/
  fail-soft. Testcontainers `@Tag("integration")` ITs are BLOCKED on this host — write but do NOT run; rely
  on unit `:test` + `:compileTestJava`; CI runs ITs.
- Conventional commit `feat(ecommerce): ...`.

## Acceptance Criteria

- **AC-1** order-service has an `AccountDeletedConsumer` (`@KafkaListener(topics = "account.deleted",
  groupId = "order-service-account-sync")`, `@Profile("!standalone")`) that branches on the event's own
  `anonymized` flag: `true` → order-PII anonymize; `false` → no-op (grace handled by the `UserWithdrawn`
  reaction).
- **AC-2** The anonymize reaction clears all `ShippingAddress` PII fields on **every** order for the
  `accountId` (all statuses, all tenants), preserving `orderId`/`userId`/amounts/items/status/timestamps.
- **AC-3** Idempotent: `eventId` dedup via the existing `EventDeduplicationChecker`; re-applying the mask
  on already-tombstoned orders is a no-op (no exception). A subject with no orders is a no-op.
- **AC-4** Fail-soft: a null payload / missing `accountId` is logged + skipped (no throw); a malformed
  message routes to `account.deleted.dlq` via the existing `DefaultErrorHandler`
  (`JsonProcessingException` / `IllegalArgumentException` non-retryable). The lifecycle partition never
  blocks on a poison message.
- **AC-5** Contract `account-deleted-order-subscription.md` (or an addition to the existing subscription
  contract) documents the order-service consumer group, the `anonymized=true`-only handling, the
  preserved-FK boundary, and the SLA reference (TASK-BE-258 24h).
- **AC-6** `account-lifecycle-subscriptions.md` § scope boundary updated: order-PII cascade IMPLEMENTED.
- **AC-7** order-service `architecture.md` Event-consumption row lists the new `account.deleted`
  subscription.
- **AC-8** `./gradlew :ecommerce-microservices-platform:order-service:test` GREEN; new unit tests cover the
  anonymizer (domain + app service) + consumer routing/idempotency/fail-soft.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` (Event consumption)
- `projects/ecommerce-microservices-platform/specs/contracts/events/account-lifecycle-subscriptions.md`
  (the M4 subscription contract — scope-boundary section to update)
- `docs/adr/ADR-MONO-037-ecommerce-account-lifecycle-projection.md` (§2 P3/P6, §3.2, §3.3 optional follow-ups)

## Related Contracts

- IAM producer: `projects/iam-platform/specs/contracts/events/account-events.md` — `account.deleted`
  schema v2 (`accountId`/`tenantId`/`reasonCode`/`actorType`/`actorId`/`deletedAt`/`gracePeriodEndsAt`/
  `anonymized`) + § Consumer Obligations (TASK-BE-258). **No producer change** — order-service adds a
  consumer.
- ecommerce: `account-lifecycle-subscriptions.md` (extend); new
  `account-deleted-order-subscription.md` if a dedicated file is cleaner.

## Edge Cases

- **No orders for the subject** → no-op (idempotent/fail-soft log).
- **Already-anonymized orders** (re-delivery, or both `false`-then-`true` emissions arrive) → re-clearing
  tombstoned fields is a no-op; `eventId` dedup also short-circuits exact re-delivery.
- **`anonymized` null** → treat as grace-entry (no-op for order PII; the user-service consumer treats null
  as grace-entry too — symmetric).
- **Cross-tenant orders** — a subject may have ordered under multiple tenants; masking must reach all of
  them (tenant-agnostic lookup by globally-unique `user_id` cannot leak — it only mutates that subject's
  own rows; each order's immutable `tenant_id` is preserved on save).
- **ShippingAddress null** (defensive) → skip that order's address mask, no NPE.
- **Order business data must survive** — amounts, line items, status, payment/refund/timestamps unchanged;
  only the address snapshot PII is tombstoned.

## Failure Scenarios

- **F1 — partition stall on poison message.** Mitigated: fail-soft skip + DLQ routing
  (`account.deleted.dlq`), mirroring the user-service consumer and the existing order consumers.
- **F2 — undocumented GDPR gap.** This task CLOSES the F2 boundary ADR-037 P3 flagged: the order-PII
  cascade moves from "documented deferred" to "implemented." The contract + ADR boundary note are updated
  so deletion handling is not misrepresented either before (documented gap) or after (closed).
- **F3 — double-masking corrupts business data.** Mitigated: the anonymize step touches ONLY the address
  snapshot fields; amounts/items/status are never written. Idempotent re-application is provably safe.
- **F4 — FK integrity broken.** Mitigated: `orderId` and `userId` are preserved (masking is field-level on
  the embedded address, not row deletion), so audit/finance/settlement FKs survive.
