# TASK-MONO-422 — promote the internal-endpoint scope discriminator to a shared library and close the remaining IAM `/internal/**` shared-issuer stragglers (auth-service, security-service)

- **Type**: TASK-MONO (monorepo-level — touches `libs/` shared path + 3 iam-platform services in one atomic cross-project PR)
- **Status**: done
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (security authz, cross-cutting shared-lib, multi-service)

## Goal

Three IAM services expose `/internal/**` resource servers that all pin only signature + timestamps +
issuer against the **single shared** `auth-service` SAS — which mints both system (`client_credentials`)
and user (`authorization_code`) tokens. So `.authenticated()` / `jwtDecoder.decode()` success does **not**
distinguish a system credential from a valid user token: any valid-issuer token passes a "system credential
only" internal contract. account-service was fixed in isolation (TASK-BE-514) with a local
`RequiredScopeValidator` requiring the `internal.invoke` scope. security-service and auth-service are the
remaining stragglers with the identical gap.

Rather than copy the validator a third time (the "fix the same defect service-by-service" smell — a rule
that lives in one service is effectively no rule), **promote `RequiredScopeValidator` to `libs/java-web`**
(project-agnostic — a pure `OAuth2TokenValidator<Jwt>`) as the single canonical home, refactor
account-service to consume it, and wire security-service + auth-service to the shared validator — closing all
three IAM `/internal/**` stragglers in one atomic PR.

## AC-0 — Findings (audit, verified 2026-07-17)

- **account-service** (`SecurityConfig.internalJwtDecoder()`) — ✅ already enforces `internal.invoke`
  (TASK-BE-514, local `RequiredScopeValidator`). Refactored here to consume the shared class.
- **security-service** — `InternalJwtDecoderConfig.internalJwtDecoder()` = `NimbusJwtDecoder.withJwkSetUri`
  + `setJwtValidator(createDefaultWithIssuer(issuer))` only; consumed by the custom `InternalAuthFilter`
  (403 PERMISSION_DENIED shape). No `sub`/scope/audience discriminator. A valid user token passes
  `jwtDecoder.decode()` → reaches `/internal/security/**` business logic. **Blast radius**: cross-tenant
  read of any account's login-history + suspicious-events (security analytics leak; read-only, no mutation).
- **auth-service** — `SecurityConfig.internalJwtDecoder()` = same bare `createDefaultWithIssuer` shape;
  `.requestMatchers("/internal/**").authenticated()` (with a `/internal/auth/jwks` permitAll carve-out).
  No discriminator. **Blast radius**: `/internal/auth/**` credential/action endpoints.
- **Shared issuer** confirmed = `http://localhost:8081` (the one SAS) in all three.
- **Non-breaking**: the sole real caller of both security + auth `/internal/**` is admin-service
  (`admin-service-client`), registered with exactly `["internal.invoke"]` (`V0019`) and sending no narrowing
  scope → its token carries the scope. E2E `InternalWorkloadAuthE2ETest` mints with explicit
  `scope=internal.invoke`. → scope enforcement is non-breaking.
- **Vacuous test face**: security + auth internal ITs run under the `test` profile → filter bypass ON
  (decoder never exercised); slice/filter tests mock the decoder. No test mints a user token and asserts
  rejection. → verify the actual validator chain, not a bypassed one.

## Scope

- **In**:
  - `libs/java-web`: new project-agnostic `com.example.web.security.RequiredScopeValidator` + unit test;
    `compileOnly`/`testImplementation` `spring-security-oauth2-jose` (mirrors the existing `BearerJwtOpenApi`
    `compileOnly swagger` pattern — never reaches the reactive gateway runtime classpath).
  - account-service: delete local `RequiredScopeValidator` (+ its unit test, moved to lib); consume shared.
  - security-service: wrap `internalJwtDecoder` validator with the shared scope gate (fail-closed → existing
    403); wiring test asserting a scope-less token is rejected by the real chain.
  - auth-service: same wrap on `internalJwtDecoder` (fail-closed → existing 401 entry point); wiring test.
  - Contracts: pin the `internal.invoke` requirement on the security + auth internal Authentication sections.
- **Out**: order-service (ecommerce uses a `sub` allow-list — different discriminator, not this scope class);
  auto-configured fleet-wide hardening (larger architectural change, separate ADR); changing the shared-issuer
  model; the `product-service-client` registration (separate ecommerce task).

## Acceptance Criteria

- **AC-1**: `com.example.web.security.RequiredScopeValidator` is project-agnostic (no service names / domain
  entities — HARDSTOP-03), returns success iff the token's `scope` (space-delimited string or list; `scp`
  fallback) contains the configured required scope; blank required scope → fail-closed (reject all).
- **AC-2**: `libs/java-web` compiles with `spring-security-oauth2-jose` as `compileOnly` (not exposed to the
  reactive gateway runtime classpath) + `testImplementation` for the lib unit test. Charter preserved
  (no spring-web / servlet added to java-web).
- **AC-3**: account-service consumes the shared validator; its local copy + local unit test are deleted; its
  existing wiring test (`SecurityConfigInternalValidatorTest`) stays green.
- **AC-4**: security-service `internalJwtDecoder` composes `[createDefaultWithIssuer(issuer),
  RequiredScopeValidator(internal.invoke)]`; a scope-less/valid-issuer token is rejected by the **actual**
  chain (wiring test), surfacing as the existing 403 PERMISSION_DENIED via `InternalAuthFilter`.
- **AC-5**: auth-service `internalJwtDecoder` composes the same; scope-less token rejected by the actual
  chain (wiring test), surfacing as the existing 401 entry point.
- **AC-6**: required scope env-overridable per service (default `internal.invoke`); fail-closed on blank.
- **AC-7**: Mutation check — breaking the shared `validate()` to always succeed turns the guard tests
  (lib + all three service wiring tests) RED. Recorded in the PR.
- **AC-8**: `./gradlew :libs:java-web:test` + the three services' fast lanes green. Contracts updated.

## Related Specs / Contracts

- `libs/java-web/build.gradle` (charter: MONO-044a framework-agnostic split)
- ecommerce precedent `SystemClientSubjectValidator` (BE-505, different discriminator); account
  `RequiredScopeValidator` (BE-514, the promoted class)
- `V0019__seed_internal_service_workload_clients.sql` (§ `internal.invoke`, BE-319 declared-not-shipped)
- security + auth internal contracts (Authentication § — pin scope)

## Edge Cases / Failure Scenarios

- `scope` as space-delimited String vs List; `scp` fallback; blank/missing → reject.
- Blank required-scope config → fail-closed (a wiring error must not reopen the gap).
- `compileOnly` oauth2 leaking to the reactive gateway → verified absent (gateway does not depend on the
  validator; `compileOnly` never reaches consumer runtime).
- Validator promoted but a service not actually wired → each service has a wiring test on its real decoder chain.
- Breaking a legitimate caller → all callers verified to carry `internal.invoke` (AC-0).
