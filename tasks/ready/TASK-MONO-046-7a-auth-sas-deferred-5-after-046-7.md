# Task ID

TASK-MONO-046-7a

# Title

GAP auth-service SAS deferred 5 IT — Cluster A (RT rotation/reuse/revoke 3) + Cluster C-2 (Microsoft 503 state-pollution 2) after 046-7 Option X

# Status

ready

# Owner

backend / qa

# Task Tags

- code
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-MONO-046-7](../in-progress/TASK-MONO-046-7-auth-service-sas-deferred-8.md) closed via **Option X** (partial recovery) after 8 CI cycles iterated on Cluster A with diminishing returns and a cycle-7→8 regression. Option X was triggered by TASK-MONO-046-7 spec § Failure Scenarios C: "6 cycle 후 통과 안 됨 → spec deviation 검토".

## Option X outcome

- **4 of 8 tests recovered** (PR #264):
  - Cluster B: `userinfo_validToken_returnsOidcClaims` PASS (`.oidcUserInfoEndpoint("/oauth2/userinfo")` setting)
  - Cluster C happy-path 3: `googleHappyPath` / `kakaoHappyPath` / `microsoftHappyPath` PASS (assertOutboxLoginMethod `payload.loginMethod` path fix)
- **5 tests deferred** to this task (TASK-MONO-046-7a):
  - Cluster A (3): `refreshTokenGrant_normalRotation`, `refreshTokenGrant_reuseDetected_returns400`, `authCode_revokeRefreshToken_introspectInactive`
  - Cluster C-2 (2): `microsoftPreferredUsernameFallback`, `microsoftExistingEmailAutoLink`

## Why deferral was necessary

### Cluster A — SAS public-client refresh_token grant (3 tests)

8 CI cycle iteration (TASK-MONO-046-7) explored multiple approaches to authenticate a public PKCE client for `grant_type=refresh_token` and `/oauth2/revoke`:

- Cycles 1-4: diagnose SAS stock `PublicClientAuthenticationConverter` scope (only handles PKCE auth-code `code_verifier` requests)
- Cycle 5: inject `OAuth2TokenGenerator` via method parameter to resolve shared-object null
- Cycles 6-8: swap `persistRotation` / `authorizationService.save()` order inside `SasRefreshTokenAuthenticationProvider`; add `@Transactional` on `AuthEventPublisher.publishTokenRefreshed`; revert+reapply multiple times

Cycle 7 → cycle 8 introduced a regression (revert of the order swap caused JTI duplicate key conflict). The root architectural issue: `SasRefreshTokenAuthenticationProvider` is **manually instantiated** inside the `authorizationServerSecurityFilterChain()` lambda — it has no `@Transactional` proxy, no `@Service` lifecycle, and no Spring AOP wrappers. `DomainSyncOAuth2AuthorizationService`, `OutboxWriter`, and domain JPA repositories all require a managed transaction boundary that is absent in manual instantiation context.

**Architectural rework required**: The provider must be redesigned as a `@Service @Transactional` bean, decoupled from the filter chain's lambda instantiation. The `authorizationServerSecurityFilterChain` method must retrieve it via DI (constructor param or `@Autowired`) rather than `new SasRefreshTokenAuthenticationProvider(...)`. This design is incompatible with the current "shared-object" pattern (where `OAuth2TokenGenerator` is only available after `SAS configurer init()`) and requires resolving the circular init dependency first.

### Cluster C-2 — Microsoft 503 state-pollution (2 tests)

`microsoftPreferredUsernameFallback` and `microsoftExistingEmailAutoLink` fail with HTTP 503 in some test orderings but pass in others. The `@DirtiesContext(AFTER_CLASS)` annotation (added in 046-1 PR #218) is not sufficient: state pollution is observed even within the same class across test orderings. Hypothesis: the Resilience4j circuit-breaker (`AccountServiceClient`) opens after the `microsoftProvider5xx` test (which stubs a 500 response) and remains open when the Fallback/AutoLink tests run next — `@BeforeEach` resets WireMock stubs but does not reset the circuit-breaker state.

**Investigation required**: Confirm that circuit-breaker state persists across `@BeforeEach` resets. Then either (a) reset the circuit-breaker in `@BeforeEach`, (b) reorder tests so the 5xx scenario runs last, or (c) disable the circuit-breaker entirely for integration tests via `resilience4j.circuitbreaker.configs.default.slidingWindowSize=1000` in `application-test.yml`.

---

# Scope

## In Scope

### Cluster A — architectural rework

- Redesign `SasRefreshTokenAuthenticationProvider` as a `@Service @Transactional` bean
- Resolve `OAuth2TokenGenerator` circular init dependency (decouple from filter chain lambda)
- Re-enable and achieve PASS for 3 deferred methods (Order=2/4 in OAuth2RefreshTokenIntegrationTest + Order=4 in OAuth2RevokeIntrospectIntegrationTest)
- Verify no regression on `token_wrongCodeVerifier_returns400` + all other auth-service IT

### Cluster C-2 — circuit-breaker state-pollution

- Diagnose whether Resilience4j circuit-breaker state from `microsoftProvider5xx` test bleeds into subsequent tests in `OAuthLoginIntegrationTest`
- Apply minimal fix (circuit-breaker reset or test-order guard or property override)
- Re-enable and achieve PASS for `microsoftPreferredUsernameFallback` + `microsoftExistingEmailAutoLink`

## Out of Scope

- TASK-MONO-046-8 (consumer-pipeline) — separate task
- Changes to other auth-service tests recovered in 046-7 / 046-1
- New OAuth2 endpoints or flows not already tested

---

# Acceptance Criteria

## Tests that must PASS after this task

1. `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation` (Order=2) PASS
2. `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_reuseDetected_returns400` (Order=4) PASS
3. `OAuth2RevokeIntrospectIntegrationTest.authCode_revokeRefreshToken_introspectInactive` (Order=4) PASS
4. `OAuthLoginIntegrationTest.microsoftPreferredUsernameFallback` PASS
5. `OAuthLoginIntegrationTest.microsoftExistingEmailAutoLink` PASS

## No regression

6. `OAuth2AuthCodePkceIntegrationTest.token_wrongCodeVerifier_returns400` — MUST remain PASS
7. 4 tests recovered by 046-7 Option X — MUST remain PASS (`userinfo_validToken_returnsOidcClaims`, `googleHappyPath`, `kakaoHappyPath`, `microsoftHappyPath`)
8. All other auth-service IT — 0 regression

## CI

9. main CI `Integration (GAP)` Job: auth-service reaches 60/60 PASS (0 FAIL, 0 DISABLED from this task's scope)
10. PR description documents root cause diagnosis for Cluster A architectural change + Cluster C-2 circuit-breaker fix

---

# Related Specs

- [TASK-MONO-046-7](../in-progress/TASK-MONO-046-7-auth-service-sas-deferred-8.md) — **direct predecessor** (this task's `@Disabled` annotations were added in 046-7 Option X PR #264)
- [TASK-MONO-046-1](../done/TASK-MONO-046-1-auth-sas-12.md) — original partial recovery PR #235
- `projects/global-account-platform/specs/services/auth-service/`

---

# Related Contracts

- None (SAS internal behaviour + test-only changes — no external contract changes)

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/SasRefreshTokenAuthenticationProvider.java`
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/AuthorizationServerConfig.java`
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OAuth2RefreshTokenIntegrationTest.java`
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OAuth2RevokeIntrospectIntegrationTest.java`
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OAuthLoginIntegrationTest.java`
- Possibly: `projects/global-account-platform/apps/auth-service/src/main/resources/application-test.yml` (Resilience4j circuit-breaker override)

---

# Edge Cases

1. **Cluster A — token generator circular init**: `SasRefreshTokenAuthenticationProvider` as a `@Service @Bean` will require `OAuth2TokenGenerator` injected via constructor. `OAuth2TokenGenerator` is declared as a `@Bean` in `AuthorizationServerConfig`. Spring must resolve this without a circular dependency. If circularity remains (SAS configurer imports TokenGenerator from `HttpSecurity` shared objects which requires the SAS filter chain bean), use `@Lazy` injection or a `@Configuration(proxyBeanMethods = false)` split.

2. **Cluster A — PKCE regression guard**: The `PublicClientNonPkceAuthenticationConverter` deleted in 046-7 Option X must NOT be reintroduced. Any new public-client authentication extension must pass `token_wrongCodeVerifier_returns400` without regression.

3. **Cluster C-2 — state-pollution vs test ordering**: The circuit-breaker hypothesis depends on `microsoftProvider5xx` running before the Fallback/AutoLink tests. JUnit 5 does not guarantee ordering within a class unless `@TestMethodOrder` is used. Check whether the test class uses `@TestMethodOrder` — if not, `@Order` annotations may be needed to isolate the 5xx test.

4. **Cluster C-2 — @BeforeEach WireMock reset**: The existing `@BeforeEach resetStubs()` calls `wireMock.resetAll()` but does not reset Resilience4j state. A `@BeforeEach` call to `circuitBreakerRegistry.circuitBreaker("accountServiceClient").reset()` (if the registry is `@Autowired`) should clear the state without requiring `@DirtiesContext` per method.

5. **Cycle-6 vs cycle-8 evidence** (from PR #264 cycles): in cycle 6 the `persistRotation`-before-`authorizationService.save()` order swap passed locally but regressed in CI with a JTI duplicate key error. In cycle 8, the revert of that swap combined with `@Transactional` on `publishTokenRefreshed` failed with a different NPE. The interaction between `DomainSyncOAuth2AuthorizationService` (which writes a `RefreshToken` row on `save()`) and `SasRefreshTokenAuthenticationProvider`'s `persistRotation()` (which also writes a `RefreshToken` row) is the core race condition. This must be resolved at the transaction boundary level, not by ordering calls.

---

# Failure Scenarios

## A. Cluster A: circular init cannot be resolved

If `SasRefreshTokenAuthenticationProvider` as `@Service` creates an unresolvable Spring circular dependency with the SAS filter chain — consider a `@Configuration` split where the provider bean is declared in a separate `SasRefreshTokenConfig.java` that does not import `AuthorizationServerConfig`. Use `@Lazy` injection of `AuthorizationServerSettings` if needed.

## B. Cluster A: DomainSync + persistRotation double-write persists after architectural rework

If `DomainSyncOAuth2AuthorizationService.save()` and `SasRefreshTokenAuthenticationProvider.persistRotation()` continue to conflict even with a transactional provider bean — add an idempotency guard in `persistRotation()` that checks `refreshTokenRepository.findByJti(newJti).isPresent()` before inserting, mirroring the existing guard in `DomainSyncOAuth2AuthorizationService.save()` at line 99.

## C. Cluster C-2: circuit-breaker registry not accessible in test context

If `CircuitBreakerRegistry` is not `@Autowired`-able in the test class (e.g., not exposed as a bean) — override `resilience4j.circuitbreaker.instances.accountServiceClient.slidingWindowSize=1` and `permittedNumberOfCallsInHalfOpenState=100` in `application-test.yml` so the breaker resets to CLOSED after every `@BeforeEach` via the reset mechanism, or set `circuitbreaker.instances.accountServiceClient.enabled=false` entirely for the test profile.

## D. 6 cycle 후 통과 안 됨 → 046-7b 분리

If this task (046-7a) reaches 6 CI cycles without resolving Cluster A, trigger the same Option X as 046-7: defer remaining Cluster A tests to TASK-MONO-046-7b with a spec documenting the further root-cause evidence gathered.

---

# Test Requirements

- 5 `@Disabled` IT methods re-enabled + all PASS
- `token_wrongCodeVerifier_returns400` + 4 046-7-recovered tests remain PASS
- All auth-service integrationTest PASS (0 FAIL, 0 DISABLED in scope)
- main CI `Integration (GAP)` Job SUCCESS

---

# Definition of Done

- [ ] Cluster A architectural rework complete (`SasRefreshTokenAuthenticationProvider` as `@Service @Transactional`)
- [ ] Cluster C-2 circuit-breaker state-pollution root-caused and fixed
- [ ] 5 `@Disabled` annotations removed from the 3 test classes
- [ ] local integrationTest PASS confirmed (or CI as proxy if Docker unavailable on dev machine)
- [ ] main CI `Integration (GAP)` Job SUCCESS (60/60 PASS)
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — SAS 1.4 Spring bean lifecycle, circular dependency resolution, Resilience4j circuit-breaker internals. 8-cycle evidence in 046-7 confirms this requires deep Spring Security / SAS architectural understanding.
- **분량 추정**: large (Cluster A requires rearchitecting the provider bean wiring; Cluster C-2 requires Resilience4j diagnosis).
- **dependency**:
  - `선행`: TASK-MONO-046-7 Option X PR #264 merged (this task's `@Disabled` come from that PR).
  - `병렬`: TASK-MONO-046-8 (consumer-pipeline deeper investigation).
  - `후속`: both 046-7a + 046-8 merged → main `Integration (GAP)` Job 전체 GREEN (60 PASS / 0 DISABLED) milestone.
