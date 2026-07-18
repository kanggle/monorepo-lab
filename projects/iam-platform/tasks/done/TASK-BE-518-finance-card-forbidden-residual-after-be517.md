# TASK-BE-518 — finance overview card still `forbidden` after TASK-BE-517 (a deeper layer under the circuit-open fix) — INVESTIGATION

- **Type**: TASK-BE (investigation; likely resolves into iam-platform account-service data/ceiling OR finance-platform gate OR a federation/console e2e seed fix — reassign after AC-0)
- **Status**: done
- **Service**: TBD by AC-0 — candidates: auth-service/account-service (iam-platform, entitled_domains issuance), finance-platform gateway/account-service, or the federation/console e2e fixtures
- **Domain/traits**: saas / [transactional, integration-heavy, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (multi-service token/gate + e2e seed)
- **⚠️ INVESTIGATION-FIRST**: three prior investigations misdiagnosed this exact surface. AC-0 MUST pin the current cause with **runtime evidence**, not static seed analysis. Do not trust `.last-run.json`.

## Goal

Clear the finance overview card `forbidden` that **persists after TASK-BE-517** — the last RED of the
2026-07-18 "verify all services" sweep. BE-515/516/517 each removed a real, verified layer of this
onion, but the surface RED remains, so at least one more independent cause is stacked underneath.

## AC-0 RESULT (runtime-confirmed) — federation acme persona: candidate 2 (finance-side authority gate)

Pinned from federation run `29632072800` (DIAG logging, since discarded). The **federation acme**
persona's cause is **candidate 2** (a finance-side gate rejects for a reason other than the tenant
claim — specifically the **scope/role authority layer**, NOT the tenant gate):

- acme operator's OIDC domain-facing token: `tenant_id=acme-corp`, `entitled_domains=[finance, wms]`,
  `scope=[openid,profile,email,tenant.read]`, **NO roles** (`resolvedRoles=[]`). So the token minting is
  correct (candidate 1 refuted for this persona — `entitled_domains` DOES contain `finance` at runtime).
- console-bff finance leg: `financeDefaultAccountId present=true` → HTTP GET made → finance returned
  **HTTP 403 `{"code":"PERMISSION_DENIED"}`** (candidate 3 / MISSING_PREREQUISITE refuted — it is a real
  403, not a missing-account short-circuit).
- Same token PASSES the WMS leg (admin-service grants `ROLE_WMS_VIEWER` on `entitled_domains ∋ wms`);
  finance had no equivalent authority-layer grant.

**Root cause:** finance `account-service` + `ledger-service` `SecurityConfig` `readAuthorities` require a
finance scope or an operator role; the entitled-but-scopeless base token holds neither, so it 403s at
**layer-2 (authorization)** despite PASSING **layer-1** (the finance tenant gate admits it via
`trustEntitledDomains()`). Finance applied entitlement-trust at the tenant layer only (FIN-BE-006) but
never at the authority layer — a straggler behind the WMS `ROLE_WMS_VIEWER` pattern (TASK-MONO-162).

**Fix = `TASK-FIN-BE-048`** (finance-platform): synthesise a READ-only `ROLE_FINANCE_VIEWER` on
`entitled_domains ∋ finance` in both finance converters + add it to `readAuthorities` only (writes stay
gated). **This BE-518 closes only when the federation `entitlement-trust-crossdomain` finance card goes
green in CI post-merge of FIN-BE-048.** The **console super-admin** (`tenant_id=*`) persona of
`operators-profile.spec.ts:60` is NOT covered by FIN-BE-048 (wildcard, not entitlement) — pin it
separately before closing AC-2 for that spec.

## AC-0 RESULT — super-admin persona: finance-side **wildcard-read** authority straggler (`TASK-FIN-BE-049`)

Pinned from **nightly-e2e run `29635409302`** against main `de8edcae4` (Playwright trace of the console
`/dashboards/overview` RSC payload for the `operators-profile.spec.ts` super-admin persona):

```
"domain":"finance","status":"forbidden","reason":"PERMISSION_DENIED"
```

A real **403 at the authorization layer** — NOT `TENANT_FORBIDDEN` (tenant gate opened), NOT
`MISSING_PREREQUISITE` (candidate 3 refuted). The cause is the **wildcard sibling** of the acme
persona's straggler, one axis over from FIN-BE-048:

- The super-admin's base OIDC domain-facing token carries `tenant_id='*'`, `scope=[openid,profile,email,
  tenant.read]` (**no finance scope**), **no domain roles** (its `SUPER_ADMIN` is an
  `admin_db.admin_operator_roles` / ADMIN-plane entry, DELIBERATELY kept off the domain-facing token per
  **ADR-033 S2 / ADR-034 U5** disjointness — so the token has NO `ROLE_SUPER_ADMIN`), and
  `entitled_domains=[]`.
- finance `account-service`+`ledger-service` `ServiceLevelOAuth2Config` opens the tenant gate for this
  token via `.allowSuperAdminWildcard()` (**layer-1 passes**), but `SecurityConfig.readAuthorities`
  (tightened by FIN-BE-046/047) admits none of what the bare wildcard token carries → **layer-2 403s**.
- FIN-BE-048 (entitlement, `entitled_domains ∋ finance`) does NOT cover this persona (`entitled_domains`
  is empty; the wildcard is a different axis). The fix must NOT mint `ROLE_SUPER_ADMIN` at auth-service
  (breaches ADR-033/034 plane disjointness) — it is finance-side.

**Fix = `TASK-FIN-BE-049`** (finance-platform): synthesise a READ-only `ROLE_FINANCE_SUPERADMIN_READ`
keyed strictly on `tenant_id='*'` in both finance converters + add it to `readAuthorities` only (writes
stay gated) — the authority-layer analogue of the tenant gate's `allowSuperAdminWildcard()`.

**BE-518 closes when BOTH cards are green post-merge:** (a) the federation `entitlement-trust-crossdomain`
finance/wms acme card (already green via FIN-BE-048), AND (b) the console `operators-profile` super-admin
finance card (via FIN-BE-049 + a nightly-e2e confirmation run). A doc-only sibling-parity audit for the
OTHER wildcard-opening domains (erp/scm/ecommerce) is tracked as **TASK-FIN-BE-050** (ready/,
investigation-first).

## AC-0 — Finding so far (runtime-verified; the cause below is NOT yet pinned)

- **Failing specs:** federation `entitlement-trust-crossdomain.spec.ts:66/120/127` (acme-operator,
  `tenant_id=acme-corp`, asserts finance/wms NOT forbidden) and — earlier in the sweep — console
  `operators-profile.spec.ts:60` (super-admin, `tenant_id=*`). These personas differ, so the two may
  have **different** causes; pin each.
- **What BE-517 already removed (verified in federation run `29630219536`, head `fd4d0004e`):** the
  `Invalid tenant_id: *` 400s, the `entitled_domains lookup failed` warnings, and the
  `CallNotPermittedException` (circuit-open) are **all gone**. So token minting no longer fails on the
  lookup — yet the acme finance card is **still `forbidden` (403)**.
- **⇒ The residual is a NEW layer**, not the circuit/lookup one. The token minting now succeeds; finance
  still rejects. **Candidates (each to confirm or refute with runtime evidence):**
  1. the acme entitled-domains lookup now **returns without `finance`** — e.g. account-service
     `V0020` acme-corp+finance subscription not ACTIVE in the *federation* compose, or the org-node
     `effectiveCeiling(acme-corp)` clips finance out of `ACTIVE ∩ ceiling`;
  2. a **finance-side gate** rejects for a reason other than the tenant claim — scope/role
     (`SecurityConfig`) or the tenant gate (`TenantClaimValidator.forTenant("finance")`);
  3. the finance leg short-circuits to `MISSING_PREREQUISITE` (finance card also renders `forbidden`
     for a **missing `finance_default_account_id`**, not only for a 403 — the reason string is the only
     A-vs-B discriminator, per BE-516's finding; the console super-admin persona is the likely
     candidate here).
- **What is already refuted (do NOT re-chase):** it is **not** the console credential / #569 axis
  (`CredentialSelectionAdapter` FINANCE → OIDC domain-facing token is correct; the operator/admin token
  would be rejected by finance's issuer allow-list anyway — the void `TASK-PC-BE-013`); it is **not** the
  BE-517 circuit-open layer (removed, verified).

## Scope

- **In (AC-0 investigation)**: pin the current finance-403 cause for **each** failing spec with runtime
  evidence — decode the acme/super-admin token's actual `entitled_domains`/`roles`/`scope`/`tenant_id`,
  and the finance side's exact rejection (tenant vs scope vs MISSING_PREREQUISITE). Then implement the
  #569-compliant fix the evidence dictates (data/seed/ceiling or finance-gate), NOT a credential rewire.
- **Out**: credential-routing / #569 changes (refuted); the shared-circuit 4xx hardening (`TASK-MONO-427`).

## How to get the runtime evidence (the CI dump logs are insufficient)

The `Dump docker compose logs on failure` step does **not** contain the finance-403 reason or the token
claims. Options, in order of decisiveness:
1. **Local repro** of the federation stack (`docker-compose.federation-e2e.yml` + `.replenishment.yml`),
   log in as `acme-operator`, decode the minted token (`entitled_domains`?), and hit the finance balance
   endpoint directly to read the 403 body (`tenant_mismatch` vs scope vs MISSING_PREREQUISITE).
2. **Add targeted debug logging** (temporary, or a permanent DEBUG line) in
   `TenantClaimTokenCustomizer.populateEntitledDomains` (what the lookup returned) and the console-bff
   `OperatorOverviewCompositionUseCase.callFinance` (the `LegOutcome` reason), then re-run the E2E.
3. Query the federation account-service DB for `acme-corp` subscriptions + `effectiveCeiling` to confirm
   candidate 1.

## Acceptance Criteria (draft — finalize after AC-0)

- **AC-0**: the current finance-403 root cause is pinned with runtime evidence for both the federation
  (acme) and console (super-admin) specs — A (MISSING_PREREQUISITE) vs B (403: tenant/scope/role) vs the
  entitled-domains-returns-without-finance sub-case — and recorded.
- **AC-1**: the #569-compliant fix the evidence dictates is applied (data/seed/ceiling or finance-gate;
  the finance leg keeps using the OIDC domain-facing token).
- **AC-2**: federation `entitlement-trust-crossdomain` finance/wms cards → `ok`; console
  `operators-profile` finance card → `ok` (CI-authoritative, post-merge).

## Related

- Predecessors (all merged 2026-07-18): `TASK-BE-515`, `TASK-BE-516`, `TASK-BE-517` (this residual is
  the layer under BE-517). Superseded/void: `TASK-PC-BE-013`. Follow-up: `TASK-MONO-427`.
- Memory: `project_finance_forbidden_onion_sweep_2026_07_18` (the onion + the anti-patterns:
  stale `.last-run.json`, static-seed over-trust, sentinel-to-circuit).

## Edge Cases / Failure Scenarios

- The two specs may have **different** roots (super-admin wildcard vs acme entitled-domains) — pin each;
  a fix for one may not clear the other.
- Static seed analysis says the data is correct (acme+finance in `V0020`) — but a prior investigation was
  misled by exactly this (the token never received it at runtime). **Trust the decoded token + the live
  403, not the seed file.**
- `.last-run.json` shows `passed` from a stale local run — **ignore it**; the authoritative signal is the
  CI federation/nightly run.
