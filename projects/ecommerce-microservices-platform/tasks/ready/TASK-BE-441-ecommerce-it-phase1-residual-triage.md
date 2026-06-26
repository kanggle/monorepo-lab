# TASK-BE-441 — Triage ecommerce IT Phase-1 residual failures (assertion drift + behavioral)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (per-test triage — each is test-expectation drift OR a small product behaviour question; classify then fix or re-ticket)

**Service:** order-service

> **Origin.** Surfaced by **TASK-MONO-307** (ecommerce integration CI lane), first-ever CI run. These order ITs failed for reasons **other** than the two systemic bugs ([[TASK-BE-439]] lazy-init, [[TASK-BE-440]] payment consumer-tx). They are **quarantined** with `@Disabled("TASK-BE-441: …")` (method-level, preserving the passing tests in each class) so the lane is GREEN; this task triages each — most are likely **test-expectation drift** (in-scope to fix), a few may be real product behaviour (re-ticket).

---

## Goal

Five quarantined order ITs failed with assertion / behavioural mismatches (not the lazy-init or consumer-tx bugs). Determine for each whether the **test expectation is stale** (fix the assertion) or the **product behaviour regressed** (file a product fix task), then un-quarantine.

## Scope (order-service test triage; product fix only if a real regression is confirmed)

1. **Error-code drift — `403` auth tests** (`OrderQueryIntegrationTest.getOrder_differentUser_returns403`, `OrderCancellationIntegrationTest.cancelOrder_differentUser_returns403`). Both assert `$.code == "UNAUTHORIZED"` but the app returns `ACCESS_DENIED` (HTTP 403). Decide the correct vocabulary (403 ⇒ `ACCESS_DENIED` is almost certainly correct; `UNAUTHORIZED` is 401) and fix the test assertions — unless the gateway/contract mandates a different code, in which case fix the producer.
2. **Seller-scope ABAC AC-3** (`SellerScopeIsolationIntegrationTest.multiSellerOrder_visibleToEachSeller_linesAttributed`) — MockMvc assertion failed. Determine whether the multi-seller visibility/attribution behaviour or the test's expectation drifted (relates to the seller-scope ABAC work, ADR-MONO-042 D4-C).
3. **`@Version` optimistic-lock did not raise** (`OrderRepositoryImplIntegrationTest.save_versionConflict_throwsOptimisticLockingFailureException`) — "Expecting code to raise a throwable" (no `OptimisticLockingFailureException`). Determine whether the `@Version` conflict path regressed, or the IT no longer constructs a real stale-version write on the singleton/real Postgres path.
4. **`saveAll` non-existent id did not raise** (`OrderRepositoryImplIntegrationTest.saveAll_nonExistentOrderId_throwsIllegalStateException`) — expected `IllegalStateException` not thrown.
5. **Refund-on-PENDING DLQ** (`OrderPaymentRefundedIntegrationTest.paymentRefunded_pendingOrder_propagatesException`) — expected exception (for DLQ routing) not propagated when a `PaymentRefunded` arrives for a `PENDING` order.

For each: classify (test-drift vs product), fix in place or re-ticket the product bug, and **remove the `@Disabled`** so it passes on the ecommerce integration lane (TASK-MONO-307).

## Acceptance Criteria

- **AC-1** — Each of the five quarantined tests is triaged: either its assertion is corrected (test-drift) or a product fix is applied / a separate product task is filed (with the IT re-quarantined against it).
- **AC-2** — All non-re-ticketed tests are un-`@Disabled` and GREEN on the ecommerce integration lane; Docker-free `:order-service:check` unchanged.
- **AC-3** — The triage decision (test-drift vs product) is recorded per test in the PR description.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md`; seller-scope ABAC: ADR-MONO-042.

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/` error-code vocabulary (if the 403 code is contract-driven).

## Dependencies / Prior Work

- **TASK-MONO-307** — holds the quarantine; this task lifts the residual five. Independent of [[TASK-BE-439]] / [[TASK-BE-440]] (different root causes).

## Edge Cases

- **Concurrency ITs on real Postgres** — the `@Version` / concurrent tests may behave differently against a real container than the prior (never-run) expectation; verify the race actually occurs before assuming a product regression.
- **Error-code contract** — if any gateway/front-end depends on the literal `$.code`, changing producer vs test must follow the contract, not convenience.

## Failure Scenarios

- **F1 — fixing the test to mask a regression** — if `@Version`/DLQ behaviour genuinely regressed, "fixing" the assertion hides it. Mitigation: AC-1 requires confirming the behaviour before editing the expectation.
