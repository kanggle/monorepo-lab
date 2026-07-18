# TASK-FIN-BE-049 — finance super-admin wildcard READ authority parity (account + ledger)

- **Type**: TASK-FIN-BE (backend — Spring Security authorization gate)
- **Status**: ready
- **Service**: finance-platform `account-service` + `ledger-service` (identical gate in each)
- **Domain/traits**: saas / [transactional]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (security-critical authorization gate)

## Goal

Close the finance-side authority-layer straggler that leaves a **platform super-admin** (the
`platform-console` super-admin persona, `tenant_id='*'`) with a `forbidden` finance overview card.
Grant a READ-only `ROLE_FINANCE_SUPERADMIN_READ` authority to a token whose `tenant_id` claim equals
the platform wildcard `"*"`, so its READS pass the `/api/finance/**` authorization gate — while WRITES
stay gated on `finance.write` scope / an operator role. This is the **authority-layer analogue of the
tenant gate's `allowSuperAdminWildcard()`**, and the wildcard sibling of **TASK-FIN-BE-048** (which
closed the *entitlement-trust* read straggler, keyed on `entitled_domains ∋ finance`). One axis over:
FIN-BE-048 closed `entitled_domains`, this closes `tenant_id='*'`.

## Runtime evidence (nightly-e2e run `29635409302` against main `de8edcae4`)

The console `operators-profile.spec.ts` super-admin persona (platform-console super-admin,
`tenant_id='*'`) sees the finance overview card `forbidden`. Playwright trace of the
`/dashboards/overview` RSC payload:

```
"domain":"finance","status":"forbidden","reason":"PERMISSION_DENIED"
```

A real **403 at the authorization layer** — NOT `TENANT_FORBIDDEN` (the tenant gate opened), NOT
`MISSING_PREREQUISITE` (there is a finance_default_account_id). The asymmetry is WITHIN finance:

- finance-account + ledger-service `ServiceLevelOAuth2Config` wires the tenant gate with
  `.allowSuperAdminWildcard().trustEntitledDomains()` → a `tenant_id='*'` super-admin token **passes
  layer-1** (the tenant gate opens the wildcard "so a platform operator can reach this edge during
  incident response").
- But `SecurityConfig.readAuthorities = [SCOPE_finance.read, SCOPE_finance.write, ROLE_OPERATOR,
  ROLE_ADMIN, ROLE_SUPER_ADMIN, ROLE_FINANCE_OPERATOR, ROLE_FINANCE_VIEWER]` admits **none** of what
  the super-admin base OIDC domain-facing token carries:
  - no finance scope (`scope=openid profile email tenant.read`);
  - **no domain roles** — the super-admin's `SUPER_ADMIN` is an `admin_db.admin_operator_roles` entry
    (the ADMIN plane), which per **ADR-033 S2 / ADR-034 U5 disjointness** is DELIBERATELY kept OFF the
    domain-facing token, so the token has no `ROLE_SUPER_ADMIN`;
  - `entitled_domains=[]` — so FIN-BE-048's `ROLE_FINANCE_VIEWER` is not granted either.
  ⇒ 403.

So the tenant layer opens the wildcard but the read-authority layer (tightened to an explicit list by
FIN-BE-046/047) never got a matching **wildcard-read admission**. FIN-BE-048 closed the *entitlement*
read straggler (`entitled_domains ∋ finance`); this closes the *platform-wildcard* read straggler
(`tenant_id='*'`).

**Why NOT fix it in auth-service:** minting `ROLE_SUPER_ADMIN` on the domain-facing token would breach
the ADR-033/034 admin/domain plane disjointness. The fix is finance-side, mirroring the tenant gate's
own wildcard relaxation at the authority layer.

## Scope

- **In**: `ActorContextJwtAuthenticationConverter` (both services) — synthesise a READ-only
  `ROLE_FINANCE_SUPERADMIN_READ` when `TenantClaimValidator.WILDCARD_TENANT.equals(tenant_id)` (the
  `tenant_id` claim already extracted at the top of `convert`, via
  `TenantClaimValidator.CLAIM_TENANT_ID`); `SecurityConfig` (both services) — add
  `ROLE_FINANCE_SUPERADMIN_READ` to `readAuthorities` **only** (never `writeAuthorities`); class Javadoc
  updated to document the wildcard-read path; converter unit tests + HTTP scope-enforcement ITs extended
  to prove read-passes / write-denied for the wildcard token.
- **Out**: the tenant gate / layer-1 (`allowSuperAdminWildcard()` wiring — unchanged); auth-service
  token issuance / plane disjointness (must NOT mint `ROLE_SUPER_ADMIN` on the domain token — ADR-033
  S2 / ADR-034 U5); the entitlement-trust path (FIN-BE-048, unchanged); write-authority changes; new
  endpoints/contracts.

## Acceptance Criteria

- **AC-1**: a super-admin wildcard token (`tenant_id='*'`, NO finance scope, NO role,
  `entitled_domains=[]`) → the converter grants exactly `ROLE_FINANCE_SUPERADMIN_READ` (and no
  `SCOPE_*`, no VIEWER), in BOTH services.
- **AC-2**: same token → GET `/api/finance/**` PASSES the authorization gate (not 403), in BOTH services.
- **AC-3**: same token → POST/PUT/PATCH/DELETE `/api/finance/**` → **403 `PERMISSION_DENIED`** (write
  gate intact — `ROLE_FINANCE_SUPERADMIN_READ` is in `readAuthorities` only), in BOTH services.
- **AC-4**: the grant is strictly keyed on `tenant_id='*'` — a non-wildcard, non-entitled, no-scope
  token still gets no wildcard-read role and still 403s (the existing `unprivilegedFinanceTokenDenied`
  IT, `tenant_id=finance`, stays RED-on-read); the FIN-BE-048 entitlement path (`entitled_domains ∋
  finance` → `ROLE_FINANCE_VIEWER`) still works.
- **AC-5**: mutation-check — removing the `WILDCARD_TENANT → grant SUPERADMIN_READ` line turns the
  read-allowed assertion RED in each service; restored via Edit.
- **AC-6**: `:account-service:check` and `:ledger-service:check` GREEN.

## Related Specs

- `projects/finance-platform/apps/account-service` + `ledger-service` architecture (Hexagonal,
  ADR-MONO-012) — security layer in `infrastructure/security`.
- ADR-MONO-019 §D5 (SUPER_ADMIN wildcard, incident response) — the tenant gate's
  `allowSuperAdminWildcard()` rationale, now mirrored at the authority layer.
- ADR-033 S2 / ADR-034 U5 (admin/domain plane disjointness — why the fix is finance-side, not an
  auth-service `ROLE_SUPER_ADMIN` mint).
- **TASK-FIN-BE-048** (the entitlement-trust read straggler — this is its wildcard sibling, one axis
  over); TASK-FIN-BE-046 / TASK-FIN-BE-047 (the read-scope hardening that made finance a straggler);
  TASK-FIN-BE-006 (finance tenant-gate wildcard/entitlement pilot — layer-1 only).

## Related Contracts

- None changed. `iam-integration.md § Token 검증 규칙 #5` (downstream `finance.read`/`finance.write`
  enforcement) is unaffected — role/scope callers behave identically; only a wildcard super-admin
  token's READ visibility widens. No new error codes (`PERMISSION_DENIED` already registered).

## Edge Cases

- A `tenant_id` that is blank/absent → the converter already throws `IllegalStateException` before the
  wildcard check (unchanged); only the literal `"*"` grants the role.
- A wildcard token that ALSO carries a finance scope or operator role is unaffected — the wildcard-read
  role is additive and read-only; writes remain governed by scope/role.
- An entitlement-trust token (`entitled_domains ∋ finance`, non-wildcard tenant) gets
  `ROLE_FINANCE_VIEWER` but NOT `ROLE_FINANCE_SUPERADMIN_READ` — the two admission axes are distinct
  roles for audit separability.

## Failure Scenarios

- **Wildcard-read leaks into writes**: guarded by AC-3 + a dedicated write-403 assertion;
  `writeAuthorities` deliberately excludes `ROLE_FINANCE_SUPERADMIN_READ`. If a future edit adds it to
  writes, the write-403 IT goes RED.
- **Grant keyed on "authenticated" instead of the wildcard**: guarded by AC-4 (non-wildcard scopeless
  token → still 403); the grant is gated strictly on `tenant_id='*'`, exactly as the tenant gate's
  `allowSuperAdminWildcard()` gates on the same literal.

## Verification note

iam-platform **TASK-BE-518** (stays in `ready/`) closes only when BOTH the federation acme card
(already green, FIN-BE-048) AND the console super-admin card (this FIN-BE-049 + a nightly-e2e
confirmation) are green in CI post-merge. A doc-only sibling-parity audit for the OTHER wildcard-opening
domains (erp/scm/ecommerce) is tracked as **TASK-FIN-BE-050** (ready/, investigation-first, no measured
defect yet).
