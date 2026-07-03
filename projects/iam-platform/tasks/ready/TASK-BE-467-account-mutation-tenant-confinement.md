# TASK-BE-467 — Multi-tenant admin account-mutation propagation + confinement (was: GAP-ACCOUNT-01)

- **Status**: ready
- **Domain**: iam (admin-service + account-service)
- **Type**: correctness + latent-confinement (backend) — **downgraded from "security-critical vuln"**
- **분석=Opus 4.8 / 구현 권장=Opus 4.8**

## ✅ UNBLOCKED (2026-07-03) — now a bounded, actionable residual

Prerequisite check: **TASK-BE-228 (account tenant schema) AND TASK-BE-229 (auth
JWT tenant claim) are BOTH in `tasks/done/`.** The earlier "tangled with deferred
multi-tenant-account work → not a small patch" framing is therefore **stale** —
the foundation is in place. What remains is exactly this task's scope: **thread the
real tenant through the admin account-mutation write-path + confine it.** No open
epic blocks it; the iam `ready/` queue is otherwise near-empty. It is NOT urgent
(not an exploitable hole — see below) but it is now a well-scoped finishing slice.

## ⚠️ REVISED after code verification (2026-07-02) — original premise was WRONG

The Explore-agent framing ("account mutations are cross-tenant-OPEN → exploitable
today") is **inaccurate**. Direct code read of `AccountStatusUseCase` /
`GdprDeleteUseCase` / `DataExportUseCase` (account-service) shows every admin-
reachable mutation use-case does `accountRepository.findById(TenantId.FAN_PLATFORM,
accountId)` — **hard-pinned to FAN_PLATFORM** (TASK-BE-228 debt; the "until
TASK-BE-229" comments are stale — BE-229 shipped the auth *login* tenant claim
but never removed this admin-path pin — still present as of 2026-07-03 grep).
`findById(tenant, id)` is tenant-scoped (returns empty for a foreign tenant), so:

- A cross-tenant lock/gdpr/export attempt → `findById(FAN, foreignId)` → **404**,
  NOT a silent cross-tenant mutation. **There is no exploitable-today hole.**
- The REAL issues are: **(A) correctness bug** — admin can only mutate
  FAN_PLATFORM accounts; ecommerce/acme/globex accounts 404 (the read path shows
  them, but lock/gdpr/export fail); **(B) latent confinement** — required WHEN the
  FAN pin is removed (which this task does).

**Corrected fix shape:** account-service admin-mutation controllers read
`X-Tenant-Id` → thread into the use-case → replace the `TenantId.FAN_PLATFORM`
literal with `TenantId.of(header)` (absent → FAN_PLATFORM = net-zero). Because
`findById(realTenant, id)` is already tenant-scoped, a wrong tenant → 404 =
enumeration-safe confinement **for free**. admin-service resolves the active
tenant via `QueryTenantScopeGate` and stamps it as `X-Tenant-Id` (as the design
below describes). Session-revoke is a separate enforcement point (auth-service
credentials/refresh_tokens tenant_id). Scope must touch ONLY the admin-reachable
mutation use-cases — NOT the consumer-facing FAN-pinned use-cases
(signup/verify-email/profile/last-login = genuine BE-229 Phase-3 debt, out of scope).

**Lesson**: verify REAL-GAP (module-liveness + grep cross-check) before acting —
the Explore sweep missed the FAN pin two call-frames below the controller.

---

## Goal

Bring the admin account **mutation** path (`lock` / `unlock` / session-`revoke` /
`gdpr-delete` / `export` / `bulk-lock`) to **tenant parity with the read path**:
honor the actor's active tenant (`X-Tenant-Id`) instead of the hard-pinned
`TenantId.FAN_PLATFORM` literal, and confine the target account to that tenant.

The READ path (`GET /api/admin/accounts`) is ALREADY confined via
`QueryTenantScopeGate` (TASK-BE-357) — this task brings the mutation path to
parity, **net-zero for the only current holder (SUPER_ADMIN `'*'`)** and for any
caller that omits `X-Tenant-Id` (defaults to FAN_PLATFORM = today's behavior).

## Design (authoritative — mirror the read path + the existing `X-Tenant-Id` defense-in-depth)

Two-layer confinement, keyed on the actor's **active tenant** (`X-Tenant-Id`):

**Layer 1 — admin-service (actor may act in tenant T):**
- Each mutation controller reads `@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId`.
- Resolve via the EXISTING read-path gate:
  `resolved = queryTenantScopeGate.resolve(op, tenantId, <ActionCode>, <Permission>).tenantId()`
  (omitted header → operator's own tenant; out-of-scope → `403 TENANT_SCOPE_DENIED` + best-effort DENIED row — identical to the list).
- Thread `resolved` into the command → use-case → `AccountServiceClient`.

**Layer 2 — account-service (the mutation actually honors the tenant):**
- Replace the `accountRepository.findById(TenantId.FAN_PLATFORM, id)` literal in
  the admin-reachable mutation use-cases (`AccountStatusUseCase` lock/unlock/
  changeStatus, `GdprDeleteUseCase`, `DataExportUseCase`) with
  `findById(TenantId.of(header), id)`.
- The internal mutation controllers read
  `@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId`;
  absent OR `'*'` → default to `TenantId.FAN_PLATFORM` (**net-zero** — byte-identical
  to today). Present + concrete → that tenant.
- Because `findById(realTenant, id)` is tenant-scoped, a wrong-tenant target →
  empty → the existing not-found path → **`404 ACCOUNT_NOT_FOUND`** (enumeration-safe;
  never leak cross-tenant existence, never a 403 that confirms it exists elsewhere).
  Do NOT add a new cross-tenant finder that bypasses isolation.

**admin `AccountServiceClient`**: switch the mutation calls (`lock`, `unlock`,
`gdprDelete`, `export`, and the session-revoke call — check
`SessionAdminUseCase`'s client) to stamp `X-Tenant-Id` using the EXISTING
`callPostWithTenant` / `callGetWithTenant` helpers (already present for the
identity EPs). Add a `tenantId` parameter to each mutation method.

`bulk-lock`: `BulkLockAccountUseCase` delegates per-row to
`AccountAdminUseCase.lock(...)`; threading `resolved` through the bulk command →
per-row lock automatically confines every row (a cross-tenant row → that row's
result = the mapped `404`/failure, not a whole-batch failure — preserve the
per-row outcome shape).

## Scope

- admin-service: `AccountAdminController`, `SessionAdminController`,
  `AdminGdprController` (+ their use-cases + command records +
  `AccountServiceClient`), `BulkLockAccountUseCase` / `BulkLockAccountCommand`.
- account-service: `AccountStatusUseCase`, `GdprDeleteUseCase`, `DataExportUseCase`
  (replace the FAN literal + update the stale `// until TASK-BE-229` comments) +
  the internal mutation controllers (read `X-Tenant-Id`, default → FAN).
- Contract: `iam/specs/contracts/http/internal/admin-to-account.md` +
  `admin-api.md` account mutation sections — document the `X-Tenant-Id` header +
  the `404 ACCOUNT_NOT_FOUND` cross-tenant behavior.

Out of scope: granting any account.* permission to TENANT_ADMIN (that is the
follow-up delegation task); GDPR/export delegation policy; the consumer-facing
FAN-pinned use-cases (signup/verify-email/profile/last-login).

## Acceptance Criteria

- AC-1: A non-platform operator (or a SUPER_ADMIN with active tenant = T) can
  only lock/unlock/revoke/gdpr/export/bulk-lock accounts whose `tenant_id == T`;
  a cross-tenant target → `404 ACCOUNT_NOT_FOUND` (enumeration-safe), audited as
  FAILURE (not SUCCESS).
- AC-2: **NET-ZERO** — SUPER_ADMIN `'*'` with no active tenant (X-Tenant-Id
  absent/`'*'`) behaves byte-identically to today (FAN_PLATFORM accounts). No
  existing test regresses.
- AC-3: The confinement reuses `QueryTenantScopeGate` (admin) + `X-Tenant-Id`
  defense-in-depth at account-service — NO new bespoke scope logic, NO new
  cross-tenant repository finder.
- AC-4: Out-of-scope active tenant (non-platform requests a tenant not in
  home ∪ assignments) → `403 TENANT_SCOPE_DENIED` (same as the list read).
- AC-5: `./gradlew :apps:admin-service:test :apps:account-service:test` green;
  Testcontainers IT covering cross-tenant lock → 404 and same-tenant → success
  (IT is the authority — the Docker-free `:test` will not exercise the wiring).
  Requires seeding at least one non-FAN tenant account to exercise same-tenant success.
- AC-6: Contracts updated (admin-to-account.md + admin-api.md).

## Related Specs / Contracts

- `iam/specs/services/admin-service/rbac.md` (account.* keys, QueryTenantScopeGate vs TenantScopeGuard)
- `iam/specs/contracts/http/internal/admin-to-account.md`
- `iam/specs/contracts/http/admin-api.md` (account mutation sections)

## Edge Cases

- Account exists in tenant A, operator scoped to B → `404` (not 403; no existence leak).
- bulk-lock mixing in-tenant + cross-tenant accountIds → in-tenant locked, cross-tenant rows → per-row `ACCOUNT_NOT_FOUND` outcome (batch still 200).
- X-Tenant-Id `'*'` from a non-platform operator → the gate already rejects (they can't resolve `'*'`); belt-and-suspenders at account-service treats `'*'`/absent as FAN default, but the admin gate is the primary guard.
- export uses `audit.read` (not account.lock) — still must be tenant-confined (same header treatment).

## Failure Scenarios

- account-service down → existing circuit-breaker/DownstreamFailure path unchanged (audit FAILURE).
- Missing X-Tenant-Id from a non-platform operator → admin gate defaults to their own tenant (safe confinement), never unscoped.
