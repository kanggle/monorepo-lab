# TASK-BE-517 — SUPER_ADMIN wildcard `*` entitled-domains/roles lookup 400s and trips the auth→account circuit → tokens omit entitlements → finance card `forbidden`

- **Type**: TASK-BE
- **Status**: ready
- **Service**: auth-service (iam-platform)
- **Domain/traits**: saas / [transactional, integration-heavy, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (token-minting + cross-tenant circuit blast radius)
- **Supersedes**: `TASK-PC-BE-013` (misdiagnosed — the finance card `forbidden` is NOT a console credential/token-wiring or #569 issue; three prior investigations chased that dead end before the runtime root was pinned from CI logs).

## Goal

Fix the federation `entitlement-trust-crossdomain:66/120/127` and console `operators-profile:60` finance-card
`forbidden` — the last remaining RED after TASK-BE-515 (which unmasked it) and TASK-BE-516. Root cause was
pinned from the CI federation logs (not static analysis), correcting three prior misdiagnoses.

## AC-0 — Finding (runtime-verified from CI federation run 29627594218, 2026-07-18)

The full causal chain, each link evidenced in the run logs:

```
finance card forbidden
  ← acme-operator token omits the entitled_domains claim (fail-soft)
  ← auth-service → IAM account-service circuit is OPEN (CallNotPermittedException ×20)
  ← token-minting lookups return 400 BAD_REQUEST:
      GET /internal/tenants/*/entitled-domains        → {"code":"VALIDATION_ERROR","message":"Invalid tenant_id: *"}
      GET /internal/tenants/*/accounts/{aid}/roles     → {"code":"VALIDATION_ERROR","message":"Invalid tenant_id: *"}
```

- **The 400 is `Invalid tenant_id: *`.** account-service's `TenantId` value object rejects the platform
  wildcard `*` (`account-service/.../domain/tenant/TenantId.java:44` → `IllegalArgumentException` → 400
  VALIDATION_ERROR). auth-service mints the SUPER_ADMIN token with `tenant_id='*'` and calls
  `listEntitledDomains("*")` + `listAccountRoles("*", …)` — both 400.
- **The 400 opens the shared circuit.** `AccountServiceClient` wraps the lookups in
  `ResilienceClientFactory.buildCircuitBreaker("accountService")`; the raw `HttpClientErrorException`
  (4xx) propagates through the circuit decorator (the 4xx→`AccountServiceUnavailableException`
  conversion happens *after*, in the catch), so the circuit **counts the 400s** → opens (50% rate, min 5).
- **Collateral: OTHER tenants' lookups fail-soft.** With the circuit open, the *next* token minting —
  the **acme-operator** (`tenant_id=acme-corp`, a valid tenant whose lookup would 200) — gets
  `CallNotPermittedException` → `listEntitledDomains` fail-softs (`AccountServiceUnavailableException`,
  `TenantClaimTokenCustomizer` omits the claim). So acme's token has **no `entitled_domains`**, the finance
  tenant gate (`trustEntitledDomains`) rejects → **403 forbidden**. The seeds are correct
  (`acme-corp+finance ACTIVE` in account-service `V0020`); the token simply never receives them.
- **Why it survived BE-515:** before BE-515 these lookups 401'd (missing `internal.invoke`); BE-515 fixed
  the auth, revealing the *next* layer — the `*` request is itself invalid (400). Onion, not regression.
- **Not the console credential/#569 axis:** the finance leg correctly uses the domain-facing OIDC token
  (`CredentialSelectionAdapter` FINANCE → `IamOidcAccessToken`), and the operator token would be rejected
  by finance anyway (`iss=admin-service` ∉ finance issuer allow-list). TASK-PC-BE-013's premise is void.
- **Adjacent, out of scope:** a separate `Data too long for column 'account_id'` (VARCHAR(36)) on
  auth-service's `SAS_SYNC` refresh-token domain-store write (`account=<email>`, ~40 chars) — unrelated to
  finance; note-only.

## Scope

- **In**: `auth-service` `AccountServiceClient.listEntitledDomains` / `listAccountRoles` short-circuit the
  platform wildcard `*` — return empty **without** a network call. The wildcard is not a real tenant
  (account-service rejects it) and SUPER_ADMIN has no per-tenant entitlements/roles (it passes every gate
  via the wildcard), so the lookup is meaningless. This removes the 400s that open the circuit, so
  real-tenant lookups in the same window are no longer collateral-damaged.
- **Out**:
  - The shared-circuit **4xx-should-not-open-the-circuit asymmetry** (`libs/java-common`
    `ResilienceClientFactory.standardCircuitBreakerConfig()` ignores nothing, while its retry config
    already `ignoreExceptions(HttpClientErrorException.class)`). Fixing that would harden the whole fleet
    against *any* 4xx tripping the circuit (BE-516 sibling-parity, but on the programmatic shared factory),
    but it is a **shared-library / monorepo-level change** with cross-project blast radius → separate root
    task. This task's `*`-skip removes the *specific* 400s and fixes the finance RED on its own.
  - The `account_id` VARCHAR(36) refresh-token truncation (separate bug, note-only above).
  - console credential/#569 changes (TASK-PC-BE-013 premise — void).

## Acceptance Criteria

- **AC-1**: `AccountServiceClient.listEntitledDomains("*")` and `listAccountRoles("*", …)` return an empty
  list and make **no** HTTP call to account-service (verified: WireMock receives zero requests).
- **AC-2**: a non-`*` tenant lookup still calls account-service (existing happy-path tests stay green —
  the guard is wildcard-specific).
- **AC-3**: auth-service fast lane green.
- **AC-4** *(CI-authoritative, post-merge)*: the Federation Hardening E2E `entitlement-trust-crossdomain`
  finance/wms cards resolve to `ok` (not `forbidden`), and the console `operators-profile` finance card is
  `ok`. Recorded in the close-chore. (SUPER_ADMIN behaviour is unchanged — its `*` lookups already
  fail-softed to empty; they now skip the network + circuit instead.)

## Related Specs / Contracts

- `projects/iam-platform/apps/account-service/.../domain/tenant/TenantId.java` (the `*` rejection)
- `projects/iam-platform/apps/auth-service/.../infrastructure/oauth2/TenantClaimTokenCustomizer.java` (fail-soft omit)
- `libs/java-common/.../resilience/ResilienceClientFactory.java` (the shared circuit — retry ignores 4xx, circuit does not; § Out follow-up)
- Predecessors: `TASK-BE-515` (unmasked login), `TASK-BE-516` (admin-service circuit sibling)

## Edge Cases

- `*` is the only tenant value account-service rejects with 400; real slugs (`gap`, `acme-corp`) are valid
  and still looked up. Guard matches `"*"` exactly.
- SUPER_ADMIN token: `entitled_domains`/`roles` from this path were already empty (400 → fail-soft); the
  skip is behaviour-preserving for `*` while eliminating the circuit damage to other tenants.
- If a future caller passes `*` for a legitimately-looked-up value, the guard returns empty — correct,
  since account-service would 400 it anyway.

## Failure Scenarios

- Guard only added to one of the two lookups → the other still 400s and trips the circuit. Mitigated:
  AC-1 covers both `listEntitledDomains` and `listAccountRoles`.
- The shared-circuit 4xx asymmetry remains → a *different* real 4xx (e.g. a genuine 404) could still open
  the circuit and cascade. Mitigated for finance by removing the `*` source; the general hardening is the
  § Out monorepo follow-up.
