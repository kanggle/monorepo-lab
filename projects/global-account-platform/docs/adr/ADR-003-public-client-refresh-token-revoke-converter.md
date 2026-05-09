# ADR-003: SAS Public-Client AuthenticationConverter for `refresh_token` and `revoke` Grants

**Status**: ACCEPTED — 옵션 B closure (Cluster A 3/3 + 모든 enabled IT PASS, 회귀 0)
**Date**: 2026-05-09 (proposed) / 2026-05-09 (옵션 A partial) / 2026-05-09 (옵션 B closure)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: TASK-MONO-046-1 (PR #235), TASK-MONO-046-7 (PR #264, 11-cycle burn), TASK-MONO-046-7a (PR #289, 0/7 recovery), ADR-001 (OIDC Adoption), TASK-BE-272 (PR #292, 옵션 A 부분 성공), TASK-BE-274 (PR #296, 옵션 B closure — Cluster A 3/3 + token customizer 결함 unmasked + fix)

---

## Context

`auth-service`는 `spring-boot-starter-oauth2-authorization-server` (SAS)를 OIDC Authorization Server로 사용한다. `demo-spa-client`는 본 플랫폼의 첫 public client (B2C SPA)로, 다음과 같이 등록되어 있다.

```
client_id                       = demo-spa-client
client_authentication_methods   = ["none"]
authorization_grant_types       = ["authorization_code", "refresh_token"]
client_settings                 = require-proof-key=true (PKCE 필수)
```

**증상**: public client가 `POST /oauth2/token` 으로 `grant_type=refresh_token + client_id=demo-spa-client` (no client_secret) 를 보내면 SAS는 `INVALID_CLIENT` 401을 반환한다. 같은 client의 `POST /oauth2/revoke` (no secret) 도 동일.

**근본 원인**: SAS의 stock `PublicClientAuthenticationConverter` 는 다음 조건을 모두 충족해야만 fire한다.

1. `grant_type=authorization_code`
2. `code_verifier` 파라미터 존재 (PKCE)
3. `client_id` 파라미터 존재 + 등록된 client가 `ClientAuthenticationMethod.NONE` 사용

즉, `authorization_code + code_verifier` 조합 외에는 public-client 자기 인증 경로가 없다. `refresh_token` grant는 `code_verifier`를 보내지 않으므로 매칭 실패 → 인증 안 된 client → `INVALID_CLIENT`.

**왜 SAS 가 그렇게 설계됐는가**: OAuth 2.0 Security BCP (RFC 8252 / RFC 9700) 는 public client의 refresh-token 인증 메커니즘을 명시하지 않는다. 가장 흔한 권장 패턴은 (a) client가 PKCE 로 처음 발급받은 refresh-token 자체가 client identity proof 라고 보는 것, 또는 (b) DPoP (RFC 9449) 등 별도 proof-of-possession 메커니즘. SAS 는 둘 다 stock 으로 제공하지 않고, 운영자가 custom converter 를 짜야 한다.

### 11-cycle 시도 이력 (PR #264)

| Cycle | 시도 | 결과 |
|---|---|---|
| 1 (`41ffebae`) | `PublicClientNonPkceAuthenticationConverter` + `Provider` 추가 | revoke 401→200, refresh NPE |
| 2 | 4-arg constructor preserve `additionalParameters` | compile error → revert |
| 5 (`b1fd59a6`) | OAuth2TokenGenerator 직접 DI | NPE root caused; new failure: JTI dup |
| 6 (`b86302d1`) | persistRotation 순서 swap | best state (60-2 fail = Cluster A only); outer tx 깸 |
| 7 (`05ab3203`) | UPSERT in persistRotation | 6 fails (cluster C bleed) → revert |
| 8 (`9958c2c5`) | `@Transactional` on `publishTokenRefreshed` | 6 fails → revert |
| 9-11 | 모든 변경 revert + 8 method `@Disabled` | CI green — 1/8 recovered (Cluster B userinfo only) |

PR #264 는 11 cycle 동안 4 anti-pattern 학습:

- **A1**. SAS Customizer 람다는 `.with()` 시점에 동기 평가 → `http.getSharedObject(OAuth2TokenGenerator.class)` 가 람다 안에서 `null` (SAS init 전).
- **A2**. `DomainSyncOAuth2AuthorizationService.save()` 와 `SasRefreshTokenAuthenticationProvider.persistRotation()` 가 같은 JTI로 dual-INSERT → `idx_rt_jti` UNIQUE violation.
- **A3**. `new SasRefreshTokenAuthenticationProvider(...)` 수동 instantiation 은 Spring AOP `@Transactional` 미적용.
- **A4**. Test order pollution — `@DirtiesContext(AFTER_CLASS)` 적용 중에도 무언가 leak (cycle 9-11 503 SERVICE_UNAVAILABLE 반복).

TASK-MONO-046-7a (PR #289, 1 + 2 추가 cycle) 는 Cluster C (OAuth callback) 에 집중하며 **Cluster A (refresh_token + revoke 3 method) 는 architectural rework 없이는 fix 불가** 임을 재확인.

### 단위 coverage 현황

`SasRefreshTokenAuthenticationProviderTest` 는 port-fake 기반으로 다음을 커버한다.

- 정상 rotation flow
- reuse-detection
- revoke flow
- tenant mismatch rejection
- 만료/revoked check

이 단위 테스트는 IT @Disabled 상태와 무관하게 PASS. **도메인 로직 자체는 수렴됐고, 결함은 SAS 인증 경로 (converter ↔ provider 연결) 에만 한정**된다.

---

## Decision (Proposed)

### 옵션 비교

| 옵션 | 설명 | 장점 | 단점 |
|---|---|---|---|
| **A. Custom converter — full implementation** | `PublicClientRefreshTokenAuthenticationConverter` + `PublicClientRevokeAuthenticationConverter` 작성. `grant_type ∈ {refresh_token, revoke}` + `client_id` + no client_secret 조건에서 fire, 등록된 client의 `ClientAuthenticationMethod.NONE` 검증 → authenticated `OAuth2ClientAuthenticationToken` emit. SAS의 token endpoint authentication manager 에 첫 번째 provider로 등록. | OAuth 2.0 spec-compliant. authorization_code path 영향 0 (별 converter). 도메인 코드 (`SasRefreshTokenAuthenticationProvider`) 변경 0. | 11 cycle 의 negative lessons 무시 위험. authorization_code path 와 충돌하지 않도록 careful design. |
| **B. Provider-side fallback** | `SasRefreshTokenAuthenticationProvider.authenticate()` 에서 `client_id` 파라미터로 client lookup → `ClientAuthenticationMethod.NONE` 인 경우 inline-authenticate. converter 추가 없음. | 변경 범위 최소. SAS 의 client-auth filter 우회. | SAS 라이프사이클 위반 (인증 책임이 provider 안으로 들어감). 다른 grant type 으로 같은 패턴 재발 시 N×N 복잡도. |
| **C. IT 에서 client_secret 강제** | `demo-spa-client` 를 confidential client로 변경하여 모든 grant 에 client_secret 요구. | SAS stock path 그대로 활용. | 계약 위반 (SPA 는 client_secret 보관 불가능, B2C 표준 패턴 깨짐). |
| **D. IT 영구 demote** | 3 IT method 를 unit test 로 대체 (ALREADY 부분 충족 — `SasRefreshTokenAuthenticationProviderTest` 가 도메인 로직 cover). IT 는 architectural smoke test 만 보존. | cycle burn 0. ADR 발행 자체가 불필요. | E2E 회귀 가드 약화. CI 에서 SAS 통합 행위 검증 누락. |

### 권장 — 옵션 A (Custom converter, full implementation)

**근거**:

1. OAuth 2.0 spec 의 표준 패턴 (Spring 외 다른 OIDC AS — Keycloak, Authelia, Hydra — 모두 이 경로를 자체 구현).
2. authorization_code path 와 격리된 새 converter 라 회귀 위험 낮음. 11 cycle 의 negative lessons 는 모두 **provider** + **DomainSync** 영역이었지 converter 영역이 아니었음.
3. 도메인 코드 (`SasRefreshTokenAuthenticationProvider` + `DomainSyncOAuth2AuthorizationService`) 는 unit-test 검증된 상태로 보존. 본 ADR 의 변경은 **인증 진입 경로만 추가** — 전제는 깨지지 않음.
4. 옵션 D (영구 demote) 는 portfolio 가치 하락. SAS public-client refresh_token 통합 IT 가 main CI 에서 deterministic PASS 하는 것이 OIDC AS 운영 능력의 핵심 증명.

### 핵심 설계 (옵션 A)

```java
// 새 파일: PublicClientRefreshTokenAuthenticationConverter.java
@Component
public class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {
    private final RegisteredClientRepository clientRepository;

    @Override
    public Authentication convert(HttpServletRequest request) {
        // 1. grant_type filter
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            return null;
        }
        // 2. client_id 필수, client_secret 부재 (Authorization 헤더 없음)
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (clientId == null || request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return null;
        }
        // 3. 등록된 client + method=NONE 검증
        RegisteredClient client = clientRepository.findByClientId(clientId);
        if (client == null
                || !client.getClientAuthenticationMethods()
                          .contains(ClientAuthenticationMethod.NONE)) {
            return null;
        }
        // 4. authenticated OAuth2ClientAuthenticationToken 반환
        return new OAuth2ClientAuthenticationToken(
                client,
                ClientAuthenticationMethod.NONE,
                /* credentials */ null);
    }
}

// AuthorizationServerConfig 에서 등록
http.with(authorizationServerConfigurer, configurer ->
    configurer.tokenEndpoint(tokenEndpoint ->
        tokenEndpoint
            .accessTokenRequestConverter(publicClientRefreshTokenConverter)
            .authenticationProvider(buildRefreshTokenProvider(...))));
```

`POST /oauth2/revoke` 에도 동일 패턴의 `PublicClientRevokeAuthenticationConverter` 를 발행하고 `tokenRevocationEndpoint(...)` 에 wire.

### 회귀 매트릭스 (옵션 A 도입 시 검증 필수)

| 흐름 | 기대 결과 | IT class |
|---|---|---|
| `authorization_code` + PKCE (demo-spa-client) | 200 + access_token | `OAuth2AuthCodePkceIntegrationTest` |
| `authorization_code` + 잘못된 code_verifier | 400 invalid_grant | 동상 |
| `refresh_token` (demo-spa-client) | 200 + 새 RT (rotation) | **`OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation`** |
| `refresh_token` reuse (demo-spa-client) | 400 invalid_grant + chain revoked | **`OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_reuseDetected_returns400`** |
| `refresh_token` (test-internal-client, secret) | 200 (stock 경로 — converter null 반환) | `OAuth2AuthorizationServerIntegrationTest` |
| `client_credentials` (test-internal-client) | 200 (converter 무관) | 동상 |
| `revoke` (demo-spa-client) | 200 → introspect inactive | **`OAuth2RevokeIntrospectIntegrationTest.authCode_revokeRefreshToken_introspectInactive`** |
| `revoke` (test-internal-client, secret) | 200 (stock 경로) | 동상 |

**bold** = 현재 @Disabled 인 3 IT method. 본 ADR 채택 + 구현 task 완료 시 모두 enable + PASS 기대.

---

## Implementation Strategy

본 ADR 채택 후 발행되는 **TASK-BE-272** 가 다음 단계로 구현한다.

### Phase 1 — Converter 구현 + unit test
- 두 converter 클래스 신설.
- 직접 unit test (`HttpServletRequest` mock + assertion on returned token).
- **회귀 가드**: `OAuth2AuthCodePkceIntegrationTest` 의 7 test 모두 PASS 유지.
- Cycle 상한 = 2.

### Phase 2 — IT enable + 회귀 검증
- 3 disabled IT method `@Disabled` 제거.
- CI run → 회귀 매트릭스 8 케이스 모두 PASS 확인.
- **A2 anti-pattern (DomainSync vs persistRotation race) 재발 우려**: 11 cycle 의 cycle 6 best state (60-2 fail) 가 converter 도입 후 어떻게 변하는지 면밀 관찰. 만약 JTI duplicate INSERT 재발 시 즉시 cycle 6 의 swap 패턴 (`b86302d1`) 검토 필요.
- Cycle 상한 = 2.

### Phase 3 — Cluster C 영향 검증
- `OAuthLoginIntegrationTest` 의 5 disabled method 는 본 ADR 의 직접 영향 영역이 아니지만 (Cluster C RC 는 Linux HTTP client 영역), converter 추가가 그쪽으로 어떤 부수 효과를 만드는지 1 cycle observation.
- 영향 0 → Cluster C 는 ADR-004 분리 진행.
- Cycle 상한 = 1.

**총 cycle 예산** = 5 (Phase 1: 2 + Phase 2: 2 + Phase 3: 1). PR #264 의 11 cycle 보다 절반 이하.

---

## Consequences

### Positive

- 8 deferred IT 중 3 method (Cluster A) 회복.
- portfolio 의 OIDC AS 깊이 증명 — public-client refresh_token + revoke 가 main CI 에서 deterministic PASS.
- 4 anti-pattern (A1-A4) 학습이 ADR 형태로 영속화.

### Negative

- AuthorizationServerConfig 변경 + 2 새 converter 추가 = production code surface +~120 line.
- SAS 1.x major upgrade 시 converter API 변경 가능성. `OAuth2TokenGenerator` 와 같은 SAS internal hook 에 의존.
- 11 cycle 의 negative lessons 모두를 옵션 A 가 회피한다는 보장은 가설 — 첫 cycle 까지 결정적 검증 필요.

### Neutral

- 단위 test (`SasRefreshTokenAuthenticationProviderTest`) 와 IT 의 coverage 가 부분 중첩. 양쪽 모두 보존 (IT = E2E 회귀 가드, unit = 도메인 로직 빠른 피드백).

---

## Alternative Path — If Option A 도 6-cycle 안에 안 됨

본 ADR 의 옵션 A 시도 후에도 6-cycle 안에 PASS 되지 않을 경우 다음 fallback 단계 적용:

1. **2nd attempt**: 옵션 B (provider-side fallback) 로 전환. 변경 영역이 더 작아 회귀 위험 낮음.
2. **3rd attempt**: 옵션 D (IT permanent demote). 3 IT method 를 `@Disabled("permanent — see ADR-003 architectural blocker")` 로 영구 표기 + ADR-003 status `REJECTED` 갱신. 단위 coverage 가 도메인 로직을 cover 한다는 사실을 portfolio README 에 명시.

---

## Outcome (TASK-BE-272 / PR #292, 2026-05-09)

옵션 A 를 5 cycle 안에 구현, **부분 성공** — Cluster A 3 IT 중 1 회복 (revoke), 2 미회복 (refresh_token normal rotation + reuse detection).

### 회귀 매트릭스 8 케이스 채점 (ADR-003 § "회귀 매트릭스" 결과)

| 흐름 | 기대 | 실제 |
|---|---|---|
| `authorization_code` + PKCE | 200 | **PASS** |
| `authorization_code` + 잘못된 code_verifier | 400 | **PASS** |
| `refresh_token` (demo-spa-client) normal | 200 + 새 RT | **FAIL → A2 재발, 재@Disabled** |
| `refresh_token` reuse (demo-spa-client) | 400 + chain revoked | **FAIL → A2 재발, 재@Disabled** |
| `refresh_token` (test-internal-client, secret) | 200 (stock 경로) | **PASS** |
| `client_credentials` (test-internal-client) | 200 | **PASS** |
| `revoke` (demo-spa-client) | 200 → introspect inactive | **PASS (회복)** |
| `revoke` (test-internal-client, secret) | 200 (stock 경로) | **PASS** |

순회복 = revoke 1/3. 회귀 0.

### Cycle 소비 추적 (PR #292 commit 별)

| Cycle | 변경 | 결과 |
|---|---|---|
| 1 (`b12cbd80`) | converter 2개 + tokenEndpoint slot wiring | unit PASS, **CI 401 INVALID_CLIENT** (slot 잘못) |
| 2 (`51f8f988`) | clientAuthentication slot 으로 이동 + revoke 가드 | unit PASS, **CI 400 invalid_grant** (PKCE 강제) |
| 3 (`606652d7`) | `PublicClientNoPkceAuthenticationProvider` (pass-through) 추가 | **revoke 1/3 회복**, RT 2 NPE |
| 4 (`de8ebe3a`) | A1 회피 — `OAuth2TokenGenerator` 직접 inject | RT 2 가 NPE → A2 (`idx_rt_jti` UNIQUE violation) |
| 5 (`e84e4d5b`) | RT 2 IT 재@Disabled + 사유 명시 | **CI GREEN** (Integration GAP success) |

### 4 anti-pattern 회피 평가

- **A1** (SAS Customizer 람다 안 shared-object lookup): cycle 4 fix 로 영구 회피 — `OAuth2TokenGenerator` 직접 inject.
- **A2** (DomainSync vs persistRotation race, `idx_rt_jti` dual-INSERT): **재발**. 본 ADR 의 옵션 A 가 cover 하지 못하는 영역 — converter 추가만으로는 도메인 INSERT 경로의 race 해소 불가. RT 2 IT 회복은 옵션 B (provider-side fallback) 필요.
- **A3** (manual instantiation `@Transactional` 미적용): 신규 provider 들이 모두 `@Transactional` 미사용 → AOP 영향 0.
- **A4** (test order pollution): 신규 클래스 stateless, `@DirtiesContext(AFTER_CLASS)` 패턴 유지.

### 결론

옵션 A 의 **유효 영역 = revoke + 인증 진입 경로**. RT rotation/reuse 의 도메인 INSERT race 는 ADR-003 의 "도메인 코드 변경 0" 전제로는 미해결. ADR-003 status `ACCEPTED — partial` 로 closed. **다음 단계는 follow-up TASK-BE-274 (옵션 B provider-side fallback)**.

옵션 D (영구 demote) 는 채택하지 않음 — RT 2 IT 의 도메인 가치 (rotation + reuse detection 은 SAS public-client 의 핵심 보안 행동) 가 unit-test 만으로 충분히 cover 되지 않는다는 판단. 옵션 B 시도 후 재평가.

### A2 진단 결과 (TASK-BE-274)

TASK-BE-274 Phase 1 진단으로 **dual-INSERT 두 위치** 가 정확히 식별됐다.

#### 호출 stack

`POST /oauth2/token (grant_type=refresh_token, client_id=demo-spa-client)` 도착 후:

1. SAS `OAuth2ClientAuthenticationFilter` → `PublicClientRefreshTokenAuthenticationConverter` (TASK-BE-272) → `PublicClientNoPkceAuthenticationProvider` (pass-through) 가 client 인증 통과.
2. SAS `OAuth2TokenEndpointFilter` → `OAuth2RefreshTokenAuthenticationConverter` 가 `OAuth2RefreshTokenAuthenticationToken` emit.
3. **`SasRefreshTokenAuthenticationProvider.authenticate(Authentication)`** 진입 (이 메서드 안에서 dual-INSERT 발생):

   - **위치 (a)** — `SasRefreshTokenAuthenticationProvider.java:233`
     ```java
     authorizationService.save(updatedAuthorization);
     ```
     이는 `DomainSyncOAuth2AuthorizationService.save(authorization)` 로 위임 →
     - `delegate.save(authorization)` (`JpaOAuth2AuthorizationService` JDBC INSERT 로 SAS oauth2_authorization row + refresh_token 컬럼 저장; `refresh_tokens` 도메인 테이블과 무관)
     - `syncRefreshTokenToDomainStore(authorization)` (`DomainSyncOAuth2AuthorizationService.java:88-132`) 가 `refreshTokenRepository.findByJti(NEW_RT)` 로 idempotent check 하고 not-found 면 `refreshTokenRepository.save(domainToken)` → `RefreshTokenJpaEntity` 로 변환 후 JPA persist → **첫 번째 INSERT** 큐 enqueue (jti = NEW_RT_VALUE).

   - **위치 (b)** — `SasRefreshTokenAuthenticationProvider.java:237`
     ```java
     persistRotation(submittedTokenValue, newRefreshToken, authorization, registeredClient, now);
     ```
     `persistRotation()` 내부 (line 301-331) 가 `RefreshToken.create(NEW_RT_VALUE, ..., rotated_from=OLD_RT_VALUE)` 로 새 도메인 entity 생성 후 `refreshTokenRepository.save(newDomainToken)` (line 324) → **두 번째 INSERT** 큐 enqueue (jti = NEW_RT_VALUE).

#### Race 메커니즘

같은 transaction (provider 의 호출 stack 전체가 SAS `OAuth2TokenEndpointFilter` 의 outer transaction 안에 있음) 안에서 **같은 NEW_RT_VALUE 로 두 개의 `RefreshTokenJpaEntity` instance 가 persist** 된다:

- 위치 (a) 의 entity 는 `rotated_from = null` (DomainSync 의 syncRefreshTokenToDomainStore line 117)
- 위치 (b) 의 entity 는 `rotated_from = OLD_RT_VALUE` (persistRotation line 320)

JPA `save()` 는 transaction commit 시 flush 한다. 두 entity 모두 같은 jti = NEW_RT_VALUE 로 INSERT SQL 발사 → `idx_rt_jti UNIQUE` 제약 위반 → `DataIntegrityViolationException` → outer transaction rollback → `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation` 실패.

**왜 idempotent check 가 효과 없는가**: `DomainSyncOAuth2AuthorizationService.syncRefreshTokenToDomainStore()` 의 idempotent check (line 99 `if (refreshTokenRepository.findByJti(tokenValue).isPresent()) return`) 는 위치 (a) 가 먼저 실행될 때 NEW_RT 가 아직 도메인 store 에 없으므로 통과 → INSERT enqueue. 위치 (b) 의 `persistRotation()` 은 idempotent check 없이 무조건 save → 두 번째 INSERT enqueue. 두 INSERT 모두 같은 transaction 의 flush 큐에 쌓여 commit 시점에 같은 jti 로 발사 → 충돌.

**Initial issuance (AuthCode → tokens) 에서 race 가 발생하지 않는 이유**: AuthCode 흐름은 SAS 의 `OAuth2AuthorizationCodeAuthenticationProvider` 가 마지막에 `authorizationService.save(...)` 한 번만 호출하고 그 호출 한 번이 `DomainSync.syncRefreshTokenToDomainStore()` 를 통해 도메인 INSERT 한 번 발사. provider 의 `persistRotation()` 은 rotation path 에만 존재. 따라서 dual-INSERT 는 rotation path 한정.

#### 옵션 B 채택 (TASK-BE-274)

세 안 (skip-path TransactionSync flag / UPSERT race-safe / save 통합) 중 **(1) skip-path TransactionSync flag** 채택. 사유:

- 변경 범위 최소 (DomainSync + Provider 두 파일 한정).
- AuthCode initial issuance path 는 flag 가 set 되지 않으므로 기존 single-INSERT 경로 그대로 — 회귀 0 보장.
- PR #264 cycle 7 (`05ab3203`) UPSERT 시도가 cluster C bleed 일으킨 사례 회피.
- Provider 가 rotation path 의 도메인 INSERT 책임 single source — A2 anti-pattern 의 architectural 해소 (단순 retry/swallow 가 아닌).
- TSM `unbindResourceIfPossible` + `TransactionSynchronization.afterCompletion` 으로 stale flag 영구 cleanup.

구현 핵심:

```java
// SasRefreshTokenAuthenticationProvider.authenticate() — line 233 직전
TransactionSynchronizationManager.bindResource(
    SAS_ROTATION_SKIP_KEY, Boolean.TRUE);
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(SAS_ROTATION_SKIP_KEY);
        }
    });
try {
    authorizationService.save(updatedAuthorization);  // SAS save 만, 도메인 sync skip
    persistRotation(...);  // 도메인 INSERT single source
} finally {
    // afterCompletion 이 cleanup (try-finally 는 fail-safe)
}

// DomainSyncOAuth2AuthorizationService.syncRefreshTokenToDomainStore() entry
if (Boolean.TRUE.equals(TransactionSynchronizationManager.getResource(SAS_ROTATION_SKIP_KEY))) {
    return;  // rotation in progress — provider 가 책임
}
```

#### 옵션 B 결과 (TASK-BE-274 / PR #296, 2026-05-09 closure)

**Cluster A 회복 = 3/3 (완전 달성)** — 회귀 매트릭스 8/8 PASS + token customizer 결함 unmasked + fix → 회귀 0.

##### Phase 별 cycle 추적

| Phase | Commit | 변경 | 결과 |
|---|---|---|---|
| Phase 1 | `c7c5ecc8` | A2 race 진단 (provider line 233 + 237 dual-INSERT 식별) | 본 ADR sub-section 추가 |
| Phase 2 cycle 1 | `172216b8` | 옵션 (1) skip-path TSM flag — DomainSync + Provider 양쪽 wire | dual-INSERT race 해소 (local), 새 RC `TransactionRequiredException at OutboxWriter.save()` 노출 |
| Phase 2 cycle 2 | `a83a4d12` | TransactionTemplate programmatic 도입 (A3 회피) — rotation write+publish 한 tx wrap, reuse path 동일 | unit PASS, IT verify 는 Rancher Docker 회귀로 차단 → CI 검증 위임 |
| Phase 2 cycle 3 | `7e7719c9` | TenantClaimTokenCustomizer 에 REFRESH_TOKEN grantType 분기 추가 (기존 결함 unmasked, fix) | 단위 24 cases PASS, CI verify 위임 |

CI verify cycle 1+2 결과 (PR #296 / run `25596254251`, 2026-05-09 08:19 UTC): GAP Integration 2m30s, **회귀 매트릭스 8/8 PASS**:

| 흐름 | 기대 | 실제 |
|---|---|---|
| authorization_code + PKCE | 200 | **PASS** |
| authorization_code + 잘못된 code_verifier | 400 | **PASS** |
| **refresh_token (demo-spa-client) normal rotation** | 200 + 새 RT | **PASS (회복!)** |
| **refresh_token reuse (demo-spa-client)** | 400 + chain revoked | **PASS (회복!)** |
| refresh_token (test-internal-client, secret) | 200 | **PASS** |
| client_credentials | 200 | **PASS** |
| revoke (demo-spa-client) | 200 | **PASS** (BE-272 회복분 유지) |
| revoke (test-internal-client, secret) | 200 | **PASS** |

##### Cycle 3 — token customizer 결함 unmasked + fix

cycle 1+2 CI verify 에서 `OAuth2RefreshTokenIntegrationTest.refreshedAccessToken_hasTenantClaims:271` **FAILED** (`[refreshed access_token must contain tenant_id]`). 처음에는 BE-274 변경의 부수 회귀로 의심.

**cycle 3 진단**: 가설 1/2/3 (TransactionTemplate timing / skip-path principal / TokenGenerator instance) **모두 falsified**. 실제 RC = `TenantClaimTokenCustomizer.customize()` 에 `REFRESH_TOKEN` grantType 분기 누락:

```java
// 본 fix 전: TenantClaimTokenCustomizer.customize()
if (context.getAuthorizationGrantType().equals(AuthorizationGrantType.AUTHORIZATION_CODE)) {
    customizeForAuthorizationCode(context);
} else if (context.getAuthorizationGrantType().equals(AuthorizationGrantType.CLIENT_CREDENTIALS)) {
    customizeForClientCredentials(context);
}
// REFRESH_TOKEN grant 는 fall-through → no-op → claim 누락
```

**의미**: BE-274 변경과 무관한 **기존 결함**. BE-272 시점 RT 2 IT 가 `@Disabled` 라 customize 호출 자체가 없어 결함이 unmasked 안 됐음. RT 2 가 BE-274 cycle 1 에서 처음 enable 되면서 결함 노출. 즉 회귀 fix 가 아니라 **신규 결함 발견 + 수정** (BE-274 의 추가 가치).

**Fix** (commit `7e7719c9`): `customize()` 에 `REFRESH_TOKEN` 분기 추가, `customizeForAuthorizationCode()` 위임 (principal.getDetails() 우선 + ClientSettings Option B fallback). `TenantClaimTokenCustomizerTest` 에 `refresh_token` grant 단위 test 2 case 신규 (24 cases 총 PASS).

##### CI verify cycle 3 (PR #296 / run `25597001354` / 2026-05-09 08:58 UTC)

GAP Integration **2m 14s SUCCESS**. **OAuth2RefreshTokenIntegrationTest 7/7 PASS** (refreshedAccessToken_hasTenantClaims 회복 포함). 다른 IT (Cluster A revoke / AuthCode/PKCE 7/7 / OAuth2AuthorizationServer 6/6 / OAuth2RevokeIntrospect 7/7 / OAuthLogin 7/7 / DeviceSession / AuthIntegration) 회귀 0.

##### 4 anti-pattern 회피 평가

- **A1** (SAS Customizer 람다 timing): 본 task 영역 무관, 회피
- **A2** (DomainSync ↔ persistRotation dual-INSERT): **architecturally 해소** (skip-path 로 race 자체 제거)
- **A3** (`@Transactional` AOP 미적용): TransactionTemplate programmatic 으로 우회 — provider 의 publish 가 active tx 안에서 실행됨이 핵심
- **A4** (test order pollution): stateless 변경, 기존 `@DirtiesContext` 패턴 유지

##### 결론

옵션 B 의 **유효 영역 = Cluster A 3/3 회복 + 회귀 매트릭스 8/8 PASS + token customizer 결함 unmasked + fix**. **AC-01~07 모두 충족**, cycle 3/6 사용 (남은 3 미사용). 본 task spec 의 success criteria 완전 달성.

**부수 가치**: cycle 3 의 RC 진단으로 **BE-272 시점부터 잠재된 token customizer 결함** (REFRESH_TOKEN grantType 분기 누락) 이 unmasked + fix. 이는 RT 2 IT 가 처음 enable 되지 않았다면 발견되지 않았을 결함 — 본 task 의 추가 portfolio 가치.

ADR-003 status: `ACCEPTED — partial (Cluster A 1/3 + RT 2 deferred)` → `ACCEPTED — 옵션 B closure (Cluster A 3/3 + 모든 enabled IT PASS, 회귀 0)`.

---

## References

- TASK-MONO-046-7 (PR #264) — 11-cycle burn 결과 + 4 anti-pattern 학습
- TASK-MONO-046-7a (PR #289) — 0/7 recovery, doc-only PR
- TASK-BE-272 (PR #292) — 옵션 A 구현, 부분 성공 (revoke 1/3)
- TASK-BE-274 (PR #296, commits c7c5ecc8 + 172216b8 + a83a4d12 + 7e7719c9) — 옵션 B closure, Cluster A 3/3 회복 (RT 2 + revoke), 회귀 매트릭스 8/8 PASS, token customizer 결함 (REFRESH_TOKEN 분기 누락) unmasked + fix, 회귀 0
- ADR-001 (OIDC Adoption) — SAS 도입 결정
- Spring Authorization Server 1.4 docs — `OAuth2ClientAuthenticationFilter` lifecycle
- RFC 8252 (OAuth 2.0 for Native Apps) + RFC 9700 (Best Current Practice for OAuth 2.0 Security)
- `SasRefreshTokenAuthenticationProvider` (`projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/`) — 도메인 로직 보존 영역
- `SasRefreshTokenAuthenticationProviderTest` — 단위 coverage
