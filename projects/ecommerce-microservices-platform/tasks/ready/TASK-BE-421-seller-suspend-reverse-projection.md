# TASK-BE-421 — Reverse `account.status.changed → LOCKED` → seller-SUSPENDED projection (ADR-MONO-042 D4-C)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (cross-service event projection + idempotent/race-tolerant lifecycle reaction)

> Realizes the **D4-C** reverse leg deferred by [TASK-BE-402](../done/TASK-BE-402-seller-onboarding-iam-provisioning.md) (ADR-MONO-042). The forward direction (operator suspend → IAM lock) already shipped (BE-402, `product-to-account.md §3`). The IAM producer of `account.status.changed` is LIVE and verified (account-service emits it via outbox on every status change — [`iam-platform/specs/contracts/events/account-events.md`](../../../iam-platform/specs/contracts/events/account-events.md)). No producer change.

---

## Goal

Close the seller lifecycle hole: a fraud/admin-locked seller currently stays `ACTIVE` in the marketplace. Add the REVERSE projection so product-service consumes IAM `account.status.changed` and transitions the matching `Seller` aggregate to `SUSPENDED` when `currentStatus == "LOCKED"`. The projection must NOT call IAM back (the account is already locked — that would loop with the forward leg).

## Scope

**Stays within ecommerce product-service + the two named contract files.** Does NOT modify account-service (it CONSUMES an existing live event). No Flyway migration (the `account_id` column exists since V15).

- **Contracts first.** `specs/contracts/events/account-lifecycle-subscriptions.md` — add the `product-service` consumer (group `product-service-iam`) subscribing to `account.status.changed`; document trigger (LOCKED only), idempotency, fail-soft, no-loop-back, CLOSED-race tolerance, and the shared-error-handler/DLQ decision. `specs/contracts/http/internal/product-to-account.md` — flip the deferred "Reverse account.status.changed → seller-SUSPENDED projection (ADR-042 D4-C)" line to IMPLEMENTED (TASK-BE-421).
- **Inbound DTO.** `infrastructure/event/AccountStatusChangedEvent.java` — `@JsonIgnoreProperties(ignoreUnknown=true)`, mirrors the user-service `AccountDeletedEvent` envelope shape exactly (flat top-level `eventId`/`eventType`/`occurredAt`/`source`/`tenantId` + nested `payload`; camelCase primary + snake_case `@JsonAlias`).
- **Repository extension.** Add `SellerRepository.findByAccountId(String)` (domain port), `SellerJpaRepository.findByTenantIdAndAccountId(...)` (tenant-scoped JPQL), implement in `SellerRepositoryImpl` (tenant-scoped via `TenantContext`), and a delegating `findByAccountId` on `SellerLifecyclePersistence`.
- **Application use case.** `RegisterSellerService.suspendByLockedAccount(accountId)` — find-by-account → `Seller.suspend()` → persist; NO IAM call. Returns whether a transition occurred; lets `IllegalStateException` (CLOSED) propagate for the consumer to tolerate.
- **Consumer.** `infrastructure/event/AccountStatusChangedSellerConsumer.java` — `@KafkaListener(topics="account.status.changed", groupId="product-service-iam")`, `@Profile("!standalone")`, package-private `handle()`. LOCKED-only; `TenantContext` set/clear in try/finally; fail-soft guards; CLOSED race caught (no rethrow); forward-loop-back / already-SUSPENDED logged DEBUG.
- **Error handler / DLQ.** Reuse the existing single shared `CommonErrorHandler` bean (`WmsReconciliationConfig.wmsReconciliationErrorHandler`) auto-wired to the default container factory — it already routes `<topic>.dlq` and marks `JsonProcessingException`/`IllegalArgumentException` not-retryable. A second `CommonErrorHandler` bean is intentionally NOT added (it would defeat Boot single-bean auto-detection and un-wire DLQ for all listeners).

## Acceptance Criteria

- **AC-1 (LOCKED → SUSPENDED)** — A `account.status.changed` with `currentStatus=LOCKED` whose `accountId` matches a tenant's seller transitions that seller ACTIVE/PENDING_PROVISIONING → SUSPENDED and persists. Unit + IT prove it.
- **AC-2 (idempotent re-delivery)** — Re-delivering the same LOCKED event (Kafka at-least-once) leaves the seller SUSPENDED with no error and no redundant persist (`Seller.suspend()` returns `false`). Unit + IT prove it.
- **AC-3 (seller-not-found fail-soft)** — A LOCKED event whose `accountId` backs no seller in the tenant is logged + skipped (no exception, no DLQ). Unit + IT prove it.
- **AC-4 (CLOSED-seller race tolerated)** — When the matched seller is CLOSED, `Seller.suspend()` throws `IllegalStateException`; the consumer catches it, WARNs, and does NOT rethrow (no DLQ). Unit proves the exception is caught.
- **AC-5 (non-LOCKED ignored)** — A `currentStatus != "LOCKED"` event (e.g. `ACTIVE`, `DORMANT`) produces no seller interaction. Unit proves it.
- **AC-6 (forward-loop-back no-op)** — The forward operator-suspend re-emits `LOCKED`; looping back here resolves to an already-SUSPENDED seller → no-op, logged DEBUG (not a spammy WARN). Covered by the already-SUSPENDED unit/IT path + DEBUG-level logging.
- **AC-7 (no IAM loop)** — The projection NEVER calls `SellerAccountProvisioner.lockAccount`/any account-service endpoint. Verified by `suspendByLockedAccount` not depending on the provisioner for the lock call (inspection + the consumer IT mocks the provisioner and asserts no error / no required interaction).
- **AC-8 (contracts updated)** — `account-lifecycle-subscriptions.md` documents the new consumer + decisions; `product-to-account.md` marks D4-C IMPLEMENTED.
- **AC-9 (tests)** — Unit `AccountStatusChangedSellerConsumerTest` + Testcontainers `@Tag("integration")` `AccountStatusChangedSellerConsumerIntegrationTest` written. `compileJava`/`compileTestJava` GREEN and the unit test GREEN locally; the IT is compile-verified only (Docker blocked on the dev Windows host per `project_testcontainers_docker_desktop_blocker`; CI-Linux authoritative).

## Related Specs

- `docs/adr/ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md` (D4 — deactivation; D4-C the reverse leg)
- `specs/services/product-service/architecture.md` (owning service of the `Seller` aggregate + inbound consumers)
- `iam-platform/specs/contracts/events/account-events.md` (`account.status.changed` producer schema — live, unchanged)

## Related Contracts

- `specs/contracts/events/account-lifecycle-subscriptions.md` (this task ADDS the product-service consumer + reverse-projection section)
- `specs/contracts/http/internal/product-to-account.md` (this task flips D4-C deferred → IMPLEMENTED)

## Edge Cases

- **Forward-loop-back** — operator suspend (BE-402) locks the account, IAM re-emits `LOCKED`, the event loops back; the seller is already SUSPENDED → idempotent no-op (DEBUG, not WARN).
- **Locked account with no seller in this tenant** — most locked accounts are not seller-operators; `findByAccountId` returns empty → skip (DEBUG/WARN), not an error.
- **Tenant derivation** — bind `payload.tenantId` (fallback envelope `tenantId`, then default tenant via `TenantContext`); the lookup is tenant-scoped so the same `account_id` never crosses tenants.
- **Non-LOCKED transitions** — ACTIVE/DORMANT/DELETED etc. are emitted on the same topic; ignored (only LOCKED acts). (DELETED → seller CLOSE is intentionally out of scope; CLOSE is operator-driven, and the GDPR cascade is BE-401's `account.deleted` path.)
- **PENDING_PROVISIONING seller** — `Seller.suspend()` allows PENDING → SUSPENDED (m1); the reverse projection follows the same domain rule.

## Failure Scenarios

- **Malformed JSON** — deserialization throws `IllegalArgumentException` (not-retryable) → routed to `account.status.changed.dlq` by the shared error handler; the lifecycle partition is not blocked.
- **CLOSED seller** — `IllegalStateException` caught + WARNed, NOT rethrown (terminal-state race tolerated → no DLQ, no retry storm).
- **DB unavailable during persist** — the short `@Transactional` update fails with a retryable exception → the shared error handler retries (exponential backoff, 3 attempts) → DLQ on exhaustion; on next at-least-once delivery the idempotent no-op converges.
- **Null payload / missing accountId** — WARN + skip (fail-soft); a poison-shaped event never blocks the partition.
