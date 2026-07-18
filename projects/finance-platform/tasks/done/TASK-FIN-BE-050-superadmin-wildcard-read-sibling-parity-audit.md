# TASK-FIN-BE-050 — super-admin wildcard READ authority: sibling-parity audit (erp / scm / ecommerce) — INVESTIGATION

- **Type**: TASK-FIN-BE (doc-only investigation; likely resolves into per-domain fix tasks after AC-0 — reassign each to its own project after runtime pinning)
- **Status**: done
- **Service**: TBD by AC-0 — the domain services that ALSO open the tenant-layer wildcard via
  `.allowSuperAdminWildcard()`: erp (read-model / notification / masterdata), scm, ecommerce.
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (multi-service authorization audit)
- **⚠️ INVESTIGATION-FIRST**: no measured defect yet. AC-0 MUST pin each service with **runtime
  evidence** (a live wildcard-token request + the observed status/reason), NOT static seed/grep
  analysis. The finance straggler (FIN-BE-048/049) was runtime-confirmed; static code alone misled
  three prior investigations on the adjacent finance surface.

## Goal

Audit whether the OTHER domain services that open the tenant-layer wildcard the same way finance did —
via `.allowSuperAdminWildcard()` on their `TenantClaimValidator` / `TenantClaimEnforcer` — **reject a
bare platform super-admin wildcard token (`tenant_id='*'`, no scope, no domain role,
`entitled_domains=[]`) at their read-authority layer** the same way finance did before FIN-BE-049. If a
sibling opens the wildcard at layer-1 (the tenant gate) but its layer-2 read-authority list admits none
of what the bare super-admin token carries, it is the same straggler finance was — a defect, even if no
e2e currently exercises it.

**Why this is a sibling-parity sweep, not a hunch** (memory `project_enforcement_straggler_sibling_parity`):
finance opened the wildcard at the tenant layer (FIN-BE-006) and, when the read-scope hardening landed
(FIN-BE-046/047), became a read straggler that FIN-BE-048 (entitlement) + FIN-BE-049 (wildcard) closed.
Per `libs/java-security` `TenantClaimValidator`'s own per-domain table, **scm, erp, and ecommerce also
run `allowSuperAdminWildcard()`** (wms does NOT; fan does but sits outside the entitlement plane). Each
of those is a candidate for the identical layer-1-opens / layer-2-rejects asymmetry. Read one service,
don't generalise — line the siblings up and check each independently. Harvest refutations too (a
sibling that grants a wildcard-read role, or whose read gate is `.authenticated()`-only, is NOT a
straggler).

## Why it is untested today (the reason there is no measured defect)

The console e2e's overview cards for erp/scm/ecommerce are wired to dead-ends for the super-admin
persona (the console composition short-circuits or the leg is not exercised for `tenant_id='*'`), so no
current spec drives a bare super-admin wildcard token at those services' read endpoints. Absence of a
RED is therefore NOT evidence of correctness — it is evidence of no coverage. AC-0 must MANUFACTURE the
runtime probe (a signed `tenant_id='*'` token against each service's read endpoint), not infer from the
green suite.

## Note on erp — different mechanism, own investigation

erp does NOT use the finance `SecurityConfig.readAuthorities` shape. Its read authorization runs through
a **domain-layer** mechanism (`ReadAuthorizationGate` / `RoleScopeAuthorizationAdapter` in
read-model / notification / masterdata), so the finance fix pattern (a converter-synthesised wildcard
role added to `readAuthorities`) may not transplant directly. erp needs its own investigation of where
the wildcard token's read verdict is decided and whether that layer admits a bare super-admin wildcard.
Pin erp separately from scm/ecommerce.

## Acceptance Criteria (AC-0 gates the rest)

- **AC-0 (per service, runtime-pinned)**: for each of erp (read-model / notification / masterdata), scm,
  and ecommerce, pin — with a live `tenant_id='*'` (no scope / no role / `entitled_domains=[]`) token
  against a read endpoint — whether it PASSES or 403s at the read-authority layer, and record the exact
  status + reason. INVESTIGATION-FIRST: do not trust static grep; do not trust the green e2e suite.
- **AC-1**: for each service pinned as a straggler (layer-1 opens the wildcard, layer-2 rejects the bare
  super-admin token), file a per-domain fix task mirroring FIN-BE-049 (a READ-only wildcard authority
  keyed strictly on `tenant_id='*'`, added to the read gate only, writes stay gated). For erp, the fix
  task must account for the domain-layer mechanism.
- **AC-2**: refutations recorded explicitly (siblings that already admit the wildcard-read, or whose
  read gate does not depend on scope/role, are NOT stragglers — note why).

## Related

- **TASK-FIN-BE-048** (finance entitlement-trust read straggler), **TASK-FIN-BE-049** (finance
  platform-wildcard read straggler — the pattern this audit checks the siblings against).
- `libs/java-security` `TenantClaimValidator` — the per-domain wildcard/entitlement policy table
  (which domains run `allowSuperAdminWildcard()`).
- ADR-MONO-019 §D5 (SUPER_ADMIN wildcard incident-response rationale); ADR-033 S2 / ADR-034 U5
  (admin/domain plane disjointness — why fixes are domain-side, not an auth-service role mint).
- Memory: `project_enforcement_straggler_sibling_parity` ("X 강제되나?" = 형제 서비스 줄 세우기 —
  read siblings side by side, an N-1-wired shared mechanism with 1 unwired = a straggler = a defect;
  infra bean/table existence ≠ mechanism active; green CI can mean the tests bypass the enforcement
  layer). `project_finance_forbidden_onion_sweep_2026_07_18` (the anti-patterns: static-seed over-trust,
  stale `.last-run.json`, runtime evidence wins).

## Edge Cases / Failure Scenarios

- A sibling may open the wildcard at layer-1 but ALSO admit it at layer-2 (e.g. its read gate is
  `.authenticated()`-only, pre-hardening) — that is NOT a straggler; record the refutation.
- erp's domain-layer authorization may decide the verdict in a place the finance pattern doesn't map
  onto — do not force the finance fix shape; pin the actual decision point first.
- Static analysis may say "the wildcard is admitted" while runtime rejects (or vice-versa) — the live
  probe is authoritative, not the grep.
