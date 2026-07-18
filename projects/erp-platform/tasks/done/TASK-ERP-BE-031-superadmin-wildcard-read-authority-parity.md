# TASK-ERP-BE-031 — super-admin wildcard READ authority parity (masterdata + read-model + notification)

- **Type**: TASK-ERP-BE (backend — Spring Security authorization gate)
- **Status**: done
- **Service**: erp-platform `masterdata-service` + `read-model-service` + `notification-service` (shared READ-gate predicate)
- **Domain/traits**: saas / [transactional]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (security-critical authorization gate)

## Goal

Close the erp-side authority-layer straggler that leaves a **platform super-admin** (the `platform-console`
super-admin persona, `tenant_id='*'`) with a `forbidden` erp overview card. This is the **erp analogue of
finance FIN-BE-048/049**, surfaced by the sibling-parity audit **TASK-FIN-BE-050** (the doc-only audit that
FIN-BE-049 spun off to check the OTHER wildcard-opening domains — erp/scm/ecommerce). Grant a READ-only
wildcard admission to a token whose `tenant_id` claim equals the platform wildcard `"*"`, so its READS pass
the erp layer-2 domain read gates — while WRITES stay gated on `erp.write` scope / an operator role. This is
the **authority-layer analogue of the tenant gate's `allowSuperAdminWildcard()`**, mirroring FIN-BE-049.

## Runtime token profile (the persona that 403s)

A platform-console super-admin opening the operator-overview forwards the operator's **base OIDC
domain-facing token**:

- `tenant_id='*'` (platform wildcard);
- `scope = openid profile email tenant.read` — **NO `erp.read`/`erp.write` scope**;
- **NO domain roles claim** — the admin-plane `SUPER_ADMIN` is deliberately kept OFF the domain token per
  **ADR-033 S2 / ADR-034 U5** (admin/domain plane disjointness);
- `entitled_domains=[]`.

This token passes erp's **layer-1 tenant gate** (`ServiceLevelOAuth2Config` wires
`.allowSuperAdminWildcard()`, so `tenant_id='*'` is admitted) but is **403'd at layer-2** by the erp domain
read gates: each requires `erp.read` ∨ `erp.write` scope ∨ `isOperator()` ∨ `isEntitledTo("erp")`. None hold
→ `PERMISSION_DENIED` 403 → the super-admin's erp overview card is `forbidden`. Note the gates also test a
`SUPER_ADMIN` **role in the token**, which ADR-033/034 keeps OFF the domain token — so that branch is **dead**
for the real super-admin persona; the correct key is the wildcard `tenant_id`, exactly as FIN-BE-049 keyed
finance.

## Straggler finding — 3 erp services share the same READ-gate predicate

1. **masterdata-service** — `RoleScopeAuthorizationAdapter` READ arm of `evaluate(...)`. Fired by
   `MasterdataApplicationService.listDepartments` via `authorize(actor, RequiredScope.READ, null)` — this is
   the service the operator-overview ERP card actually calls (`GET /api/erp/masterdata/departments`). Has an
   explicit READ/WRITE arm split.
2. **read-model-service** — `ReadAuthorizationGate.requireRead`. **READ-only gate** (E5: no mutating
   endpoints).
3. **notification-service** — `ReadAuthorizationGate.requireRead`. **READ-only gate**; the sole POST
   (mark-read) is a self-scoped, recipient-owned read-adjacent action (no org-wide notification mutation).

## The fix (READ-only wildcard short-circuit — the erp-mechanism analogue of FIN-BE-049)

For EACH gate, add a wildcard-tenant short-circuit to the **READ admission ONLY**: a super-admin wildcard
token (`tenant_id='*'`) is admitted for READS. The wildcard literal comes from the shared constant the tenant
gate already gates on — `TenantClaimValidator.WILDCARD_TENANT` (`"*"`), read via `actor.tenantId()`
(masterdata) or `jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID)` (read-model / notification). No
hardcoded `"*"`.

- **masterdata**: added `|| TenantClaimValidator.WILDCARD_TENANT.equals(actor.tenantId())` to the `case READ`
  arm of the `roleOk` switch. The `case WRITE` arm is untouched.
- **read-model / notification**: added `boolean superAdminWildcard = WILDCARD_TENANT.equals(...)` and OR'd it
  into the single READ predicate. These gates have **no WRITE arm** (structurally READ-only), so the invariant
  is doubly satisfied.

## READ-only invariant (identical to FIN-BE-049)

Widens READ visibility only, never mutation. A wildcard super-admin token must still be **DENIED on writes**.
For masterdata this is a live assertion (`RequiredScope.WRITE` still `DENY_ROLE`); for read-model /
notification it is structural (no write path through the gate).

## Acceptance Criteria

- **AC-1**: a super-admin wildcard token (`tenant_id='*'`, NO erp scope, NO role, `entitled_domains=[]`) →
  READ **admitted** in all 3 gates.
- **AC-2**: same token → masterdata `RequiredScope.WRITE` → **`DENY_ROLE`** (write arm intact); read-model /
  notification have no write path.
- **AC-3**: the admission is strictly keyed on `tenant_id='*'` — a non-wildcard, non-entitled, no-scope token
  still 403s in all 3 gates (guards against keying on "authenticated").
- **AC-4**: RED-before/GREEN-after proof for the masterdata gate (converts the static audit verdict to
  runtime proof); mutation-check per service (remove the short-circuit → READ assertion RED; restore via Edit
  → GREEN).
- **AC-5**: `:masterdata-service:check`, `:read-model-service:check`, `:notification-service:check` GREEN.

## Related Specs

- `projects/erp-platform/specs/services/{masterdata-service,read-model-service,notification-service}/architecture.md`
  — E6 authorization gate (fail-closed).
- ADR-MONO-019 §D5 (SUPER_ADMIN wildcard, incident response) — the tenant gate's `allowSuperAdminWildcard()`
  rationale, now mirrored at the authority layer.
- ADR-033 S2 / ADR-034 U5 (admin/domain plane disjointness — why the fix is erp-side, not an auth-service
  `SUPER_ADMIN` mint on the domain token).
- **TASK-FIN-BE-049** (finance wildcard READ authority — the precedent this mirrors); **TASK-FIN-BE-048** (the
  finance entitlement-trust read sibling); **TASK-FIN-BE-050** (the sibling-parity audit that flagged erp).
- **TASK-ERP-BE-029** (the erp entitlement-trust / machine-token read work on these same gates).

## Related Contracts

- None changed. Role/scope callers behave identically; only a wildcard super-admin token's READ visibility
  widens. No new error codes (`PERMISSION_DENIED` already registered).

## Edge Cases

- A wildcard token doing a **targeted** masterdata read (non-null `targetDepartmentId`) still hits the
  data-scope check (`isPlatformScope()` / subtree containment) — the wildcard admission widens the role check
  only, not data-scope, exactly like the existing entitlement-trust path. The operator-overview card uses a
  null target, so it is admitted.
- A wildcard token that ALSO carries an erp scope or operator role is unaffected — the admission is additive
  and read-only.
- A blank/absent `tenant_id` → not equal to `"*"` → no admission (fail-closed).

## Failure Scenarios

- **Wildcard-read leaks into writes**: guarded by the masterdata write-denied test; the short-circuit is in
  the `case READ` arm only. If a future edit moves it to the WRITE arm, the write-denied test goes RED.
- **Grant keyed on "authenticated" instead of the wildcard**: guarded by the non-wildcard scopeless-denied
  test in each gate; admission is gated strictly on `tenant_id='*'`, the same literal
  `allowSuperAdminWildcard()` gates on.

## Verification note

Audit provenance: **TASK-FIN-BE-050** (sibling-parity audit). erp is confirmed a straggler by the same
predicate as finance FIN-BE-049. The RED-before/GREEN-after masterdata proof + per-service mutation-check
convert the static audit verdict to runtime proof. scm/ecommerce (the other domains FIN-BE-050 flags) remain
separate follow-ups.
