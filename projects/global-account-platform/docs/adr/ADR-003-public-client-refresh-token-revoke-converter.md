# ADR-003: SAS Public-Client AuthenticationConverter for `refresh_token` and `revoke` Grants

**Status**: PROPOSED
**Date**: 2026-05-09
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: TASK-MONO-046-1 (PR #235), TASK-MONO-046-7 (PR #264, 11-cycle burn), TASK-MONO-046-7a (PR #289, 0/7 recovery), ADR-001 (OIDC Adoption)

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

## References

- TASK-MONO-046-7 (PR #264) — 11-cycle burn 결과 + 4 anti-pattern 학습
- TASK-MONO-046-7a (PR #289) — 0/7 recovery, doc-only PR
- ADR-001 (OIDC Adoption) — SAS 도입 결정
- Spring Authorization Server 1.4 docs — `OAuth2ClientAuthenticationFilter` lifecycle
- RFC 8252 (OAuth 2.0 for Native Apps) + RFC 9700 (Best Current Practice for OAuth 2.0 Security)
- `SasRefreshTokenAuthenticationProvider` (`projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/`) — 도메인 로직 보존 영역
- `SasRefreshTokenAuthenticationProviderTest` — 단위 coverage
