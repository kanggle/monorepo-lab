# TASK-BE-357 — Tenant-scope the admin account search/list (remove FAN_PLATFORM hardcode; mirror audit dual-read scope)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (multi-tenant data-isolation / authorization — security-sensitive)

---

## Goal

Close a **multi-tenant data-isolation gap** in the operator account search/list surface
(`GET /api/admin/accounts` → account-service `GET /internal/accounts`). Two inconsistent,
contract-undefined behaviours exist today:

1. **Email search is hard-coded to `tenant_id='fan-platform'`** —
   [`AccountQueryPortImpl.findByEmail`](../../apps/account-service/src/main/java/com/example/account/infrastructure/persistence/AccountQueryPortImpl.java) L35-37
   uses `TenantId.FAN_PLATFORM.value()` with a stale `// until TASK-BE-229` comment.
   TASK-BE-229 (`auth-jwt-tenant-claim`, **done**) only addressed the auth/login JWT claim — it
   never touched this search port, so the hard-code is permanent residue. Consequence: an
   **ecommerce** (or any non-fan) consumer account is **never findable by email** even though the
   row exists.
2. **List-all (`findAll`) is unscoped** — `findAllAccounts(pageable)` returns accounts across
   **every tenant** with no scope gate, i.e. a tenant-A operator can page through tenant-B accounts.

The audit query surface already solved exactly this problem: it scopes by a `tenantId` query param
gated against the operator's **dual-read effective scope** (home ∪ assignments — TASK-BE-249 /
TASK-BE-326), defaulting to the active tenant, with SUPER_ADMIN cross-tenant override, rejecting an
out-of-scope tenant with `403 TENANT_SCOPE_DENIED`. **Mirror that exact model** onto account
search/list so the account surface is tenant-consistent with audit. The visible outcome the user
asked for — "ecommerce 계정도 검색되게" — falls out: an operator entitled to the `ecommerce`
tenant, with that tenant active (or passed explicitly), finds ecommerce accounts; the search is no
longer fan-locked.

## Scope

**In scope:**

1. **Contract first** (`specs/contracts/http/internal/admin-to-account.md`) — add a **required**
   `tenantId` query param to `GET /internal/accounts` (both the list and the `?email=` single
   lookup). account-service filters strictly by it (it trusts admin-service to have already run the
   effective-scope gate — same internal-trust posture as the existing lock/unlock calls). Document
   that email match remains **exact** within the given tenant (the `(tenant_id, email)` unique index).
2. **Public contract** (`specs/contracts/http/admin-api.md`, `GET /api/admin/accounts`) — add a
   `tenantId` query param mirroring `GET /api/admin/audit` verbatim: omitted → operator's active/own
   tenant; `tenantId=foo` → allowed iff `foo ∈` operator effective scope OR SUPER_ADMIN; out-of-scope
   → `403 TENANT_SCOPE_DENIED`; `tenantId=*` cross-tenant view → SUPER_ADMIN only.
3. **account-service** — `AccountSearchController` / `AccountSearchQueryService` / `AccountQueryPort`
   / `AccountQueryPortImpl`: thread `tenantId` through; `findByEmail(tenantId, email)` (drop the
   `FAN_PLATFORM` constant), `findAll(tenantId, pageable)` filtered by tenant (new
   `AccountJpaRepository.findByTenantId(...)` paged query; the unscoped `findAllAccounts` is removed
   or made internal-only). Fail-closed: blank/absent `tenantId` → `400 VALIDATION_ERROR` (never an
   implicit all-tenant scan).
4. **admin-service** — `AccountAdminController.search` resolves the operator effective tenant scope
   exactly as `AuditController` / `AuditQueryUseCase` do (reuse the same scope resolver / dual-read
   gate; do **not** fork a second implementation), then passes the resolved `tenantId` to
   `AccountServiceClient.search(tenantId, email)` / `listAll(tenantId, page, size)`. The email branch
   currently bypasses `account.read` (SUPPORT_LOCK lookup) — preserve that permission nuance, but it
   must still be tenant-scoped.
5. **console-web** (`features/accounts/api/accounts-api.ts`) — default the `tenantId` query param to
   the active tenant (mirror `audit-api.ts` TASK-PC-FE-043); it already sends `X-Tenant-Id`. Surface
   `403 TENANT_SCOPE_DENIED` as the existing inline 권한/스코프 message (no crash), consistent with
   the accounts screen's degrade taxonomy.
6. **Tests** — account-service: search/list now tenant-filtered (unit + a Testcontainers Integration
   proving tenant-A operator cannot see tenant-B rows by email or by list). admin-service: scope-gate
   slice (in-scope tenant passes; out-of-scope → 403 TENANT_SCOPE_DENIED; SUPER_ADMIN cross-tenant +
   `*`). console-web: `accounts-api` defaults tenantId to active tenant; 403 scope → inline.

**Out of scope:**

- Partial / substring email matching (LIKE). The match stays **exact** — substring search is a
  separate privacy-posture decision (email enumeration surface) and is explicitly **not** part of
  this fix. (User asked about it 2026-06-14; declined in favour of the tenant-scope fix.)
- Signup tenant selection — `POST /api/accounts/signup` defaulting to FAN_PLATFORM is a separate
  concern (this task only touches the read/search path).
- The audit query surface itself (already correct — this task consumes its scope model, does not
  change it).
- Any change to the lock/unlock/gdpr/export internal endpoints (they take an explicit `accountId`,
  not a tenant-scoped search).

## Acceptance Criteria

- **AC-1 (email cross-tenant)** — With a consumer account seeded under `tenant_id='ecommerce'` and an
  operator whose effective scope includes `ecommerce` (active tenant `ecommerce`), `GET
  /api/admin/accounts?email=<that email>` returns the row. The previous fan-platform hard-code is
  gone (a fan-platform-only operator no longer sees the ecommerce row, and vice-versa).
- **AC-2 (list scoped)** — `GET /api/admin/accounts` (no email) returns ONLY accounts in the resolved
  tenant; a tenant-A operator never sees tenant-B rows. No code path performs an unscoped all-tenant
  account scan.
- **AC-3 (scope gate)** — `tenantId` out of the operator's effective scope → `403
  TENANT_SCOPE_DENIED`; in-scope (home ∪ assignment) → allowed; SUPER_ADMIN may pass any `tenantId`
  incl. `*` (cross-tenant) — byte-identical semantics to `GET /api/admin/audit` (TASK-BE-249/BE-326).
- **AC-4 (fail-closed)** — A blank/absent resolved `tenantId` at the account-service boundary →
  `400 VALIDATION_ERROR`, never an implicit cross-tenant result. `size>100` still `400`.
- **AC-5 (contract sync)** — `admin-to-account.md` + `admin-api.md` updated **before** impl; the
  request/response shapes in both match the implementation; the stale `// until TASK-BE-229` comment
  is removed.
- **AC-6 (build/CI)** — `:account-service:check` + `:admin-service:check` BUILD SUCCESSFUL; CI
  "Integration (iam, Testcontainers)" GREEN; console-web `pnpm lint` + tsc + vitest pass (no
  no-unused-vars regression). No regression to lock/unlock/audit/entitlement surfaces.

## Related Specs / Contracts

- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (search endpoints — updated here).
- `projects/iam-platform/specs/contracts/http/admin-api.md` (`GET /api/admin/accounts` — `tenantId` param added, mirroring `GET /api/admin/audit`).
- `projects/iam-platform/specs/services/admin-service/` (audit query tenant-scope precedent — the model to mirror).
- `docs/adr/ADR-MONO-020-*` (operator ↔ tenant active-scope model), `docs/adr/ADR-MONO-024-*` (tenant-admin delegation / effective scope). TASK-BE-249 (audit `tenantId` param) + TASK-BE-326 (assume-tenant / dual-read effective scope) are the implementation precedent.

## Edge Cases

- **Same email in two tenants** — the `(tenant_id, email)` unique index permits `a@x.com` under both
  `ecommerce` and `fan-platform`; the tenant-qualified lookup returns the correct one
  deterministically (today's fan-hard-code silently hides the ecommerce one — the very bug).
- **SUPER_ADMIN `tenantId=*`** — list across all tenants is the ONE legitimate unscoped read; it must
  remain gated to SUPER_ADMIN (never reachable by a tenant-scoped operator), exactly as audit's `*`.
- **Email branch permission nuance** — single-email lookup does not require `account.read`
  (SUPPORT_LOCK may look one up to lock it). Keep that, but it must still be tenant-scoped (a
  SUPPORT_LOCK operator cannot email-probe another tenant).
- **Active tenant unset** — console blocks with `400 NO_ACTIVE_TENANT` before the call (same as
  audit), never an empty/unscoped tenantId.
- **Stale-comment trap** — do not "resolve" the bug by deleting the comment only; the FAN_PLATFORM
  constant usage itself must go.

## Failure Scenarios

- **F1 — cross-tenant leak persists** — if `findAll` stays unscoped while only `findByEmail` is
  fixed, tenant-B accounts still leak via the list. Guarded by AC-2 (both paths scoped).
- **F2 — fail-open on missing tenant** — if a blank `tenantId` falls through to an all-tenant query,
  the isolation gap is reintroduced. Guarded by AC-4 (fail-closed 400).
- **F3 — divergent scope logic** — if admin-service reimplements the effective-scope gate instead of
  reusing the audit path's resolver, the two surfaces drift (one accepts a tenant the other rejects).
  Guarded by AC-3 (byte-identical to audit) + reuse mandate in Scope §4.
- **F4 — console regression** — if `accounts-api` omits the `tenantId` default, the producer rejects
  with `NO_ACTIVE_TENANT` / mis-scopes. Guarded by AC-6 + the audit-api precedent.
