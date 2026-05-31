# Task ID

TASK-MONO-161

# Title

ADR-MONO-019 entitlement-trust **authz-layer completion** — extend the per-domain **role/scope authorization** READ check (scm-procurement `RoleScope`-style `ActorContext` authz + erp-masterdata `RoleScopeAuthorizationAdapter`) to **dual-accept `entitled_domains`**, mirroring the tenant gate. Today the tenant gate (`TenantClaimValidator`/`TenantClaimEnforcer`) dual-accepts `entitled_domains`, but the **separate** role/scope authz layer requires a domain scope (`erp.read`) / domain role (`ERP_OPERATOR`/`SUPER_ADMIN`) that an entitlement-trust token does NOT carry — so a real-customer token **entitled** to scm/erp is still 403'd at the authz layer. This unblocks the TASK-MONO-158 (ADR-020 D4) federation-e2e **B-side** (globex switch → scm/erp must become entitled).

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **unblocks**: TASK-MONO-158 federation-e2e `tenant-switch-rescope.spec.ts` **B-side** (globex-corp [scm,erp] entitled). The D4 console mechanism is correct + proven (A-side passes; the globex assumed token correctly carries `tenant_id=globex-corp` + `entitled_domains=[scm,erp]` — confirmed via auth `injected entitled_domains=[erp, scm] for tenant_id=globex-corp` ×3 + account `listActive tenantId=[globex-corp] -> [erp, scm]` ×3). The B-side fails ONLY because scm/erp's role/scope authz layer rejects the entitled-but-no-domain-scope token.
- **completes**: ADR-MONO-019 § D5 entitlement-trust — step 3 wired the **tenant gate** dual-accept (`tenant_id==slug ∪ entitled_domains`) but NOT the **role/scope authz** layer. This task closes that gap (the first test of an scm/erp-**entitled** token PASSING scm/erp authz is the MONO-158 B-side — it surfaced the gap).
- **root cause (definitive, instrumented)**: scm-procurement (`application/ActorContext` + `infrastructure/security/*` + `presentation/.../advice` → 403 `PERMISSION_DENIED`) and erp-masterdata (`infrastructure/authorization/RoleScopeAuthorizationAdapter`: READ = `hasScope("erp.read") || hasScope("erp.write") || isOperator()`; `ActorContext.isOperator()` = `ERP_OPERATOR/ERP_ADMIN/SUPER_ADMIN` role) gate READ on a domain scope/role. The entitlement-trust assumed token has `entitled_domains=[scm,erp]` but NONE of those scopes/roles → `denyRole` (403). SUPER_ADMIN passes via the role; real-customer entitlement-trust tokens do not.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (cross-domain auth; entitlement-trust must stay fail-closed + WRITE unaffected).

---

# Goal

Make a signed-**`entitled_domains`** claim grant **READ** authorization at each domain's role/scope authz layer, exactly as it already grants passage at the tenant gate — so an operator viewing a customer they are entitled to (via the assume-tenant token) can READ that domain's data. **WRITE stays scope/role-gated** (entitlement-trust grants visibility, not mutation). Fail-closed preserved; net-zero for existing tokens (SUPER_ADMIN/scope-bearing tokens are unaffected; tokens with neither scope/role NOR entitlement are still denied).

## Principle (the dual-accept, now at the authz layer)

READ is authorized when **either**:
- (legacy) the actor has the domain scope/role (`erp.read`/`erp.write`/`ERP_OPERATOR`/`SUPER_ADMIN`; the scm equivalent), **or**
- (entitlement-trust) the signed `entitled_domains` claim contains this domain's key (`scm` / `erp`).

Reject READ only when **both** fail (fail-closed). `entitled_domains` is read only from the RS256/JWKS-verified token (unforgeable). This mirrors `TenantClaimValidator.isEntitled` / `TenantClaimEnforcer` already shipped at the tenant gate (SCM-BE-019 / ERP-BE-005).

---

# Scope

## In scope

1. **erp-masterdata** (`RoleScopeAuthorizationAdapter` + `ActorContext` + the JWT→ActorContext converter):
   - Thread `entitled_domains` into `ActorContext` (the JWT converter that builds `ActorContext` must extract the `entitled_domains` claim — a `List<String>` — and expose it, e.g. `Set<String> entitledDomains` + `boolean isEntitledTo(String domain)`, fail-closed on shape anomaly like `TenantClaimValidator.isEntitled`).
   - `RoleScopeAuthorizationAdapter.evaluate` **READ** branch: `... || actor.isOperator() || actor.isEntitledTo("erp")`. **WRITE unchanged**.
   - **Data-scope check**: for an entitlement-trust READ with no `targetDepartmentId` (the operator-overview list/summary path the BFF hits), the existing `targetDepartmentId == null → allow` already passes. If a targeted READ is reachable via the BFF, an entitled token with no `dataScopeDepartmentIds` must still resolve sensibly (entitlement-trust = read-all-visible for the entitled domain at the overview granularity) — preserve fail-closed for WRITE/targeted-mutation; keep READ overview working. Document the chosen handling.
2. **scm-procurement** (`application/ActorContext` + `infrastructure/security/ActorContextJwtAuthenticationConverter` + the authz check that yields 403 `PERMISSION_DENIED` + `infrastructure/security/SecurityConfig`):
   - Same shape: extract `entitled_domains` into `ActorContext`; the READ authorization that currently 403s an entitled-but-no-role token must dual-accept `isEntitledTo("scm")`. **Investigate the exact scm READ authz path the console-bff hits** (`scm-procurement-service:8080`, the endpoint behind `ScmInventoryReadAdapter`) and add the dual-accept there. WRITE unchanged.
3. **Verify wms + finance** (the MONO-158 A-side already passed finance/wms for the acme assumed token, so they likely already honor entitlement-trust OR have no role/scope READ gate): confirm by reading their authz layer. If they already accept (no change), record that; if they have the same gap, apply the same dual-accept. Do NOT change them speculatively — verify first.
4. **Tests** (per domain): unit test the authz READ dual-accept — an `entitled_domains ∋ <domain>` token with NO domain scope/role → READ **allowed**; a token with neither scope/role NOR entitlement → READ **denied** (fail-closed, net-zero); WRITE with entitlement-only → still **denied** (entitlement grants READ only). Keep existing authz tests GREEN (SUPER_ADMIN/scope tokens unaffected).
5. **Contracts/spec**: each touched service's `architecture.md` § Authorization — note the entitlement-trust dual-accept now applies at the role/scope authz READ layer (mirroring the tenant gate). If ADR-MONO-019 has an authz-layer note, add an additive clarification that entitlement-trust grants READ at the authz layer too (additive, HARDSTOP-04).

## Out of scope

- console-web / console-bff / the assume-tenant exchange (MONO-158/159/160) — all correct; the token already carries the right claims.
- WRITE/mutation authz — entitlement-trust grants READ visibility only; WRITE stays scope/role-gated.
- The tenant gate (`TenantClaimValidator`/`Enforcer`) — already dual-accepts; unchanged.

---

# Acceptance Criteria

- **AC-1 (erp)**: erp-masterdata READ authorizes a token with `entitled_domains ∋ "erp"` and no `erp.read`/role; WRITE still requires the scope/role. Unit-tested. Existing authz tests GREEN.
- **AC-2 (scm)**: scm-procurement READ (the endpoint the console-bff hits) authorizes a token with `entitled_domains ∋ "scm"` and no domain role/scope; WRITE unchanged. Unit-tested.
- **AC-3 (wms/finance verified)**: wms + finance authz READ layer confirmed to accept entitlement-trust (already, or via the same dual-accept). Recorded.
- **AC-4 (fail-closed + net-zero)**: a token with neither domain scope/role NOR `entitled_domains ∋ <domain>` → READ denied (403). SUPER_ADMIN/scope-bearing tokens unaffected (existing tests unmodified GREEN). `entitled_domains` read only from the verified JWT.
- **AC-5 (the B-side proof)**: re-running `federation-hardening-e2e.yml` → `tenant-switch-rescope.spec.ts` **SUCCESS** — switch to globex-corp → **scm/erp NOT forbidden** + finance/wms forbidden (the inverse of acme). All specs GREEN. Verified post-merge via `gh workflow run`.
- **AC-6**: per-domain CI Integration (Testcontainers) GREEN; 0 regression.

# Related Specs / Code

- `projects/erp-platform/apps/masterdata-service/.../infrastructure/authorization/RoleScopeAuthorizationAdapter.java` + `application/ActorContext.java` + the JWT→ActorContext converter.
- `projects/scm-platform/apps/procurement-service/.../application/ActorContext.java` + `infrastructure/security/ActorContextJwtAuthenticationConverter.java` + `SecurityConfig.java` + `presentation/advice/GlobalExceptionHandler.java`.
- The tenant-gate dual-accept reference: `scm-platform/.../procurement/infrastructure/security/TenantClaimValidator.isEntitled` + `erp` equivalent (the `isEntitled` helper to mirror).
- wms: `wms-platform/apps/*/.../TenantClaimValidator` + any authz adapter ; finance: `finance-platform/.../account-service` authz.
- ADR-MONO-019 § D5 (entitlement-trust) ; `rules/traits/multi-tenant.md` M1-M7.
- The e2e proof: `tests/federation-hardening-e2e/specs/tenant-switch-rescope.spec.ts` (MONO-158).

# Edge Cases / Failure Scenarios

- **WRITE must NOT be widened** by entitlement-trust — only READ. An entitlement-only token attempting WRITE → still 403.
- **claim-shape fail-closed**: `entitled_domains` absent/non-list/non-string-element → `isEntitledTo` false (no NPE, no blanket trust) — mirror `TenantClaimValidator.isEntitled`.
- **net-zero**: existing SUPER_ADMIN (`*`/role) + scope-bearing + client_credentials machine tokens authorize exactly as before (the dual-accept only ADDS an OR branch).
- **data-scope (erp)**: the overview READ path has no `targetDepartmentId` → the existing `null → allow` covers it; do not loosen the targeted data-scope check for entitlement-trust beyond READ-overview.
- **meta**: this gap existed because step-3 (SCM-BE-019/ERP-BE-005) wired entitlement-trust ONLY at the tenant gate; no test had an scm/erp-**entitled** token try to PASS scm/erp until the MONO-158 B-side. **When adding entitlement-trust to a domain, BOTH the tenant gate AND the role/scope authz layer must dual-accept** — they are independent gates.

# Notes

- ADR-019 entitlement-trust authz-layer completion; unblocks ADR-020 D4 (MONO-158) B-side. federation-e2e is workflow_dispatch/nightly (not PR-gated) — AC-5 verified post-merge via `gh workflow run federation-hardening-e2e.yml`.
- After this lands GREEN, the MONO-158/159/160 + this task together complete the ADR-020 D4 A↔B switch proof; close them per lifecycle.
