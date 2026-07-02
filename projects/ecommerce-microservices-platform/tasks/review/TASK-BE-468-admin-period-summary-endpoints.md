# TASK-BE-468 — Admin period-summary (오늘/주간/월간) count endpoints across the 6 operator-facing services

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (uniform additive read endpoint replicated per service; the only non-mechanical part is the shared KST calendar-boundary rule)

> Cross-project feature — the consuming half is **TASK-PC-FE-160** (platform-console overview + contract). Both land in **one atomic PR** (branch `feat/ecommerce-ops-period-metrics`). This task is the producer half.

---

## Goal

The platform-console E-Commerce **운영 개요** (operator overview, `/ecommerce`) currently shows whole-cumulative counts per area (products / orders / shippings / promotions / users / sellers / notification-templates), derived from each list endpoint's `totalElements`. There is **no time dimension** — a "47 orders" cell means "all orders ever". The operator asked for **period-based (오늘 / 주간 / 월간) metrics**.

Per ADR-MONO-017 D3.B there is no producer aggregation endpoint today and the list endpoints accept no date filter (verified: `AdminOrderController.getOrders` params = `page/size/status` only; no repository count-by-date query in any of the 6 services). This task adds a **dedicated per-service summary endpoint** returning the three calendar-period counts (+ total) in one call — the "dedicated aggregation endpoint" approach chosen over adding date-range params to the list endpoints (one round-trip per area, keeps the list contract untouched).

## Scope

Add **one `GET .../summary` endpoint per operator area** (7 endpoints across 6 services — `product-service` hosts both products and sellers):

| Area | Service | New endpoint | createdAt field |
|---|---|---|---|
| products | `product-service` | `GET /api/admin/products/summary` | `Product.createdAt : Instant` |
| sellers | `product-service` | `GET /api/admin/sellers/summary` | `Seller.createdAt : Instant` |
| orders | `order-service` | `GET /api/admin/orders/summary` | `Order.createdAt : Instant` |
| shippings | `shipping-service` | `GET /api/shippings/summary` | `Shipping.createdAt : Instant` |
| promotions | `promotion-service` | `GET /api/promotions/summary` | `Promotion.createdAt : Instant` |
| users | `user-service` | `GET /api/admin/users/summary` | `UserProfile.createdAt : Instant` |
| notification templates | `notification-service` | `GET /api/notifications/templates/summary` | `NotificationTemplate.createdAt : LocalDateTime` |

**Uniform response shape** (all 7 endpoints):

```json
{ "today": 3, "week": 12, "month": 40, "total": 47 }
```

- All values are `long`, tenant-scoped (never negative).
- `total` = tenant-scoped total row count (the current overview number — kept for back-compat display).
- `today` / `week` / `month` = tenant-scoped count of rows whose `createdAt` falls in the **KST calendar period start → now**.

**Per service, the additive change is the same 5-part slice** (mirror the existing list-read wiring; do NOT touch the list/detail/mutation endpoints):

1. **Spring Data** interface — add `long countByTenantId(String tenantId)` and `long countByTenantIdAndCreatedAtBetween(String tenantId, <T> from, <T> to)` where `<T>` = `Instant` (all services except notification) or `LocalDateTime` (notification). (Field names differ per JPA entity — match the entity's actual `createdAt` column property.)
2. **Domain repository port** — add `PeriodSummary summarize()` (or `long countCreatedBetween(Instant from, Instant to)` + `long countAll()` — implementer's choice; keep it tenant-scoped via `TenantContext.currentTenant()` in the impl, same as every other read).
3. **Repository impl (JPA adapter)** — implement using `TenantContext.currentTenant()`, exactly like the sibling reads (e.g. `OrderRepositoryImpl.findAll`).
4. **Query service** — a `@Transactional(readOnly = true)` method that computes the three KST period boundaries (see below), issues the counts, and returns the summary DTO.
5. **Controller** — `@GetMapping("/summary")` on the existing admin controller (same `@RequestMapping` base as that area's list read), returning a `…SummaryResponse` record `{ long today, week, month, total }`.

**Shared KST calendar-boundary rule** (identical in all 6 services — small intentional duplication; do NOT introduce a `libs/` shared util in this task, that would widen the blast radius to a monorepo-level change):

```java
ZoneId KST = ZoneId.of("Asia/Seoul");
ZonedDateTime now = ZonedDateTime.now(KST);
ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(KST);
ZonedDateTime weekStart  = now.toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay(KST); // ISO week, Monday
ZonedDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(KST);
// Instant-typed createdAt: pass `.toInstant()` and `now.toInstant()`.
// LocalDateTime-typed createdAt (notification): pass `.toLocalDateTime()` and `now.toLocalDateTime()`
//   (the KST wall-clock the templates were written against).
```

- The upper bound is **now** (calendar period-to-date, not the whole calendar unit) — 오늘 = KST 자정→현재, 주간 = 이번 주 월요일 00:00 KST→현재, 월간 = 이달 1일 00:00 KST→현재.
- Use a half-open range `[start, now]` — `countByTenantIdAndCreatedAtBetween` (Spring Data `Between` is inclusive on both ends; acceptable — a boundary-instant duplicate is negligible and the ends are start-of-day / now).

**Authorization / tenancy**: unchanged. The endpoints are admin reads under the same `@RequestMapping` base as each area's existing list read, so they inherit the same gateway admission (`/api/admin/**` → `roles ∋ ADMIN`, or the shipping/promotion gateway rule for those non-`/admin` paths) and the repository `WHERE tenant_id` chokepoint. No new RBAC, no new gateway route class (the base path is already routed).

**Out of scope:** date-range params on the list endpoints; coupon counts (the 프로모션 area counts *promotions*, not coupons — ignore `Coupon.issuedAt`); any time-series/grouped-by-day payload (only the 3 period totals + grand total); a shared `libs/` boundary util; console/front-end changes (TASK-PC-FE-160).

## Acceptance Criteria

- **AC-1** — Each of the 7 endpoints returns `200` with `{ today, week, month, total }` (all `long`), tenant-scoped, for an `ADMIN`-role token on the correct tenant.
- **AC-2** — Counts are correct against the KST calendar boundaries: a row created earlier today (KST) increments `today`, `week`, `month`, `total`; a row created last month increments only `total`; `today ≤ week ≤ month ≤ total` always holds for a single tenant.
- **AC-3** — Tenant isolation: a row under tenant B is invisible to tenant A's summary (same `WHERE tenant_id` guarantee as the list reads). Add/extend a repository or service test that pins this.
- **AC-4** — The existing list / detail / mutation endpoints are byte-unchanged (no signature or behavior change); `./gradlew :<each-service>:compileJava :<each-service>:test` passes.
- **AC-5** — A focused unit/slice test per service covers: empty (all zeros), a row in each period bucket, and the tenant-isolation case (AC-3). Testcontainers IT is authoritative in CI Linux (local Windows npipe is flaky — do not gate on local IT).

## Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (ecommerce overview snapshot leg) + § 2.4.10 (ecommerce operator binding) — the consumer contract updated in TASK-PC-FE-160.
- ADR-MONO-017 (console fan-out model; D3.B "no producer /summary" — this task revisits that for the operator overview specifically), ADR-MONO-030 Step 2/3 (tenant chokepoint + product-service marketplace inner axis hosting sellers).

## Related Contracts

- New reads, added to console-integration-contract § 2.4.10 as summary endpoints (#18 orders-summary and the per-area subsection equivalents) — see TASK-PC-FE-160.

## Edge Cases

- **KST vs stored UTC** — `Instant` createdAt is UTC; boundaries are computed in `Asia/Seoul` then converted to `Instant`, so "오늘" is the Korean calendar day, not UTC day (a row created 2026-07-03 08:00 KST = 2026-07-02 23:00 UTC still counts as *today* in KST).
- **notification `LocalDateTime`** — stored as a naive wall-clock; compare against the KST `LocalDateTime` bounds (`.toLocalDateTime()` of the KST `ZonedDateTime`). Documented as an intentional per-service divergence.
- **Week start** — ISO-8601 Monday (matches Korean business-week convention); `with(DayOfWeek.MONDAY)` on the current week.
- **Empty tenant** — all four values `0`, never null, never a negative or error.
- **Seller/Product co-hosting** — both endpoints live in `product-service` against their own entities/repositories; they are independent (a product created today does not affect the seller summary).

## Failure Scenarios

- **Missing `TenantContext`** (no tenant claim) — same behavior as the sibling list reads (the gateway `TenantClaimValidator` rejects a blank `tenant_id` before the controller; the count queries never run tenant-less).
- **DB unavailable** — the count query throws; the existing `GlobalExceptionHandler` maps it to the service's standard `503`/`500` envelope. The console overview treats a non-200 summary cell as "점검 필요" (degraded), never blanks the section (consumer resilience — TASK-PC-FE-160).
- **Clock/zone misconfig** — `ZoneId.of("Asia/Seoul")` is hard-coded (not server-local `ZoneId.systemDefault()`), so a UTC-configured container still computes Korean calendar periods correctly.
