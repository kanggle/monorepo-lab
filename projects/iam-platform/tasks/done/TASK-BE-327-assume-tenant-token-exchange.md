# Task ID

TASK-BE-327

# Title

ADR-MONO-020 § 3.3 step 2 (D2+D3) — GAP **assume-tenant** RFC 8693 token-exchange: auth-service mints a short-lived domain-facing GAP OIDC token scoped to the **selected** customer (`tenant_id=<selected>` + `entitled_domains=<selected's ACTIVE subscriptions>`), issued **only when** the operator's D1 assignment to the selected tenant **and** the tenant's ACTIVE subscription both hold. AWS STS AssumeRole analog; extends ADR-014 exchange; reuses the BE-324 keystone. **Additive / net-zero to existing flows** (the authorization_code login path is byte-unchanged; the exchange is only invoked by the D4 switcher in a later step).

# Status

done

> **완료 (2026-05-31)**: impl PR #983 (squash `de61856b`). ADR-MONO-020 § 3.3 step 2 (D2+D3) — GAP **assume-tenant** RFC 8693 token-exchange. **아키텍처**: auth-service 가 exchange 호스팅(SAS `/oauth2/token`, custom `AssumeTenantAuthenticationConverter`+`AssumeTenantAuthenticationProvider`+`PublicClientTokenExchangeAuthenticationConverter`, 기존 public-client refresh wiring mirror) → 공유 `JwtGenerator`+`TenantClaimTokenCustomizer` 경유 mint(동일 iss/JWKS/kid → 도메인 게이트 무변경 수용, BFF pass-through 무변경). **새 auth→admin 발급-시점 edge**: admin-service `GET /internal/operator-assignments/check`(신규 `@Order(0)` `/internal/**` GAP-JWKS chain, account-service 패턴 mirror; `OperatorAssignmentCheckUseCase` = findByOidcSubject fail-closed → `'*'`→true → `TenantScopeResolver` 유효스코프; **admin_actions 미기록**). **정의적 correctness 속성**: admin assignment gate = **fail-CLOSED**(`AdminAssignmentClient`: assigned=false/4xx/5xx/timeout/circuit-open → `AssumeTenantDeniedException` → `invalid_grant`, 토큰 0) ↔ account `entitled_domains` = **fail-SOFT**(account down → claim omit, 토큰 발급; **selected 구독만, union 금지**, recursion-safe — keystone `populateEntitledDomains` 재사용). **선택-테넌트 주입 load-bearing fix**: SAS 1.4 `JwtGenerator` 가 임의 `context.put()` 미복사 → selected tenant/type 을 `AssumeTenantAuthenticationToken`(=authorizationGrant)에 실어 customizer 가 `getAuthorizationGrant()` 로 읽음. assumed 토큰 = short-lived, **refresh 토큰 없음**, `tenant_type=B2B_ENTERPRISE`. **net-zero**: authorization_code/login + ADR-014 operator-exchange 경로 byte-unchanged. **scope-lock**: auth-service + admin-service + contracts(`auth-api.md`/신규 `auth-to-admin.md`/auth·admin architecture·dependencies·rbac·data-model) 만 — console-bff/console-web/도메인/ADR-014/login 0 byte. **CI 2-pass**: 1차 RED = `PlatformConsoleOidcClientSeedIntegrationTest`(V0020 가 platform-console-web 에 token-exchange grant 추가 → grant-set `containsExactly` 단언 깨짐; 로컬 targeted IT-run 이 full auth `integrationTest` 미실행 → local PASS/CI FAIL split) → seed 단언 갱신(client 가 실제 grant 지원하므로 정당한 변경, `49c9bcda`) → 2차 GREEN(GAP Integration pass, console-bff pass=pass-through 불변). **3차원**(MERGED `de61856b` / origin/main tip 일치 / pre-merge 0 failing). **테스트**: auth 신규 converter/provider/client unit + customizer 분기 + `AssumeTenantExchangeIntegrationTest` 7케이스(happy/denied/admin-unavail fail-closed/account-unavail fail-soft/missing-audience/invalid-subject/net-zero) + admin `OperatorAssignmentCheck` use-case/controller/chain-slice/IT 5. **후속(별 task, 새 세션 권장)**: **D4** console-web active-tenant switcher → assume-tenant flow + multi-assignment demo seed + federation-e2e A↔B → **D6 step 4** legacy single-value read cleanup. **메타**: 발급-시점 cross-service authz = fail-closed, claim-enrichment = fail-soft 의 이중정책 분리가 핵심; SAS custom grant 의 selected-param 주입은 authorizationGrant 경유(context.put 미복사).

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-020 ACCEPTED (MONO-157 `de68ab03`) § 2 **D2** (RFC 8693 assume-tenant exchange) + **D3** (`entitled_domains` = selected customer's ACTIVE subscriptions only) + § 3.3 **step 2**; **TASK-BE-326 DONE** (`ee19ff8c`) — the D1 `operator_tenant_assignment` store + `TenantScopeResolver.resolveEffectiveTenantScope(...)` this task reads cross-service. dependency-correct base = current `origin/main`.
- **builds on**: ADR-MONO-014 (`TokenExchangeService` / `OperatorAccessTokenIssuer` — the RFC 8693 substrate; this is a **sibling** exchange, the operator-identity exchange is unchanged); **TASK-BE-324** (`TenantClaimTokenCustomizer.populateEntitledDomains` keystone — reused verbatim on the assume-tenant path, fail-soft + recursion-safe); **TASK-BE-317/318c/319b** (GAP `client_credentials` workload-identity JWT + `/internal/**` resource-server pattern — mirrored for the new auth→admin edge).
- **enables (후속, 별 task)**: **D4** console-web active-tenant switcher → assume-tenant flow + multi-assignment demo seed + federation-e2e A↔B switch (step 3) → **D6 step 4** legacy single-value read cleanup.
- **orthogonal to**: ADR-005 / TASK-BE-317 workload identity (this task *reuses* its `GapClientCredentialsTokenProvider`, does not change it).
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** — auth **issuance hot-path = 최고위험** (ADR-020 § 3.3 step 2 "highest-risk"); SAS custom token-exchange grant wiring + a new cross-service trust edge + the fail-closed/fail-soft split.

---

# Goal

Implement ADR-020 **D2+D3** end-to-end **server-side**: a GAP **assume-tenant** capability that, given an operator's base GAP OIDC session token and a **selected** customer tenant, mints a **short-lived domain-facing GAP OIDC access token** scoped to that customer — `tenant_id=<selected>` + `tenant_type` + `entitled_domains=<selected's ACTIVE subscriptions>` (D3, least-privilege, **NO union** across the operator's other assignments) — **issued only when** (a) the operator has a D1 assignment to the selected tenant (admin-service `operator_tenant_assignment` ∪ legacy home, via `TenantScopeResolver`) **and** (b) the tenant has an ACTIVE subscription (account-service, ADR-019 D2). The minted token has the **same `iss`/JWKS/kid** as the login token, so the federated domain gates (ADR-019 D5) accept it unchanged and the BFF passes it through unchanged (ADR-017 D6).

This is the AWS **STS AssumeRole** analog and a **sibling** of the ADR-014 operator-identity exchange (which is left byte-unchanged). The console switcher that *invokes* this exchange is **D4 / step 3 (separate task)** — this task delivers only the GAP server capability + its contract, fully IT-testable without any frontend.

## Architecture decision (decided; record in the task PR, do not re-litigate)

There are two distinct token worlds (confirmed by recon):

| | operator token (ADR-014) | **assumed / domain-facing token (this task)** |
|---|---|---|
| Issuer | `admin-service` (`OperatorAccessTokenIssuer`, `iss=admin-service`) | **`auth-service` (SAS, `iss=auth-service`)** |
| Audience | `/api/admin/**` only | the federated **domains** (entitlement-trust gates, ADR-019 D5) |
| tenant scope | NOT in token; resolved per-request from `admin_operators.tenant_id` | **carried in token: `tenant_id=<selected>` + `entitled_domains`** |

Because the assumed token must pass the **domain** gates, it **must be minted by auth-service** (the only OIDC issuer the domains trust). Because the D1 assignment store lives in **admin-service**, auth-service must verify the assignment at issuance time. Hence:

- **HOST the assume-tenant exchange on auth-service** (SAS `/oauth2/token`, RFC 8693 token-exchange grant). Minting + the D3 `entitled_domains` derivation stay fully inside the OIDC issuer, reusing the `TenantClaimTokenCustomizer` keystone in-process.
- **NEW auth→admin issuance-time edge**: auth-service calls a new admin-service `GET /internal/operator-assignments/check` to authorize the assignment (read-only). This is an **issuance-time, one-shot** check — NOT a per-request domain→GAP callback (ADR-020 § 3.1 forbids only the latter; § 3.1 explicitly keeps "the assignment store (D1), the assume-tenant issuance (D2), and the entitled_domains derivation (D3) all in GAP" — auth↔admin coordination is GAP-internal).
- **Rejected alternative** (record in PR): host on admin-service + admin→auth mint call. Rejected — it would force auth-service to expose a *mint-arbitrary-tenant-token* capability (far more dangerous surface) than a read-only assignment check; and it would split minting away from the keystone. Matches ADR-020 D2 Option-B "composes with the operator-identity exchange" / "GAP is the single issuance authority".

This placement is a **direct mechanical consequence** of D2 (domain-facing token ⇒ auth-issued) + D1 (assignment in admin) — it is **not** a new architecture decision (no HARDSTOP-09): it introduces no new ADR-level choice, only the implementation edge the ADR's decision already implies.

---

# Scope

## In scope — auth-service (issuer + the exchange)

1. **Assume-tenant RFC 8693 token-exchange grant** on the SAS `/oauth2/token` endpoint (`grant_type=urn:ietf:params:oauth:grant-type:token-exchange`):
   - **subject_token** = the operator's base GAP OIDC **access token** (the `platform-console-web` token auth-service itself issued); `subject_token_type=urn:ietf:params:oauth:token-type:access_token`. Validate it with the service's **own** `JwtDecoder` (same JWKS it signs with) — no foreign-JWKS validation needed (unlike admin's `GapOidcSubjectTokenValidator`). Extract `sub` (account_id) + the base `tenant_id`. Fail-closed on any validation failure → `invalid_grant`.
   - **selected tenant** carried as the RFC 8693 **`audience`** parameter (the customer the operator wants to act as). Document the choice in `auth-api.md`. (`resource` is the acceptable alternative; pick one and be consistent.)
   - Wire via a custom **`AssumeTenantAuthenticationConverter`** + **`AssumeTenantAuthenticationProvider`** registered on the SAS token endpoint — mirror the existing `PublicClientRefreshTokenAuthenticationConverter` / `SasRefreshTokenAuthenticationProvider` wiring pattern in `AuthorizationServerConfig` (the converter filters by `grant_type`, returns `null` on mismatch so existing flows are untouched). The provider: validate subject → **fail-closed assignment check** (step 2 below) → build an `OAuth2TokenContext`/`JwtEncodingContext` carrying the **selected** tenant → run the existing `JwtGenerator` + `TenantClaimTokenCustomizer` so the claims are injected by the **same** path → return a short-lived access token. **No refresh token** is issued for the assumed token (short-lived, re-minted per selection — ADR-020 § 3.1).
2. **Assignment authorization — fail-CLOSED** (the gate): new `OperatorAssignmentPort.isAssigned(String oidcSubject, String tenantId): boolean` (application/port) + `AdminAssignmentClient` (infrastructure/client) that calls admin-service `GET /internal/operator-assignments/check?oidcSubject=<sub>&tenantId=<selected>` with a **GAP `client_credentials` Bearer JWT** (reuse the existing `GapClientCredentialsTokenProvider` + `ResilienceClientFactory` pattern from `AccountServiceClient` — timeouts + retry + circuit breaker). **Any** failure (4xx incl. not-assigned, 5xx, circuit-open, timeout, IO, admin-service unavailable) ⇒ **deny** (no token). This is the authorization gate — it MUST NOT fail-soft. Throw a dedicated `AssumeTenantDeniedException` → mapped to `invalid_grant` (RFC 8693) / 400. **⚠️ This fail-closed semantics is the opposite of the account-service `entitled_domains` fail-soft below — keep them strictly separate.**
3. **D3 `entitled_domains` for the assumed token — fail-SOFT, least-privilege**: extend `TenantClaimTokenCustomizer` with a `token-exchange` grant branch that reads the **selected** `tenant_id` from the exchange context and injects `tenant_id=<selected>` + `tenant_type` + calls the **existing** `populateEntitledDomains(context, <selectedTenantId>)` (account-service ACTIVE subscriptions of the **selected** tenant **only** — NO union across the operator's other assignments). `populateEntitledDomains` is reused **verbatim**: fail-soft (account down → omit claim → domains fall back to the `tenant_id` gate) + recursion-safe (never on `client_credentials`). Resolve `tenant_type` for the selected tenant (it is a customer tenant → `B2B_ENTERPRISE`; source it the same way the login path resolves `tenant_type`, or carry it on the exchange context — do not hardcode unless the spec fixes customer tenants to a single type; check `multi-tenancy.md`).
4. **Tests** (auth-service): converter/provider unit (grant filtering; subject-invalid → `invalid_grant`; assigned → mint; **assignment-denied → no token**; **admin-unavailable → fail-closed deny**); customizer token-exchange branch unit (selected tenant injected; **account-unavailable → token issued WITHOUT `entitled_domains`, fail-soft**; entitled_domains = selected's subs only, no union); **IT** (Testcontainers) covering the happy path (assigned + subscription → token carries `tenant_id=selected` + `entitled_domains=selected`), denied path, both fail modes, and the **single-assignment net-zero shape** (operator assuming their own home tenant → token shape identical to today's login token).

## In scope — admin-service (the assignment-check edge)

5. **New internal endpoint** `GET /internal/operator-assignments/check?oidcSubject=<sub>&tenantId=<t>` (presentation/internal): resolve `oidcSubject` → operator (`AdminOperatorPort.findByOidcSubject`), **fail-closed** if missing OR not `ACTIVE` (→ `assigned=false`); else `assigned = TenantScopeResolver.resolveEffectiveTenantScope(internalId, homeTenant).contains(tenantId)`. A **platform-scope `'*'` operator** (`isPlatformScope()`) → `assigned=true` for any non-blank tenant (the sentinel already grants all-tenant scope — document this branch explicitly). **Read-only, no `admin_actions` row** (mirrors the ADR-014 token-exchange "not audited" rule; the subsequent domain commands each audit). Response: `{ "assigned": true|false }` (200). Never leak whether the operator exists vs is unassigned beyond the boolean.
6. **`/internal/**` security chain** — admin-service currently has a single operator-JWT chain. Add a **second `SecurityFilterChain @Order(0)` with `securityMatcher("/internal/**")`** + `oauth2ResourceServer().jwt()` validating the **GAP `client_credentials` JWT** (GAP JWKS URI + issuer), mirroring **account-service** `SecurityConfig.internalJwtDecoder` + `InternalApiFilter` (non-terminal dev/test/standalone bypass; production fail-closed 401 `UNAUTHORIZED`). The existing operator chain keeps `/api/admin/**` and is left otherwise byte-unchanged (it already does NOT match `/internal/**`). Reuse libs/config the way account-service does (`internal.api.jwt.jwk-set-uri` / `internal.api.jwt.issuer` / `internal.api.bypass-when-unconfigured`).
7. **Tests** (admin-service): `/internal/operator-assignments/check` controller slice (assigned / not-assigned / unknown-subject / non-ACTIVE → assigned=false / `'*'` platform → assigned=true) + the `/internal/**` security chain (no JWT → 401 `UNAUTHORIZED`; valid GAP JWT → 200) + an IT proving the real `TenantScopeResolver` dual-read (legacy home → assigned; seeded assignment → assigned; unrelated tenant → not assigned).

## In scope — contracts (update **BEFORE** implementation — CLAUDE.md Layer Rules)

8. `specs/contracts/http/auth-api.md` § `POST /oauth2/token` — add the `token-exchange` `grant_type` + an **Assume-Tenant Exchange** sub-section: request (`grant_type`, `subject_token`, `subject_token_type`, `audience=<selected tenant>`), the short-lived response (no `refresh_token`), the assumed claim set (`tenant_id=<selected>`, `tenant_type`, `entitled_domains=<selected's ACTIVE subs>`), and errors (`invalid_grant` for subject-invalid / **assignment-denied / admin-unavailable**; `invalid_request` for missing `audience`).
9. **NEW** `specs/contracts/http/internal/auth-to-admin.md` — the assignment-check edge (mirror `auth-to-account.md` / `admin-to-account.md` structure): direction auth-service → admin-service, path `GET /internal/operator-assignments/check`, GAP `client_credentials` JWT auth, request params, `{assigned}` response, **fail-closed caller semantics** (admin unavailable ⇒ exchange denied), caller resilience (timeout/retry/circuit-breaker; **no** "audit first" since this is a read).
10. `specs/services/auth-service/architecture.md` — add the assume-tenant issuance path (a new minting path alongside `authorization_code`), the new `AdminAssignmentClient` in the Internal Structure tree, and the **new auth→admin outbound dependency** in Integration Rules + the reference to `auth-to-admin.md`. Also update `specs/services/auth-service/dependencies.md` (new outbound edge to admin-service).
11. `specs/services/admin-service/architecture.md` + `rbac.md` — document the new `/internal/operator-assignments/check` read endpoint + the `/internal/**` resource-server chain, and that the D1 effective-scope (BE-326) is now read cross-service by auth-service for the assume-tenant authorization. No schema change (V0030 already shipped) — `data-model.md` gets a one-line "read by auth-service via /internal" note only.

## Out of scope (do NOT touch)

- **D4 console switcher** (console-web active-tenant flow), the multi-assignment demo seed, and the federation-e2e A↔B switch — **step 3, separate task**.
- **console-bff** — **no change** (ADR-017 D6 pass-through; PC-BE-007 already proved it is value-agnostic for the assumed token). Touching it fails scope-lock.
- **Domain services** (wms/scm/finance/erp/gap) entitlement-trust gates — **no change** (ADR-019 D5 already accepts the token; same `iss`/JWKS).
- The **ADR-014 operator-identity exchange** (`TokenExchangeService` / `OperatorAccessTokenIssuer` / `/api/admin/auth/token-exchange`) — **byte-unchanged** (sibling, not modified).
- The **`authorization_code` / login** issuance path — **byte-unchanged** (the assume-tenant grant is purely additive; existing token issuance must be net-zero).
- **D6 step 4** legacy single-value read cleanup.
- Seeding any real assignment row (the IT seeds its own fixtures).

---

# Acceptance Criteria

- **AC-1 (exchange grant)**: `POST /oauth2/token` with `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` + `subject_token=<base GAP OIDC token>` + `audience=<selected tenant>` mints a short-lived JWT access token (**no `refresh_token`**) with `iss`/kid identical to the login token. Existing grants (`authorization_code`/`client_credentials`/`refresh_token`) **byte-unchanged** (converter returns null on mismatch).
- **AC-2 (assignment gate — fail-CLOSED)**: token issued **only when** admin-service `/internal/operator-assignments/check` returns `assigned=true`. Not-assigned, unknown subject, non-ACTIVE operator, **or admin-service unavailable / circuit-open / timeout** ⇒ **no token**, `invalid_grant` (400). No fail-soft on this gate.
- **AC-3 (D3 entitled_domains — least-privilege + fail-SOFT)**: the assumed token's `tenant_id` = the **selected** tenant; `entitled_domains` = the **selected** tenant's ACTIVE subscriptions **only** (NO union across the operator's other assignments). account-service unavailable ⇒ token still issued **without** the `entitled_domains` claim (fail-soft, domains fall back to the `tenant_id` gate). `populateEntitledDomains` reused unchanged; never invoked on `client_credentials` (recursion-safe).
- **AC-4 (admin /internal endpoint + chain)**: `GET /internal/operator-assignments/check` returns `{assigned}` per `TenantScopeResolver` effective scope (legacy home ∪ assignments; `'*'`→true). The `/internal/**` chain rejects a missing/invalid GAP JWT with 401 `UNAUTHORIZED` and accepts a valid GAP `client_credentials` JWT; the operator `/api/admin/**` chain is unchanged. Read-only — **no `admin_actions` row** written.
- **AC-5 (domain-gate / BFF compatibility)**: the assumed token is accepted by an existing federated-domain entitlement gate (assert via the keystone test fixture or an IT that the token shape matches the `authorization_code` token: same `iss`, `tenant_id`, `entitled_domains` claim names). **console-bff + domain services unchanged** (0 byte).
- **AC-6 (net-zero to existing flows)**: the `authorization_code`/login and ADR-014 operator-exchange paths are **byte-unchanged**; their existing unit/IT assertions pass **unmodified** (only new-dependency stub/ctor adjustments allowed). A single-assignment operator assuming their **home** tenant gets a token whose claim shape is identical to today's login token (net-zero shape).
- **AC-7 (contract-first)**: `auth-api.md` + new `auth-to-admin.md` + `auth-service/architecture.md`+`dependencies.md` + `admin-service/architecture.md`+`rbac.md` updated **before/with** the code; the implemented request/response shapes match the contracts exactly.
- **AC-8 (build + CI)**: auth-service + admin-service compile; all unit + IT GREEN — **CI Linux GAP Integration (Testcontainers) is authoritative**. 0 regressions.
- **AC-9 (scope-lock)**: changes = auth-service (exchange converter/provider + `AdminAssignmentClient`+port + `TenantClaimTokenCustomizer` token-exchange branch + `AuthorizationServerConfig` wiring + `SecurityConfig`/properties if needed + tests) + admin-service (`/internal/operator-assignments/check` + `/internal/**` chain + tests) + the listed contracts/specs **only**. **0** changes to console-bff, console-web, domain services, the ADR-014 exchange, or the login path.

---

# Related Specs

- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` § 2 **D2/D3** + § 3.1 (invariants: M1-M7 preserved; GAP single issuance authority; ADR-017 D6 pass-through; ADR-014 extended-not-replaced; least-privilege; short-lived self-contained) + § 3.3 step 2.
- `docs/adr/ADR-MONO-019-...md` § D2 (subscription = entitlement source) + § D5 (entitlement-trust domain gates — accept the assumed token unchanged).
- `docs/adr/ADR-MONO-014-...md` (RFC 8693 operator-identity exchange — the substrate this siblings) + ADR-MONO-017 § D6 (BFF pass-through).
- `projects/global-account-platform/docs/adr/ADR-002-admin-rbac.md` (`admin_operators.tenant_id` + `'*'` sentinel — the scope model the check reads) ; `ADR-001-oidc-adoption.md` (auth-service = central OIDC IdP).
- `specs/features/multi-tenancy.md` (Platform Console + `tenant_id`/`tenant_type` claim protocol — `tenant_type` resolution for the selected tenant) ; `rules/traits/multi-tenant.md` M1-M7.
- `specs/services/auth-service/architecture.md` (issuance paths, SAS chain ordering, "모든 HTTP는 @Transactional 밖에서") ; `specs/services/admin-service/{architecture,rbac,security}.md`.

# Related Contracts

- **Update**: `specs/contracts/http/auth-api.md` (`POST /oauth2/token` token-exchange grant).
- **Create**: `specs/contracts/http/internal/auth-to-admin.md` (assignment-check edge).
- **Reference (unchanged)**: `specs/contracts/http/internal/auth-to-account.md` + `account-tenant-domain-subscriptions.md` (the `entitled_domains` source, already wired by BE-324) ; `admin-api.md` (ADR-014 exchange — unchanged) ; `console-registry-api.md` (D4 consumer — unchanged here).

# Related Code

- **auth-service** (issuer): `infrastructure/oauth2/AuthorizationServerConfig.java` (SAS chain — register the new converter/provider next to `PublicClientRefreshTokenAuthenticationConverter`) ; `infrastructure/oauth2/TenantClaimTokenCustomizer.java` (add the `token-exchange` branch; **reuse** `populateEntitledDomains`) ; `infrastructure/client/AccountServiceClient.java` + `GapClientCredentialsTokenProvider.java` + `application/port/AccountServicePort.java` (templates for the new `AdminAssignmentClient`/`OperatorAssignmentPort`) ; `infrastructure/config/SecurityConfig.java` (`@Order(2)`) ; new: `AssumeTenantAuthenticationConverter`, `AssumeTenantAuthenticationProvider`, `AdminAssignmentClient`, `OperatorAssignmentPort`, `AssumeTenantDeniedException`.
- **admin-service** (assignment authority): `application/TenantScopeResolver.java` + `application/port/OperatorTenantAssignmentPort.java` + `OperatorTenantAssignmentPortImpl.java` (D1, reuse) ; `application/port/AdminOperatorPort.java#findByOidcSubject` (reuse) ; `domain/rbac/AdminOperator.java#isPlatformScope` (reuse, unchanged) ; `infrastructure/config/SecurityConfig.java` (add `@Order(0)` `/internal/**` chain) ; templates from **account-service** `infrastructure/config/{SecurityConfig,InternalApiFilter}.java` + `presentation/internal/*Controller.java` ; new: `presentation/internal/OperatorAssignmentCheckController.java`, the `/internal` security chain + `internalJwtDecoder`.

---

# Edge Cases

- **fail-CLOSED vs fail-SOFT split** (the single highest-risk point): the **admin assignment check** is fail-closed (admin down ⇒ deny, no token); the **account entitled_domains** derivation is fail-soft (account down ⇒ omit claim, token still issued). Two different downstreams, two opposite policies — assert both in tests; do NOT let the `AccountServiceUnavailableException` fail-soft pattern leak onto the admin call.
- **subject token is auth-service's own** — validate with the local `JwtDecoder`/JWKS (it issued it), not a foreign-JWKS validator. Reject expired/revoked/wrong-audience subject tokens → `invalid_grant`.
- **`'*'` platform-scope operator** assuming a specific tenant → `assigned=true` (sentinel grants all tenants); the assumed token still carries the **selected** concrete `tenant_id` (NOT `'*'`) + that tenant's `entitled_domains`. A `'*'` token must never be minted onto a domain-facing assumed token.
- **selected = operator's own home tenant** (single-assignment / net-zero) → assigned via the legacy home leg of `resolveEffectiveTenantScope`; token shape identical to today's login token.
- **`tenant_type` of the selected tenant** — resolve correctly (customer tenant). Do not blank it (`auth-service` fails closed on missing `tenant_type`).
- **no refresh token** for the assumed token — re-minted per selection; ensure the SAS path does not emit/persist a refresh token for this grant.
- **`audience`/`resource` absent or malformed** → `invalid_request` (400), no admin call.
- **recursion safety** — the `entitled_domains` lookup calls account-service with a `client_credentials` Bearer; that cc issuance must NOT re-enter the customizer's entitled_domains branch (already guarded — keep the guard).
- **dev/test bypass** — the admin `/internal/**` chain bypass (test/standalone profile) must NOT weaken production: production requires a valid GAP JWT (fail-closed), exactly as account-service.

# Failure Scenarios

- **assignment check fail-soft by mistake** → an unassigned operator mints a cross-customer token → **isolation breach**. ⇒ assignment gate fail-closed, covered by AC-2 + a dedicated "admin unavailable → deny" test.
- **union entitled_domains** (selected ∪ other assignments) → over-broad token, operator reaches customer B's domains inside an A-scoped token. ⇒ D3 selected-only, AC-3 test asserts no union.
- **assumed token `iss`/kid drift** from the login token → domain gates reject (or, worse, a second trust anchor) → federation break. ⇒ mint through the **same** `JwtGenerator`/JWKS; AC-5.
- **refresh token leaked for the assumed token** → long-lived multi-tenant credential, defeats "re-mint per selection". ⇒ no refresh; edge-case test.
- **console-bff/domain touched** → scope-lock break + ADR-017 D6 violation. ⇒ AC-9.
- **existing login/exchange path regressed** → main RED. ⇒ AC-6 (existing assertions unmodified) + CI GAP Integration authoritative.
- **CI-RED-at-merge** → main regression (CLAUDE.md 회귀 saga). ⇒ pre-merge `gh pr checks` must show 0 failing required checks; 3-dim verify before any close chore.

---

# Implementation Design Notes

- **Order**: contracts first (AC-7) → admin `/internal` endpoint + chain (the dependency auth calls) → auth `AdminAssignmentClient`/port → auth exchange converter/provider + customizer branch + SAS wiring → tests. Build admin side first so the auth IT can hit a real endpoint (or stub via WireMock/Testcontainers as the keystone IT does for account).
- **Reuse, do not duplicate** (ADR-014 lesson): the assumed token's claims go through `TenantClaimTokenCustomizer` (extend, don't fork) and the SAS `JwtGenerator` (don't hand-roll a signer). The admin client mirrors `AccountServiceClient` (don't invent a new HTTP stack). The admin `/internal` chain mirrors account-service (don't invent a new resource-server config).
- **SAS token-exchange grant** is supported by SAS 1.x (Spring Boot 3.4). If the built-in `OAuth2TokenExchangeAuthenticationProvider` wiring proves impractical for injecting the selected-tenant override into the customizer, the ADR-020 D2 **Option A fallback** (a dedicated auth-service mint path that still routes through `JwtGenerator`+customizer) is acceptable — **document the deviation in the PR** and keep the contract/claims identical. Do NOT silently hand-roll claim assembly.
- **`@Transactional` boundary**: per auth architecture.md, **no HTTP inside a transaction** — the admin assignment check + account entitled_domains lookups happen outside any tx (the SAS issuance path is not a DB-write tx anyway). Keep it that way (Hikari pinning).
- CI Linux GAP Integration (Testcontainers) authoritative; locally run `:apps:auth-service` + `:apps:admin-service` `compileJava`+`compileTestJava`+unit, then the integration slices. If `@InjectMocks` ctor deps change, `--rerun-tasks` once (stale-cache guard).
- 구현 = **Opus**.

---

# Notes

- ADR-020 § 3.3 **step 2** (D2+D3). Server-side GAP assume-tenant capability only — **D4 console switcher is step 3 (separate task, 새 세션 권장)**, **D6 step 4** cleanup after. dependency-correct base = this `origin/main` (post BE-326 `ee19ff8c`).
- **The two-policy split (fail-closed admin gate / fail-soft account entitled_domains) is the defining correctness property** — reviewers should check it first.
