# Task ID

TASK-MONO-046-7a

# Title

GAP auth-service SAS refresh_token grant rotation — 2 IT methods deferred from TASK-MONO-046-7 (architectural rework required)

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
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-MONO-046-7](../in-progress/TASK-MONO-046-7-auth-service-sas-deferred-8.md) PR #264 closed with **6 of 8** originally-deferred IT methods recovered:

- ✅ Cluster A revoke (1) — `OAuth2RevokeIntrospectIntegrationTest.authCode_revokeRefreshToken_introspectInactive` (PASS via custom `PublicClientNonPkceAuthenticationConverter` + `Provider`)
- ✅ Cluster B userinfo (1) — `userinfo_validToken_returnsOidcClaims` (PASS via `oidcUserInfoEndpoint("/oauth2/userinfo")`)
- ✅ Cluster C OAuth callback (4) — google / kakao / microsoftHappyPath / microsoftPreferredUsernameFallback (PASS via `loginMethod` payload-path fix + cycle-6 timing)

**2 methods remain deferred** with `@Disabled("TASK-MONO-046-7a: ...")`:

- `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation` (Order=2)
- `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_reuseDetected_returns400` (Order=4 — cascades on Order=2)

PR #264 iterated 8 CI cycles on Cluster A's refresh_token grant rotation path with cycles 6→8 producing **regressing** result counts (cycle 6: 2 fails / cycle 8: 6 fails). [TASK-MONO-046-7 § Failure Scenarios C](../in-progress/TASK-MONO-046-7-auth-service-sas-deferred-8.md) ("6 cycle 후 통과 안 됨 → spec deviation 검토") triggered the partial-recovery deferral.

This task addresses the architectural rework needed to make the 2 refresh_token rotation tests pass without regressing the 6 already-recovered tests.

---

# Scope

## In Scope

### 1. Architectural rework of `SasRefreshTokenAuthenticationProvider`

The provider is currently `new`-instantiated inside `AuthorizationServerConfig.buildRefreshTokenProvider(http, ...)` (a private @Configuration helper), so Spring AOP cannot proxy it for `@Transactional` and the `authenticate()` method runs across multiple DB writes (`authorizationService.save`, `persistRotation` repo writes, `authEventPublisher.publishTokenRefreshed → OutboxWriter.save`) without a single transaction boundary. This is the root cause of:

- `DataIntegrityViolationException: Duplicate entry [...] for key 'refresh_tokens.idx_rt_jti'` (PR #264 cycle 5) — `DomainSyncOAuth2AuthorizationService.save()` and `persistRotation()` both INSERT the new RT row, the second collides on the unique JTI index.
- `InvalidDataAccessApiUsageException: No EntityManager with actual transaction available for current thread` (PR #264 cycles 6–8) — outbox publisher's `EntityManager.persist()` requires a Spring write transaction and the provider isn't running in one.

### Recommended refactor

Convert `SasRefreshTokenAuthenticationProvider` into a Spring-managed `@Component` with `@Transactional` on the `authenticate()` method. Wire it into the SAS chain via `OAuth2AuthorizationServerConfigurer`'s `tokenEndpoint().authenticationProvider(...)` Customizer using a deferred-resolution Customizer (apply `getSharedObject(OAuth2TokenGenerator.class)` AFTER SAS init has run). PR #264 cycle 5 (`b1fd59a6`) used direct DI of the @Bean `oAuth2TokenGenerator` to side-step the timing issue, but that exposed the next layer (the rotation flow's transaction boundary) — both layers need the refactor together.

### 2. Re-enable + PASS the 2 deferred tests

- `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation` (Order=2)
- `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_reuseDetected_returns400` (Order=4)

### 3. Regression guard (CRITICAL)

Zero regression on the 6 tests recovered by 046-7:

- 1 Cluster A — `authCode_revokeRefreshToken_introspectInactive`
- 1 Cluster B — `userinfo_validToken_returnsOidcClaims`
- 4 Cluster C — google / kakao / microsoftHappyPath / microsoftPreferredUsernameFallback

PR #264 cycle 7→8 demonstrated that surface-level fixes (UPSERT in persistRotation, @Transactional on publishTokenRefreshed) cause cross-cluster regression. Architectural rework, not surface fixes.

## Out of Scope

- Other 046-7 deferred clusters (none — A revoke + B + C all recovered)
- 046-1 partially-recovered methods (5 already recovered in PR #235 / 046-1)
- New SAS endpoints (Add only what's required for transactional rotation; no scope creep)
- 046-8 consumer-pipeline (separate ready/ task)

---

# Acceptance Criteria

## 통과

1. `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation` `@Disabled` 제거 + PASS
2. `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_reuseDetected_returns400` `@Disabled` 제거 + PASS

## 회귀 없음

3. `authCode_revokeRefreshToken_introspectInactive` — MUST stay PASS
4. `userinfo_validToken_returnsOidcClaims` — MUST stay PASS
5. `googleHappyPath`, `kakaoHappyPath`, `microsoftHappyPath`, `microsoftPreferredUsernameFallback` — MUST stay PASS
6. `token_wrongCodeVerifier_returns400` (PKCE guard from 046-7 § Edge Case #1) — MUST stay PASS
7. Other auth-service IT — zero regression (60/60 PASS expected post-fix)

## CI

8. main CI `Integration (GAP)` Job: 60/60 PASS / 0 FAIL / 0 DISABLED for these 2 methods (other unrelated `@Disabled` may exist).
9. PR description includes:
   - Architectural rework approach taken (Spring proxy / @Service refactor / TransactionTemplate)
   - Cycle count + minutes (target: ≤ 3 cycles given the analysis above)
   - 6 unrelated tests verified green (regression guard)

---

# Related Specs

- [TASK-MONO-046-7](../in-progress/TASK-MONO-046-7-auth-service-sas-deferred-8.md) — direct predecessor; PR #264's commit history is the architectural debugging trail.
- `projects/global-account-platform/specs/services/auth-service/`

---

# Related Contracts

- 없음 (test-only + production code refactor; no public contract changes)

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/SasRefreshTokenAuthenticationProvider.java` (refactor to @Component @Transactional)
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/AuthorizationServerConfig.java` (provider injection point)
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/DomainSyncOAuth2AuthorizationService.java` (potentially: rotation-aware flag to avoid DOMain insert race)
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OAuth2RefreshTokenIntegrationTest.java` (remove 2 `@Disabled`)

---

# Edge Cases

1. **JTI duplicate insert** — both `DomainSyncOAuth2AuthorizationService.save()` and `SasRefreshTokenAuthenticationProvider.persistRotation()` INSERT the new RT row. The DomainSync-inserted row has `rotated_from=null`; the test asserts `rotated_from = oldTokenValue`. Resolution path tried in PR #264 cycle 7 (UPSERT-aware persistRotation that UPDATEs rotated_from instead of INSERTing) had its own transaction-boundary issue. The rework should put both writes (or the single canonical write) in one tx — likely the `@Transactional` provider service approach.

2. **OAuth2TokenGenerator shared-object timing** — `http.getSharedObject(OAuth2TokenGenerator.class)` returns null when called inside the `.with(authorizationServerConfigurer, configurer -> ...)` Customizer because the Customizer runs synchronously at `.with()`-time, before any sub-configurer's `init()` has populated the shared-object map. PR #264 cycle 5 swapped to direct `@Bean` DI which works at the Spring-bean-construction level. KEEP that pattern in the rework.

3. **Outbox EntityManager persist** — `AuthEventPublisher.publishTokenRefreshed()` → `OutboxWriter.save()` → `EntityManager.persist()` requires a Spring write transaction. Currently AuthEventPublisher has no `@Transactional`. PR #264 cycle 8 added `@Transactional` to `publishTokenRefreshed` but produced regressions when combined with cycle-6 order swap. The rework should keep AuthEventPublisher methods inside the calling provider's transaction, OR add `@Transactional(propagation=REQUIRES_NEW)` for outbox isolation.

4. **PKCE wrong-code_verifier guard** — `token_wrongCodeVerifier_returns400` MUST stay PASS. The Cluster A converter (`PublicClientNonPkceAuthenticationConverter`) deliberately returns null when `code_verifier` is present so the stock SAS converter handles PKCE auth-code requests unchanged. Rework should preserve that branch.

5. **Test order pollution** — PR #264 cycle 6 had Cluster C tests passing only when Cluster A's specific failure path didn't pollute downstream state. The rework should make Cluster A succeed cleanly; if it still fails, the failure mode must not pollute Resilience4j circuit breakers / Spring SecurityContext / WireMock journal in a way that affects subsequent OAuthLoginIntegrationTest runs.

---

# Failure Scenarios

## A. Refactor invalidates 6 already-recovered tests

If converting `SasRefreshTokenAuthenticationProvider` to @Component breaks the cluster A revoke or cluster C OAuth tests — abort, restore previous state, document the cross-test interaction in this spec, and explore a TransactionTemplate-only path that doesn't change the bean lifecycle.

## B. Refactor still NPEs / dups

If 2 cycles of refactor work yield no progress, defer further. Acceptable: keep both 046-7 deferred tests `@Disabled`, mark task as "investigation continued — production architectural debt acknowledged" and reopen with a deeper TASK-MONO-046-7b narrowed to ONE specific failure mode.

## C. Spring Authorization Server upstream upgrade resolves it

Track SAS upstream releases (1.4 → 1.5+). If a future SAS version simplifies the public-client refresh_token grant + custom AuthenticationProvider integration, the upgrade may render this task obsolete. In that case, supersede this task with an SAS-upgrade task.

---

# Test Requirements

- 2 IT methods `@Disabled` 제거 + PASS
- `token_wrongCodeVerifier_returns400` PKCE guard verified
- main CI `Integration (GAP)` Job 60/60 SUCCESS

---

# Definition of Done

- [ ] Architectural rework approach documented in PR description
- [ ] `SasRefreshTokenAuthenticationProvider` rotation runs in single Spring write transaction
- [ ] 2 deferred IT methods PASS without regression on 6 recovered + PKCE guard
- [ ] CI `Integration (GAP)` Job SUCCESS
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — SAS 1.4 internal authentication provider lifecycle, transaction boundary refactor, custom rotation logic.
- **분량 추정**: small — single architectural fix on a clearly-scoped path. Most of the analysis is already in PR #264's commit messages and `TASK-MONO-046-7-auth-service-sas-deferred-8.md`.
- **dependency**:
  - `선행`: TASK-MONO-046-7 (PR #264) merged. Without 046-7's 6 recovered tests on main, this task has no baseline.
  - `병렬`: TASK-MONO-046-8 (consumer-pipeline) — independent.
- **CI economy**: target ≤ 3 CI cycles. PR #264 burnt 9 cycles on Cluster A — clearly architectural, not surface-level.
- **PR #264 commit history reference** (architectural debugging trail):
  - cycle 1 `41ffebae` — added `PublicClientNonPkceAuthenticationConverter` + `Provider` (revoke fix, kept)
  - cycle 5 `b1fd59a6` — direct DI of `OAuth2TokenGenerator` (NPE: null tokenGenerator → resolved)
  - cycle 6 `b86302d1` — order swap of persistRotation / save (best-known state, 2 fails — kept)
  - cycle 7 `05ab3203` — UPSERT in persistRotation (regressed to 6 fails — REVERTED)
  - cycle 8 `9958c2c5` — @Transactional on publishTokenRefreshed (regressed Cluster C — REVERTED)
