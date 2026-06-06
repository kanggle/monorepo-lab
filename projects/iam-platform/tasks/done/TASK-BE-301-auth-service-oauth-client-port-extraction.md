# Task ID

TASK-BE-301

# Title

auth-service OAuth-client port extraction (behavior-neutral application→infrastructure.oauth leak removal — completes Signal 4 sibling)

# Status

done

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: nothing (self-contained GAP auth-service internal refactor; lands on top of `TASK-BE-300` which closed the SocialIdentity half of the same `architecture.md` L169 leak).
- **follows pattern**: `TASK-BE-300` (SocialIdentity port) + `TASK-BE-295` (TokenBlacklist/LoginAttemptCounter tenant hoist) — identical "behavior-neutral port hoist, adapter/relocation = delegation only, byte-identical test assertions" discipline. This is the explicit `infrastructure.oauth.*` out-of-scope item recorded in `project_refactor_sweep_status` (BE-295/BE-300 비차단 관찰 (b)).
- **prerequisite for**: nothing. Completing this brings **GAP auth-service `application → infrastructure` import = a true 0** (BE-300 closed only the SocialIdentity import; the OAuth-client imports are the remaining set).
- **spec-first**: **no spec change** — `specs/services/auth-service/architecture.md` L169 ("❌ `application`에서 …infra… 직접 사용 — 반드시 `domain`의 포트 인터페이스 경유") + L149-162 Allowed/Forbidden + L57-96 Internal Structure already declare the rule this enforces (BE-295/BE-300 "spec 무편집, 룰 기선언" posture). No contract/schema/event/HTTP change.

---

# Goal

`application/OAuthLoginUseCase` (wildcard `import com.example.auth.infrastructure.oauth.*`), `application/OAuthLoginTransactionalStep`, and `application/command/OAuthCallbackTxnCommand` depend directly on `infrastructure.oauth.*` (`OAuthClient`, `OAuthClientFactory`, `OAuthProperties`, `OAuthUserInfo`, `OAuthProviderException`) — the same `architecture.md` L169 violation class TASK-BE-300 closed for `SocialIdentity`. This is the **last application→infrastructure leak** in GAP auth-service.

Relocate the abstractions to the correct layers and introduce ports for the two infra dependencies that are genuinely behind a boundary (the provider HTTP client selector, and the Spring `@ConfigurationProperties` config), so the OAuth login/callback flow's observable behavior is **byte-identical** (same provider HTTP, same redirect-URI exact-match allowlist, same authorization-URL string, same exception propagation, same `@Transactional` boundary, same results/events). `OAuthProperties` (Spring `@ConfigurationProperties`) and the 3 concrete provider clients + `OidcJwksVerifier` stay in infrastructure as adapters.

After this task: `grep "import com.example.auth.infrastructure" auth-service/.../application/` = **0**; GAP auth-service application layer is fully framework/infra-free.

# Scope

## In Scope

### WI-1 — value + exception relocation (pure package move, import-only diff)

- `infrastructure.oauth.OAuthUserInfo` → **`domain.oauth.OAuthUserInfo`** (record; only depends on `domain.oauth.OAuthProvider` — natural domain sibling). `git mv`-style move; update every referencer (OAuthLoginUseCase, OAuthLoginTransactionalStep, OAuthCallbackTxnCommand, the `OAuthClient` port, the 3 concrete clients, 2 app tests).
- `infrastructure.oauth.OAuthProviderException` → **`application.exception.OAuthProviderException`** (sibling of `InvalidOAuthRedirectUriException`/`InvalidOAuthStateException`/`OAuthEmailRequiredException`; thrown by the clients, caught/rethrown by the use case). Update referencers (OAuthLoginUseCase, 3 concrete clients, `presentation/exception/AuthExceptionHandler` if it maps it, `OAuthClient` javadoc).

### WI-2 — OAuthClient port relocation

- `infrastructure.oauth.OAuthClient` → **`application.port.OAuthClient`** (sibling of `AccountServicePort`/`TokenGeneratorPort`/`EmailSenderPort`). Single method `exchangeCodeForUserInfo` unchanged. The 3 concrete clients `implements com.example.auth.application.port.OAuthClient` (import update only; bodies unchanged).

### WI-3 — OAuthClientProvider port (factory abstraction)

- NEW **`application.port.OAuthClientProvider`** — `OAuthClient getClient(OAuthProvider provider)`. Infra `OAuthClientFactory implements OAuthClientProvider` (existing switch body unchanged, `@Override` added). `OAuthLoginUseCase` injects `OAuthClientProvider` (port) instead of the concrete `OAuthClientFactory`.

### WI-4 — OAuthProviderConfigPort (the Spring `@ConfigurationProperties` boundary)

- NEW value **`application.port.OAuthProviderConfig`** — framework-free record carrying exactly what the use case reads: `clientId`, `authUri`, `scopes`, `defaultRedirectUri`, `allowedRedirectUris` (`List<String>`).
- NEW port **`application.port.OAuthProviderConfigPort`** — `OAuthProviderConfig get(OAuthProvider provider)`.
- NEW infra adapter **`infrastructure.oauth.OAuthPropertiesConfigAdapter`** `@Component implements OAuthProviderConfigPort` — switch on provider over `OAuthProperties.getGoogle()/getKakao()/getMicrosoft()`, mapping to `OAuthProviderConfig`, reproducing `ProviderProperties.resolveAllowedRedirectUris()` **exactly** (`allowedRedirectUris` if non-null & non-empty → copy; else `[redirectUri]` if redirectUri non-null; else `[]`). `OAuthProperties` itself is **unchanged** (stays `@ConfigurationProperties`, infra).
- `OAuthLoginUseCase`: replace `oAuthProperties` field + `getProviderProperties(provider)` switch with `oAuthProviderConfigPort.get(provider)`; `validateRedirectUri` uses `config.allowedRedirectUris()` (same exact-string `.contains` check, no normalization); `buildAuthorizationUrl` uses `config.authUri()/clientId()/scopes()`; `authorize`/`callback` redirect default uses `config.defaultRedirectUri()`. Orchestration/branching/URL-encoding all unchanged.

### WI-5 — test parity (byte-identical assertions)

- `OAuthLoginUseCaseTest` — mock `OAuthClientProvider` (was `OAuthClientFactory`) + `OAuthProviderConfigPort` (was `OAuthProperties.ProviderProperties` setup → stub `OAuthProviderConfig` records with the same values); imports of `OAuthUserInfo`/`OAuthClient`/`OAuthProviderException` from new packages. **Every assertion unchanged** (authorization URL contains `client_id=test-google-client-id`, redirect-not-in-allowlist throws `InvalidOAuthRedirectUriException`, trailing-slash exact-match reject, legacy single-redirect fallback, blank→default fallback, all callback ordering/HTTP-before-txn assertions).
- `OAuthLoginTransactionalStepTest` — `OAuthUserInfo` import update only (USER_INFO usage unchanged).
- Infra client tests (`GoogleOAuthClientTest`, `KakaoOAuthClientUnitTest`, `MicrosoftOAuthClientTest`, `OAuthClientFactoryTest`, `OAuthClientBeanRegistrationTest`) — import updates only for the moved `OAuthClient`/`OAuthUserInfo`/`OAuthProviderException`; behavior assertions unchanged. `OAuthClientFactoryTest` additionally exercises `OAuthClientProvider` (factory now implements it) — same returned-client assertions.

## Out of Scope

- `infrastructure.oauth2.*` (Spring Authorization Server: `JpaRegisteredClientRepository`, `OAuthClientMapper`, `AuthorizationServerConfig`, customizers, …) — a **different package**, not part of this leak; untouched.
- `OidcJwksVerifier` + the 3 concrete provider clients' HTTP/JWKS logic + `OAuthProperties` field set — unchanged (legitimate infra; only their package coordinates of moved types update).
- Renaming `OAuthClient` → `OAuthClientPort` or any cosmetic rename — keep names (minimal churn; matches `AccountServicePort` *location* convention without forcing a suffix rename of an existing widely-referenced type).
- Any `application/port/AccountServicePort.java` / `application/result/AccountProfileResult.java` change — verified false-positives (they reference `infrastructure.oauth2`/oauth-prefixed names, not the social `infrastructure.oauth.*`).
- `architecture.md` / data-model / contract / event edits — rule pre-declared (BE-295/BE-300 posture); no schema/HTTP/event surface touched.
- Splitting into multiple PRs — one cohesive behavior-neutral PR (relocations = import-only; new ports = delegation; config adapter = field-mapping); reviewable as a unit like BE-295 (11 files).

# Acceptance Criteria

- **AC-1 (leak fully closed)**: `grep -rn "import com.example.auth.infrastructure" projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/application/` → **0**. (Stronger than BE-300's SocialIdentity-only grep — *no* `infrastructure.*` import remains anywhere under `application/`.)
- **AC-2 (behavior-neutral — core gate)**: byte-identical observable behavior. (a) `exchangeCodeForUserInfo` provider HTTP unchanged (concrete clients only had imports moved); (b) redirect-URI validation = same exact-string allowlist (`allowedRedirectUris` non-empty ? copy : `[redirectUri]` : `[]`), same `InvalidOAuthRedirectUriException` on miss, no normalization/trailing-slash tolerance; (c) authorization URL = byte-identical string (`authUri ? client_id=… & redirect_uri=… & response_type=code & scope=<scopes ',' →' '> & state=…`, same `URLEncoder`); (d) `OAuthProviderException` propagation, account-status guard, `@Transactional` boundary, `OAuthLoginResult`, emitted events unchanged.
- **AC-3 (relocation/adapter = mechanical only)**: `OAuthUserInfo`/`OAuthProviderException`/`OAuthClient` diffs = package line + import updates only (no member change). `OAuthClientFactory` diff = `implements OAuthClientProvider` + `@Override` only (switch body byte-identical). `OAuthPropertiesConfigAdapter` = pure field mapping reproducing `resolveAllowedRedirectUris()` verbatim. `OAuthProviderConfig` is framework-free (no `org.springframework`/`jakarta`).
- **AC-4 (precedent conformance)**: new ports in `application.port` (sibling of `AccountServicePort`); `OAuthUserInfo` in `domain.oauth` (sibling of `OAuthProvider`); `OAuthProviderException` in `application.exception`; `domain`/`application` layers contain no `org.springframework`/`jakarta.persistence`/`infrastructure.*` import (architecture.md L166-169).
- **AC-5 (tests prove neutrality)**: `OAuthLoginUseCaseTest` rewritten with the **same** assertions (URL content, exception types, ordering, fallback) against the new port mocks; `OAuthLoginTransactionalStepTest` + infra client tests = import-only updates; test method counts unchanged. `:projects:global-account-platform:apps:auth-service:test` BUILD SUCCESSFUL, 0 regression vs the post-BE-300 baseline (459 tests).
- **AC-6 (CI authoritative)**: PR CI `Build & Test (JDK 21)` + `Integration (global-account-platform, Testcontainers)` green (the OAuth callback IT exercises the real provider-client + config path end-to-end — the authoritative behavior-neutral proof; local Docker may be unavailable).

# Related Specs

- [specs/services/auth-service/architecture.md](../../specs/services/auth-service/architecture.md) § Allowed/Forbidden Dependencies (L149-173) + § Internal Structure (L57-96) — enforced rule; **not edited** (pre-declared).
- `project_refactor_sweep_status` (memory) — `infrastructure.oauth.*` recorded as the explicit out-of-scope sibling of Signal 4 (BE-295/BE-300 비차단 관찰 (b)); this task closes it → GAP auth-service refactor backlog truly 0.

# Related Contracts

- None. OAuth HTTP contracts (`contracts/http/auth-api.md` OAuth authorize/callback) and emitted auth events are **unchanged** — request/response/URL/event shapes byte-identical; the refactor is invisible across every service boundary.

# Edge Cases

- **Wildcard import**: `OAuthLoginUseCase` uses `import com.example.auth.infrastructure.oauth.*` — must be replaced with explicit `application.port`/`domain.oauth`/`application.exception` imports (a grep for the wildcard would have missed it; AC-1 greps `infrastructure` prefix to catch it).
- **`resolveAllowedRedirectUris()` semantics**: the adapter must reproduce the 3-branch logic exactly (non-empty allowlist → `List.copyOf`; else `redirectUri != null` → `List.of(redirectUri)`; else `List.of()`), including the legacy single-redirect fallback the `authorize_legacyConfigWithoutAllowlist…` test pins.
- **`OAuthProviderConfig` immutability**: a record carrying `List<String> allowedRedirectUris` — adapter passes an unmodifiable copy (mirrors `List.copyOf`); use case only does `.contains()` (read), so no defensive-copy behavior change.
- **`OAuthProviderException` is a `RuntimeException`** caught by `OAuthLoginUseCase.callback` (`catch (OAuthProviderException e)`) and possibly mapped by `presentation/exception/AuthExceptionHandler` — the move must update both; exception identity/message/cause unchanged so `assertThatThrownBy(...).isSameAs(providerFailure)` still holds.
- **Concrete clients still read `OAuthProperties` directly** (tokenUri/userInfoUri/clientSecret/jwksUri) — that is legitimate infra→infra and stays; only the *application* stops importing `OAuthProperties` (via the new config port).

# Failure Scenarios

- **A config field is dropped/misordered in the adapter** → wrong authorization URL or a redirect-validation regression (open-redirect risk). Mitigation: AC-2(b/c) + `OAuthLoginUseCaseTest` URL-content + allowlist + trailing-slash + legacy-fallback assertions (unchanged) + CI OAuth callback IT.
- **`OAuthUserInfo`/`OAuthProviderException` move misses a referencer (esp. a concrete client or AuthExceptionHandler)** → compile failure or unmapped exception. Mitigation: AC-1 grep + full-module `:test` compile (AC-5); exhaustive referencer list enumerated in WI-1.
- **Mock-shape masks a behavior change** (BE-288 hazard) → green unit tests, broken flow. Mitigation: AC-5 keeps the *same value assertions* (URL string content, exception types) not just "port called"; CI Testcontainers OAuth callback IT is the real-flow authoritative gate (AC-6).
- **Scope creep into `infrastructure.oauth2.*` (SAS)** → unrelated risk. Mitigation: explicit Out of Scope; AC-1 grep is `infrastructure` under `application/` only; oauth2 is not application-imported.
- **`domain`/`application` re-acquires a framework import via the moved types** → reversed layering violation. Mitigation: AC-3/AC-4 grep gate on the new files (`OAuthProviderConfig`, relocated `OAuthUserInfo`).

# Verification

1. `grep -rn "import com.example.auth.infrastructure" .../application/` → 0 (AC-1); wildcard `infrastructure.oauth.*` gone from `OAuthLoginUseCase`.
2. `grep -n "org.springframework\|jakarta\|infrastructure" .../application/port/OAuthProviderConfig.java .../domain/oauth/OAuthUserInfo.java` → 0 (AC-3/4).
3. Diffs: `OAuthUserInfo`/`OAuthProviderException`/`OAuthClient` = package+import only; `OAuthClientFactory` = `implements`+`@Override` only; `OAuthPropertiesConfigAdapter` reproduces `resolveAllowedRedirectUris()` verbatim (AC-3).
4. `./gradlew :projects:global-account-platform:apps:auth-service:test` BUILD SUCCESSFUL; `OAuthLoginUseCaseTest` same assertions vs new mocks; infra client tests import-only; 0 regression vs 459-test post-BE-300 baseline (AC-5).
5. PR CI `Build & Test (JDK 21)` + `Integration (global-account-platform, Testcontainers)` green — real OAuth callback (provider client + config) end-to-end behavior-neutral proof (AC-2/AC-6).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (multi-port extraction across a `@Transactional` OAuth flow + framework-`@ConfigurationProperties` boundary abstraction + byte-identical URL/allowlist preservation + multi-file relocation — judgement-bearing, larger than BE-300, BE-295/300 class discipline) / 리뷰=Opus 4.7 (inline self-review, review-checklist 6/6, behavior-neutral + relocation-mechanical-only direct verification).
