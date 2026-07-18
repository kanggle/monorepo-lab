# TASK-BE-518 — finance overview card still `forbidden` after TASK-BE-517 (a deeper layer under the circuit-open fix) — INVESTIGATION

- **Type**: TASK-BE (investigation; likely resolves into iam-platform account-service data/ceiling OR finance-platform gate OR a federation/console e2e seed fix — reassign after AC-0)
- **Status**: ready
- **Service**: TBD by AC-0 — candidates: auth-service/account-service (iam-platform, entitled_domains issuance), finance-platform gateway/account-service, or the federation/console e2e fixtures
- **Domain/traits**: saas / [transactional, integration-heavy, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (multi-service token/gate + e2e seed)
- **⚠️ INVESTIGATION-FIRST**: three prior investigations misdiagnosed this exact surface. AC-0 MUST pin the current cause with **runtime evidence**, not static seed analysis. Do not trust `.last-run.json`.

## Goal

Clear the finance overview card `forbidden` that **persists after TASK-BE-517** — the last RED of the
2026-07-18 "verify all services" sweep. BE-515/516/517 each removed a real, verified layer of this
onion, but the surface RED remains, so at least one more independent cause is stacked underneath.

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
