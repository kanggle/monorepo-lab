# TASK-BE-515 — IAM workload client_credentials tokens omit `internal.invoke` scope → all cross-service `/internal/**` calls rejected (nightly + federation E2E RED)

- **Type**: TASK-BE
- **Status**: done
- **Service**: account-service, admin-service, auth-service, security-service (iam-platform)
- **Domain/traits**: saas / [transactional, regulated, audit-heavy, integration-heavy, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (security authz + fleet-wide cross-service blast radius)

## Goal

Close the caller side of the `internal.invoke` scope contract that **TASK-BE-514** (`#2631`) and
**TASK-MONO-422** (`#2633`, shared `RequiredScopeValidator`) shipped on the **receiver** side on
2026-07-17. Those tasks began enforcing the `internal.invoke` scope on account-service `/internal/**`
(decoder-level, fail-closed 401). Their AC-0 declared the change "non-breaking for every functioning
caller" on the premise that the four IAM callers "send no narrowing `scope` param, **so every minted
token carries `internal.invoke`**." **That premise is false.** Spring Authorization Server's
`client_credentials` flow grants **only the scopes explicitly requested** (the client's registered
scopes are the *allow-list* used to validate a request, not a default). A token request with no
`scope` param yields a token with an **empty/absent `scope` claim** → the new validator rejects it →
401. Every real cross-service `/internal/**` call now fails.

All four IAM `IamClientCredentialsTokenProvider`s mint their workload token with exactly
`.body("grant_type=client_credentials")` and **none** request `scope=internal.invoke`. Fix: request
the scope the receiver now requires (the client is already registered with `internal.invoke` in
auth-service Flyway `V0019`, so the request is authorized).

This is the sibling-parity completion of BE-514: the receiver was hardened, the callers were not —
a classic straggler where the enforcing side moved and the calling side was left behind
(`project_enforcement_straggler_sibling_parity`, `env_shared_issuer_authenticated_is_not_authorized`).

## AC-0 — Finding (audit result, verified 2026-07-18)

- **Two production RED workflows, one root cause.** Both surfaced on the 2026-07-18 nightly, immediately
  after BE-514/MONO-422 merged 2026-07-17; both are outside `ci.yml` (nightly / schedule) so they did
  **not** gate the merges (`project_nightly_only_spec_merges_green_then_main_reds`).
  - **Federation Hardening E2E** — 4 specs fail (`entitlement-trust-crossdomain:66`,
    `subscription-plane-separation:183`, `tenant-admin-delegation:138`, `tenant-switch-rescope:106`).
    All die in the OIDC login fixture (`tests/federation-hardening-e2e/fixtures/login.ts:133`) because
    the form POST bounces to `http://auth-service:8081/login?error`. auth-service form-login resolves
    `tenant_type` via a **fail-closed** account-service call
    (`CredentialAuthenticationProvider` → `TenantTypeResolver.resolve` → `AccountServiceClient.getTenantType`
    → `GET /internal/tenants/{slug}`); that call is now 401 → `AccountServiceUnavailableException`
    → `AuthenticationServiceException` → `/login?error`. **SUPER_ADMIN passes** (14/18 green) because
    `TenantTypeResolver` short-circuits the platform sentinel `tenant_id='*'` with **no** network call;
    every real-tenant persona (acme/umbrella) hits the rejected call.
  - **Platform Console nightly E2E** — IAM/gap overview card renders `data-status="degraded"`
    (`overview-consolidation.spec.ts:128`). admin-service → account-service `GET /internal/accounts`
    is now 401 → `DownstreamFailureException` → admin-service 5xx → console-bff classifies 5xx as
    `DEGRADED`. (admin-service's other account calls are D3 fail-soft, so they degrade silently; only
    the auth-service login path is fail-closed and hard-breaks.)
- **The caller defect (verified in source, all four identical):**
  - `account-service` `IamClientCredentialsTokenProvider.java:70`
  - `admin-service`  `IamClientCredentialsTokenProvider.java:71`
  - `auth-service`   `IamClientCredentialsTokenProvider.java:70`
  - `security-service` `IamClientCredentialsTokenProvider.java:66`
  each send `.body("grant_type=client_credentials")` — **zero** request `scope=internal.invoke`
  (`git grep scope=internal.invoke` over the four providers = 0 hits).
- **SAS behaviour confirming empty scope claim:** every existing auth-service SAS test that mints a
  client_credentials token passes an **explicit** `scope` param (`OAuth2AuthorizationServerIntegrationTest`
  lines 166/213/228/276 → `account.read` / `internal.invoke`); none exercises the no-scope path. The
  RED correlating exactly with the enforcement merge is the empirical confirmation that no-scope ⇒ no
  `internal.invoke` claim ⇒ reject.
- **Why PR CI stayed green:** BE-514's only accepted-path proof (`InternalWorkloadAuthE2ETest`) mints
  with an **explicit** `formParam("scope","internal.invoke")`, and account-service ITs run the `test`
  profile with the `InternalApiFilter` bypass ON — neither exercises the real no-scope caller path.
  The enforced decoder path runs for real only in nightly/federation.

## Scope

- **In**: add `scope=internal.invoke` to the client_credentials token request body of all four IAM
  `IamClientCredentialsTokenProvider`s. Update each provider's guard test to assert the outbound body
  carries the scope (admin/auth/security exact-match tests; account's `AuthServiceClientUnitTest`
  loose matcher tightened). Add a regression test proving the SAS contract both ways — explicit
  `scope=internal.invoke` ⇒ token `scope` claim contains it; no scope ⇒ claim absent (documents the
  trap so no future change re-asserts "non-breaking" on the false premise).
- **Out**: changing the receiver enforcement (BE-514/MONO-422 posture is correct — fix the caller, not
  the gate); registering ecommerce `product-service-client` (separate ecommerce task, BE-514 adjacent
  finding); the console→finance user-token forbidden card (separate axis — user OIDC token, not a
  workload token — tracked in **TASK-PC-BE-013**); making the requested scope env-configurable
  (hardcoded `internal.invoke` matches the receiver's default contract; a code comment pins the link).

## Acceptance Criteria

- **AC-1**: All four IAM `IamClientCredentialsTokenProvider`s request `scope=internal.invoke` in the
  `client_credentials` token body (`grant_type=client_credentials&scope=internal.invoke`).
- **AC-2**: admin-service / auth-service / security-service provider unit tests assert the exact
  outbound body `grant_type=client_credentials&scope=internal.invoke`; account-service
  `AuthServiceClientUnitTest` asserts the token request body contains `scope=internal.invoke`.
- **AC-3**: auth-service `OAuth2AuthorizationServerIntegrationTest` gains a test that mints a real
  workload token and asserts: (a) with `scope=internal.invoke` the JWT `scope` claim contains
  `internal.invoke`; (b) with **no** scope param the JWT carries **no** `internal.invoke` scope —
  proving the mechanism behind the RED and guarding against a future no-scope regression.
- **AC-4**: Mutation check — reverting any provider to the no-scope body turns that service's guard
  test (AC-2) RED (recorded in the PR).
- **AC-5**: Fast lane green for all four services (account, admin, auth, security).
- **AC-6** *(CI-authoritative, verified post-merge, not locally — Windows host is not IT-authoritative)*:
  the next Federation Hardening E2E and Platform Console nightly E2E runs on `main` go GREEN on the
  four previously-failing federation specs and the IAM/gap console card. Recorded in the close-chore.

## Related Specs

- `projects/iam-platform/PROJECT.md` (multi-tenant, regulated, integration-heavy)
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0019__seed_internal_service_workload_clients.sql`
  (the four callers registered with `["internal.invoke"]` — the scope this task now requests)
- Receiver side: `projects/iam-platform/tasks/done/TASK-BE-514-account-internal-endpoint-accepts-any-token.md`,
  root `tasks/done/TASK-MONO-422-shared-internal-invoke-scope-validator.md`

## Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` (§ Authentication — `internal.invoke`)

## Edge Cases

- SAS `client_credentials` with no `scope` param ⇒ empty authorized scopes ⇒ no `scope` claim (the
  exact trap that broke this; the AC-3 negative test pins it).
- A caller requesting a scope the client is **not** registered for ⇒ SAS rejects the token request
  (invalid_scope); all four clients are registered with `internal.invoke` in `V0019`, so the request
  is authorized.
- Token caching: the scope is fixed per provider, so the existing single-fetch cache semantics are
  unchanged (the body is constant).
- If the receiver's required-scope env is ever overridden away from `internal.invoke`, these callers
  must track it — noted in a code comment linking to the BE-514 contract (kept hardcoded to avoid a
  silent split-brain where only one side is overridden).

## Failure Scenarios

- Fix one provider but miss a sibling ⇒ that service's `/internal/**` calls stay 401. Mitigated: all
  four are in scope and AC-1/AC-2 cover each (fleet grep = 0 remaining no-scope bodies).
- Requested scope typo (e.g. `internal_invoke`) ⇒ token minted without the required scope ⇒ still 401.
  Mitigated: AC-3 asserts the **minted token's** `scope` claim, not just the request string (verify the
  artifact, not a proxy).
- Adding the scope but leaving the guard test on the old exact body ⇒ compile/assert drift. Mitigated:
  AC-2 updates each guard in the same change; AC-4 mutation check confirms the guard bites.
