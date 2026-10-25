# Task ID

TASK-BE-659

# Title

ecommerce order operator reads (`/summary` · `/insights` · list · detail) with the wildcard tenant `*` answer 200 with nothing — the contract's «`*` read is kept» was never implemented; answer 403 as `/metrics` does

# Status

done

# Owner

backend

# Task Tags

- code
- bug
- contract
- ecommerce

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (reuse `requireConcreteTenant` on four reads + contract + slice tests)
> Drafted 2026-10-25 UTC. Owner decisions 2026-10-25 UTC (AskUserQuestion): first «먼저 재고 계약대로 고친다»; after AC-0 showed the fix would mean building a new cross-tenant order read, re-asked → «`*` 는 403 — 실적 API 와 같게 (Recommended)». Facts read on origin/main `031210ecb` (Explore, read-only).

# Dependency Markers

- 출처: 2026-10-24 session handoff («ecommerce `/summary` `/insights` … `*` 에 빈 결과»).
- 선례: `TASK-MONO-817` (done) — `/metrics` with `*` → 403 (shared contract § Wildcard tenant, owner «조용한 0 금지»).
- 관련: `TASK-BE-506` (done) — opened the role gate for `*`, not the data layer.

# Goal

An operator whose token carries the wildcard tenant `*` is told the order reads need a concrete tenant, instead of seeing zero orders as if that were the truth.

# Scope

## In Scope

- **AC-0 facts (code reading, all under `apps/order-service/src/main/java/com/example/order/`):**
  - `presentation/filter/TenantContextFilter.java:39` stores the header verbatim; `domain/tenant/TenantContext.java:46-49` has no wildcard notion, so `*` reaches the repositories as the literal tenant `*`.
  - `/summary` (`AdminOrderController.java:58-64`) → `OrderQueryService.getPeriodSummary` (`:109-118`) → `OrderRepositoryImpl.java:259,264` → `OrderJpaRepository.java:155-157` exact `tenant_id = :tenantId`; `/insights` (`AdminOrderController.java:91-99`) → `OrderRepositoryImpl.java:270,282` → `OrderJpaRepository.java:173,187` the same. List and detail bind `TenantContext.currentTenant()` too (`OrderRepositoryImpl.java:128,237-244`; `OrderJpaRepository.java:24`). ⇒ `*` → zeros / empty list / 404.
  - The only `*` handling is the role gate: `presentation/OrderOperatorAccess.java:25,30-34` (`requireRead` admits `*` without order roles, TASK-BE-506); `requireConcreteTenant` (`:42-46`) is called only from `/metrics` (`AdminOrderController.java:83`).
  - The contract says the opposite: `order-api.md:3` (top note) and `:315` («`/summary`, `/insights`, the list and the detail keep it»).
  - No test reads data with tenant `*`; `AdminOrderBusinessRoleGateSliceTest.java:95-96,107-118` asserts only «not 403» for the `*` personas.
  - Console: `features/ecommerce-ops/api/orders-api.ts:99-119` calls `/orders/summary` and `/orders/insights`; the gateway sets `X-Tenant-Id` from the token's `tenant_id`, which can be `*` for a SUPER_ADMIN.
- **Fix:** `/summary`, `/insights`, the order list and the order detail call `requireConcreteTenant` like `/metrics` → `403 ACCESS_DENIED` for `*`. No cross-tenant data path is built.
- **Contract:** `order-api.md` top note and `:315` say `*` → 403 for these reads (cite TASK-MONO-817's shared § Wildcard tenant); each section's Errors table gains the row.
- **AC-0 of the impl:** census of the `*` callers before switching — console flows that reach these four with a `*` token (and what they render on 403), e2e / seed / other services. A caller that genuinely relies on `*` stops the ticket and goes back to the owner.

## Out of Scope

- Building a real cross-tenant read (owner rejected it — security surface).
- Other ecommerce services' `*` handling.

# Acceptance Criteria

- [x] **AC-0** — the `*` caller census above (file:line), or ⚪ with the reason.
- [x] **AC-1** — contract first (top note · `:315` · four Errors rows).
- [x] **AC-2** — `*` on each of the four reads → 403 `ACCESS_DENIED`; red before the change.
- [x] **AC-3** — control: a concrete tenant with the order read role → 200 on all four; the `*` personas of `AdminOrderBusinessRoleGateSliceTest` updated to the new expectation, not deleted.

# Related Specs

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` § top note · § GET /api/admin/orders/summary · /insights · list · detail
- `platform/contracts/performance-metrics-contract.md` § Wildcard tenant

# Related Contracts

- `GET /api/admin/orders/summary` · `/insights` · `/api/admin/orders` · `/api/admin/orders/{id}`

# Edge Cases

- `requireRead` admits `*` without the order roles; the 403 must come for `*` regardless of roles (as `/metrics`).
- Write endpoints are not in scope (they already require the order write role).

# Failure Scenarios

1. Only `/summary` and `/insights` change and list/detail keep the silent empty — AC-2 names four.
2. The contract keeps «`*` keeps the read» — the next reader re-opens it. AC-1.

---

# Implementation — 2026-10-25 UTC

**AC-0 — who calls these four with `*`:** nobody that relies on it.
- Console: `TASK-MONO-817` AC-0 (2026-10-24) measured «콘솔에서는 아무도 닿지 못한다» — the home composition answers 400 `NO_ACTIVE_TENANT` with no active tenant (`operator-overview-composition.ts:70`), uses the assumed concrete-tenant token otherwise, and domain screens sit behind `DomainTenantGate`. The `OrderOperatorAccess` javadoc's «the console operator overview forwards `*`» (TASK-BE-506) was stale and is corrected.
- Demo seed: `operator_token ecommerce` (concrete) — `infra/demo/seed/seed-ecommerce.sh:413,768-821`.
- e2e / other services: no `*` call to `/api/admin/orders*` found. Only the two slice tests used `*`.

**Change:**
- Contract (`order-api.md`): top note (BE-506's «`*` kept on 4 paths» struck, TASK-BE-659 rule), § metrics sentence, and the four Errors rows (which also said a stale «Requires ADMIN role»).
- `OrderOperatorAccess.requireRead` refuses `*` whatever the roles; `requireConcreteTenant` (metrics-only) folded into it and removed; `/metrics` keeps the same 403.

# Verification

- Red before: `AdminOrderBusinessRoleGateSliceTest` with the two `*` personas set to «every read 403» — both `FAILED` on the old code (the four reads answered non-403).
- After: `:order-service:test --rerun` 472 tests · 0 failures · 0 skipped (incl. `AdminOrderMetricsControllerSliceTest` — `/metrics` `*` still 403, and the concrete-tenant personas — AC-3's control).
