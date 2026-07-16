# TASK-BE-514 — account-service `/internal/**` accepts any valid-issuer token (system-credential contract unenforced)

- **Type**: TASK-BE
- **Status**: done
- **Service**: account-service (iam-platform)
- **Domain/traits**: saas / [transactional, regulated, audit-heavy, integration-heavy, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (security authz + multi-tenant blast radius)

## Goal

Close the shared-issuer authorization gap on account-service `/internal/**`: the endpoints promise a
"GAP `client_credentials` system credential only" contract, but the resource-server chain pins only
signature + timestamps + issuer + `.authenticated()`. Because the **same** IAM `auth-service` SAS
issues both system (`client_credentials`) and user (`authorization_code`) tokens, `.authenticated()`
does **not** distinguish a system credential from an ordinary user/CUSTOMER token — so any valid
token from the shared issuer passes. Enforce a **claim discriminator**: require the `internal.invoke`
scope (the workload scope seeded for exactly these callers in `V0019`, whose enforcement was the
declared-but-never-shipped `TASK-BE-319` hardening) as a decoder-level validator → fail-closed 401.

This is the IAM sibling of the ecommerce order-service fix **TASK-BE-505** (`SystemClientSubjectValidator`).
order-service used a `sub` allow-list because it had a single caller; account-service has multiple
callers and the repo already declared the **scope** path, so we enforce `internal.invoke` (self-maintaining;
no caller enumeration to keep in sync).

## AC-0 — Finding (audit result, verified 2026-07-17)

- **Config**: `account-service` `SecurityConfig.internalJwtDecoder()` sets exactly
  `JwtValidators.createDefaultWithIssuer(jwtIssuer)` (timestamp + issuer). No `sub` allow-list,
  no scope check, no audience validator. Authorization rule = `.requestMatchers("/internal/**").authenticated()`
  (no `hasAuthority`/`hasRole`/`@PreAuthorize` anywhere in account-service main source).
  (`SecurityConfig.java:70,91`)
- **Shared issuer** = the crux: account internal decoder pins issuer `http://localhost:8081`
  (`application.yml`), which is the **same** auth-service SAS that mints user tokens. The "GAP" issuer
  is not a separate discriminating authorization server — it is that one shared SAS. So the issuer pin
  does not distinguish system vs user. (Proven by `auth-service` `TenantClaimTokenCustomizer`:
  `client_credentials` → `sub`=client-id, no `roles`; `authorization_code` → `sub`=account UUID, has `roles`.)
- **Blast radius** (all under the gapped chain, `presentation/internal/`): cross-tenant PII read
  (`AccountSearchController` search-by-email + full-account GET), GDPR export/delete (`GdprController`),
  account lock/unlock/delete (`AccountLockController`), **role mutation = privilege escalation**
  (`AccountRoleController` `:add`/`:remove`), bulk provisioning + password-reset (`TenantProvisioningController`,
  `BulkAccountController`), tenant/org lifecycle. A holder of any ordinary CUSTOMER token on the internal
  network could invoke all of these.
- **Vacuous test (face #4)**: `InternalDualAuthSliceTest` mocks the `JwtDecoder` (real validator chain
  never runs) and its accepted-path token uses `sub=account-service-client` + `scope=internal.invoke`;
  `InternalControllerTest` runs with the bypass ON. **No existing test mints a NON-system / no-scope token
  and asserts rejection**, so the gap hides green. account-service `integration/` ITs run under the `test`
  profile → `InternalApiFilter` bypass ON → the JWT decoder is not exercised at all.
- **Non-breaking verification (all callers carry `internal.invoke`)**: the four real iam callers —
  admin-service (`AccountServiceClient` + `AccountServiceTenantClient`, client `admin-service-client`),
  auth-service (`AccountServiceClient`, `auth-service-client`), security-service (`AccountServiceClient`,
  `security-service-client`) — each authenticate with a client registered with exactly `["internal.invoke"]`
  (`V0019`) and send no narrowing `scope` param, so every minted token carries `internal.invoke`.
  E2E `InternalWorkloadAuthE2ETest` mints with explicit `scope=internal.invoke`. **→ enforcement is
  non-breaking for every functioning caller.**
- **Adjacent finding (out of scope, note only)**: ecommerce `product-service` `AccountServiceSellerProvisioner`
  authenticates as `product-service-client`, which is **not registered in any seed** — it already fails at
  token acquisition (`401 invalid_client`) today and its account calls are D3 fail-soft (swallowed). This
  change neither fixes nor worsens it; the correct fix (register `product-service-client` **with**
  `internal.invoke`) is consistent with this contract. Candidate follow-up.

## Scope

- **In**: new decoder-level `RequiredScopeValidator` on account-service `/internal/**`; wire it into
  `internalJwtDecoder()` alongside the issuer/timestamp default; env-overridable required scope
  (default `internal.invoke`); fail-closed on blank config. Unit + wiring tests (mutation-checked).
  Contract note pinning the scope requirement.
- **Out**: `sub` allow-list (rejected in favour of scope per repo intent); registering
  `product-service-client` (separate ecommerce task); security-service `/internal/security/**`
  (own `InternalAuthFilter`, 403 chain — separate shape, verify separately); changing the shared-issuer
  model or introducing a distinct authorization server.

## Acceptance Criteria

- **AC-1**: `RequiredScopeValidator` (decoder-level `OAuth2TokenValidator<Jwt>`) returns success iff the
  token's `scope` claim (space-delimited string or list; `scp` fallback) contains the required scope;
  failure otherwise. A blank/empty required-scope configuration → **fail-closed** (reject all), never
  admit-all.
- **AC-2**: `internalJwtDecoder()` composes `[createDefaultWithIssuer(issuer), RequiredScopeValidator(requiredScope)]`
  via `DelegatingOAuth2TokenValidator`. Required scope from `${internal.api.jwt.required-scope:internal.invoke}`.
- **AC-3**: A token carrying `internal.invoke` (correct issuer, unexpired) passes; a token WITHOUT it
  (wrong scope, or no `scope` claim) is rejected — verified against the **actual** validator chain
  `internalJwtDecoder()` builds (not a re-implemented copy).
- **AC-4**: Fail-closed → `401 UNAUTHORIZED` via the existing `onAuthenticationFailure` entry point
  (contract preserved).
- **AC-5**: Existing accepted-path tests stay green (their tokens already carry `internal.invoke`).
- **AC-6**: Mutation check — breaking `RequiredScopeValidator.validate` to always succeed turns the guard
  tests RED (recorded in the PR).
- **AC-7**: Full account-service fast lane green.

## Related Specs

- `projects/iam-platform/PROJECT.md` (multi-tenant, regulated, pii-sensitive)
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0019__seed_internal_service_workload_clients.sql`
  (§ `internal.invoke` — "allows scope-based hardening in TASK-BE-319")

## Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` (§ Authentication — pin scope)
- Precedent: ecommerce `order-service` `SystemClientSubjectValidator` (TASK-BE-505), `order-confirm-paid-stale.md`

## Edge Cases

- `scope` claim as space-delimited String vs List<String>; `scp` fallback; blank/missing claim.
- Required-scope config blank → fail-closed (misconfig must not reopen the gap).
- Multiple scopes including `internal.invoke` → pass.

## Failure Scenarios

- Enforcement breaks a legitimate caller → mitigated: all four functioning callers verified to carry
  `internal.invoke` (AC-0). product-service is already non-functional (adjacent finding).
- Validator added but not wired into the decoder → AC-3 tests the real `internalJwtDecoder()` chain.
- Fail-open on blank config → AC-1 fail-closed branch + test.
