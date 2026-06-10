# Task ID

TASK-MONO-210

# Title

ADR-MONO-024 § 3.3 step 3 — tenant-admin delegation federation-e2e proof. Adds a federation-hardening-e2e spec proving the delegated-administration authority model (built in-process by step 1 D2 confinement / step 2a roles / step 2b assign-surface + grant-menu) bites end-to-end on the live stack: a NON-platform `TENANT_ADMIN` (scoped to a dedicated tenant via its `admin_operator_roles` grant-row tenant_id) assigns/scopes/peer-appoints within its own tenant (2xx) but is denied cross-tenant (403 `TENANT_SCOPE_DENIED`) and on platform-escalating / cross-plane grants (403 `ROLE_GRANT_FORBIDDEN`); a `TENANT_BILLING_ADMIN` suspends/resumes its own subscription (200) but is denied cross-tenant (403); `SUPER_ADMIN` ('*') is unconstrained on the same surface (net-zero). Driven through the real OIDC login → `console_operator_token` exchange → `/api/admin/**` RBAC surface.

# Status

ready

# Owner

backend

# Task Tags

- e2e
- federation-hardening
- adr
- multi-tenant
- rbac
- security

---

# Dependency Markers

- **proves**: ADR-MONO-024 D2 (target-tenant confinement) + D3 (assign/unassign surface + grant-menu no-escalation) + D4-B (in-tenant `TENANT_ADMIN` sub-delegation) + D5-C (`TENANT_BILLING_ADMIN` plane separation) — the runtime capstone of the § 3.3 staged roadmap.
- **depends on**: TASK-BE-345 (step 1 D2 `AdminGrantScopeEvaluator` + `TenantScopeGuard`), TASK-BE-346 (step 2a the two delegated roles + `tenant.admin.delegate`), TASK-BE-347 (step 2b `ManageOperatorAssignmentUseCase` + `RoleGrantGuard` + the assign/unassign endpoints).
- **builds on (harness)**: TASK-MONO-207 (the dedicated-tenant + admin-surface federation-e2e pattern — `umbrella-corp` mirrors `initech-corp`).

# Goal

Make the ADR-024 delegated-administration invariants executable on the full federation stack: prove, through the real login → admin RBAC mutation path, that a non-platform tenant admin can manage its own tenant's operators and subscriptions without a platform ticket and can never exceed its grant (no cross-tenant reach, no role escalation), while SUPER_ADMIN is unaffected.

# Scope

**Account-side seed (entitlement plane — the billing leg needs a real subscription):**
- NEW `projects/iam-platform/apps/account-service/.../db/migration-dev/V9003__seed_umbrella_e2e_customer.sql` — a DEDICATED tenant `umbrella-corp` [finance] ACTIVE (present at account-service startup, like globex V9001 / initech V9002; isolated so the runtime suspend/resume cannot race-break the fullyParallel acme/globex/initech specs).

**Admin-side seed (IAM plane — the actors + their target):**
- `tests/federation-hardening-e2e/fixtures/seed.sql` § 15 — three operators scoped to `umbrella-corp`: `tenant-admin-umbrella` (role TENANT_ADMIN @ umbrella-corp), `tenant-billing-admin-umbrella` (role TENANT_BILLING_ADMIN @ umbrella-corp), and `deleg-target-umbrella` (role SUPPORT_READONLY @ umbrella-corp, the throwaway target — no auth_db credential, never logs in). The grant-row tenant_id IS the confinement scope.

**Harness + proof:**
- `tests/federation-hardening-e2e/fixtures/login.ts` — `loginAsTenantAdmin` / `loginAsTenantBillingAdmin` (same production-identical OIDC PKCE flow; activeTenant=null — the proof reads the operator token and calls the admin surface directly).
- NEW `tests/federation-hardening-e2e/specs/tenant-admin-delegation.spec.ts` — the proof (below).
- `tests/federation-hardening-e2e/README.md` — post-MVP spec note.

**Spec design (3 tests):**
- **TENANT_ADMIN**: assign target → umbrella (201) · org_scope on umbrella (200) · cross-tenant assign → globex (403 `TENANT_SCOPE_DENIED`) · grant TENANT_ADMIN (200, ≤-own sub-delegation) · grant SUPER_ADMIN (403 `ROLE_GRANT_FORBIDDEN`) · grant TENANT_BILLING_ADMIN (403 `ROLE_GRANT_FORBIDDEN`, lacks subscription.manage) · unassign (204).
- **TENANT_BILLING_ADMIN**: suspend umbrella/finance (200) · resume (200) · cross-tenant suspend globex/scm (403 `TENANT_SCOPE_DENIED`).
- **SUPER_ADMIN net-zero**: assign target → globex (201, '*' bypasses confinement) · unassign (204).

# Acceptance Criteria

- **AC-1 (own-tenant administration)** The `TENANT_ADMIN` assigns + org-scopes + unassigns the target within `umbrella-corp` (201/200/204) and grants `TENANT_ADMIN` to it (200, in-tenant sub-delegation).
- **AC-2 (cross-tenant confinement)** The `TENANT_ADMIN` assigning into `globex-corp` → 403 `TENANT_SCOPE_DENIED`; the `TENANT_BILLING_ADMIN` suspending `globex-corp` → 403 `TENANT_SCOPE_DENIED`.
- **AC-3 (no escalation)** The `TENANT_ADMIN` granting `SUPER_ADMIN` → 403 `ROLE_GRANT_FORBIDDEN`; granting `TENANT_BILLING_ADMIN` (cross-plane, not held) → 403 `ROLE_GRANT_FORBIDDEN`.
- **AC-4 (billing happy path)** The `TENANT_BILLING_ADMIN` suspends then resumes `umbrella-corp/finance` (200/200) through the real `subscription.manage` surface.
- **AC-5 (net-zero)** `SUPER_ADMIN` performs the exact cross-tenant assign the `TENANT_ADMIN` was denied (201) — confinement is grant-scope-driven, not a blanket restriction.
- **AC-6** GREEN on the federation-hardening-e2e workflow (nightly / `gh workflow run federation-hardening-e2e.yml`), all specs (the new spec parallel-safe with the existing cohort).

# Related Specs

- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` § 3.3 step 3
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` (harness location/scope)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (the confinement + grant-menu rules under proof)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (the assign/unassign + subscription + roles surfaces the spec drives)

# Edge Cases

- `umbrella-corp` + `deleg-target-umbrella` are referenced by NO other spec → the assign/re-role/suspend mutations are parallel-safe; `finally` restores all state (assignments deleted, target role reset to SUPPORT_READONLY, subscription resumed) for re-runnability + the CI `retries: 2`.
- The cross-tenant DENY paths (assign globex / suspend globex) fail in `TenantScopeGuard` BEFORE any write or downstream delegation — globex-corp is never mutated, so the globex specs are untouched.
- Dev seeds MUST be present at account-service startup (keystone reverse-lookup only returns Flyway-loaded rows — the MONO-160 lesson) → `umbrella-corp` is Flyway-dev (V9003), NOT seed.sql; only the admin_db operators/roles are in seed.sql.
- The target operator (`deleg-target-umbrella`) has NO auth_db credential — it is only the object of mutations (path `{operatorId}`), never an authenticated caller.
- The grant-row `admin_operator_roles.tenant_id` (not the operator's home tenant, not the operational assignment scope) is the admin-grant confinement scope — bound at `umbrella-corp` (not `'*'`), which is what makes the actor non-platform.
- `X-Operator-Reason` is required by every mutation surface (`ReasonRequiredException` → 400 otherwise); the spec always sends it.

# Failure Scenarios

- If the confinement read the operational (assume-tenant) scope instead of the admin-grant scope, a target assigned to operate globex could be administered there — the spec's cross-tenant 403 (AC-2) guards against that regression.
- If the grant-menu compared against anything other than the actor's held permissions, a `TENANT_ADMIN` could escalate to `SUPER_ADMIN` — the single most dangerous IAM bug; AC-3 guards it on the live stack.
- If `SUPER_ADMIN` were caught by the new confinement (a net-zero regression), AC-5 would fail (the cross-tenant assign would 403).
- If a future production account-service migration reuses a V9000+ number, the dev band would collide — the band is far above the production timeline by design (mirrors the MONO-207 V9001/V9002 lesson).
