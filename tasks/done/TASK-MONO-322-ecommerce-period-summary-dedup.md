# TASK-MONO-322 — Deduplicate the ecommerce period-summary slices (shared KST bounds + shared PeriodSummary record; product-service consistency)

**Status:** done

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (mechanical dedup replicated per service; behavior-preserving)

> Follow-up refactor of TASK-BE-468 (period-summary endpoints across 6 ecommerce services, merged #2124). Monorepo-level because it adds shared `libs/java-common` classes consumed by all 6 services → one atomic cross-project PR.

---

## Goal

TASK-BE-468 shipped a `GET .../summary` endpoint per operator area returning `{today, week, month, total}` (KST calendar-period-to-date). To avoid a shared-lib blast radius it was implemented with **per-service duplication**: the KST boundary computation (~5 lines) is copy-pasted in all 6 services, and each service defines its own `{today,week,month,total}` result + response records (12 near-identical record types) with divergent names. product-service additionally introduced standalone `ProductSummaryService`/`SellerSummaryService` classes (unlike the other 5, which added a method to the existing query service), which broke the existing `@WebMvcTest` slices (patched with extra `@MockitoBean`s).

This refactor removes that duplication and inconsistency **without changing any endpoint behavior or JSON shape**.

## Scope

**Shared (`libs/java-common`) — already added on this branch:**

1. `com.example.common.time.KstPeriodBounds` — computes KST today/week/month starts + now; exposes `Instant` accessors (5 services) and `LocalDateTime` accessors (notification). Factories `now()` and `from(Clock)`.
2. `com.example.common.summary.PeriodSummary` — `record (long today, long week, long month, long total)`; serialises to the identical `{today,week,month,total}` JSON; reused as **both** the application result and the REST response.

**Per service (order, product[+seller], shipping, promotion, user, notification):**

3. Replace the inline KST boundary block with `KstPeriodBounds.now()` (or `.from(clock)` where a `Clock` is already injected, e.g. promotion) and its `Instant`/`LocalDateTime` accessors.
4. Delete the per-service result record (`AdminOrderSummaryStats`, `ShippingPeriodCountResult`, `UserCountSummaryResult`, `ProductPeriodSummary`, `SellerPeriodSummary`, `PromotionCountSummary`, `TemplateSummaryResult`) **and** the per-service response record (`AdminOrderSummaryResponse`, `ShippingSummaryResponse`, `UserCountSummaryResponse`, `ProductSummaryResponse`, `SellerSummaryResponse`, `PromotionSummaryResponse`, `TemplateSummaryResponse`). Return the shared `PeriodSummary` from the query service and the controller directly.
5. Standardise the query-service method name to `getPeriodSummary()` and the domain-port count methods to `long countAllForTenant()` + `long countCreatedBetween(<T> from, <T> to)` (`<T>` = `Instant`, or `LocalDateTime` for notification). Keep the tenant-scoping via `TenantContext.currentTenant()` in the impl unchanged.
6. **product-service consistency (item ②)**: fold `ProductSummaryService`/`SellerSummaryService` into the existing `QueryProductService`/`SellerQueryService` (add a `getPeriodSummary()` method), delete the standalone service classes, remove the controller's separate dependency, and **revert the extra `@MockitoBean ProductSummaryService`/`SellerSummaryService`** added to `AdminProductControllerTest`/`ProductControllerTest`/`ProductApiContractTest`/`AdminSellerControllerTest` (the controller no longer has that dependency). Delete `KstPeriodBoundary` (product-local helper) in favour of the shared class.
7. Update each service's summary unit/slice test to the new types/method names (behavior identical).

**Out of scope:** endpoint paths, auth/tenant model, the JSON response shape (byte-identical), the console-web consumer (already parses `{today,week,month,total}` — unaffected), any non-ecommerce service.

## Acceptance Criteria

- **AC-1** — All 7 endpoints return the identical `{today,week,month,total}` JSON as before (no behavior change); the console overview is unaffected.
- **AC-2** — Zero inline KST boundary computation remains in the 6 services (all via `KstPeriodBounds`); zero per-service `{today,week,month,total}` record types remain (all use `com.example.common.summary.PeriodSummary`).
- **AC-3** — product-service has no `ProductSummaryService`/`SellerSummaryService`/`KstPeriodBoundary`; the summary lives on `QueryProductService`/`SellerQueryService`; the reverted slice tests no longer mock a summary service and still pass.
- **AC-4** — `./gradlew :libs:java-common:test :projects:ecommerce-microservices-platform:apps:<each>:test` passes (summary tests + the previously-broken product slices). Testcontainers IT is authoritative in CI Linux.

## Related Specs

- TASK-BE-468 (`tasks/done` in projects/ecommerce-microservices-platform) — the feature this refactors.
- `libs/README` / shared-library policy — the new classes are project-agnostic (pure date math + a plain count DTO), permitted in `libs/`.

## Related Contracts

- None changed — the `{today,week,month,total}` response is byte-identical; console-integration-contract § 2.4.10 stays as-is.

## Edge Cases

- **notification `LocalDateTime`** — uses the `*Local()` accessors of `KstPeriodBounds`; behavior identical to the previous inline `.toLocalDateTime()`.
- **promotion injected `Clock`** — uses `KstPeriodBounds.from(clock)` to preserve the existing testable clock; the other 5 use `now()`.
- **week start** — `KstPeriodBounds` uses ISO Monday, matching every service's prior inline logic.

## Failure Scenarios

- **Behavior drift** — any change to the computed counts would be a regression; the summary unit tests (empty→zeros, today-row bucketing, tenant isolation) pin equivalence.
- **Other-project break** — `libs/java-common` change is purely additive (two new classes), so no existing consumer of java-common is affected; `:libs:java-common:test` + the ecommerce builds confirm.
