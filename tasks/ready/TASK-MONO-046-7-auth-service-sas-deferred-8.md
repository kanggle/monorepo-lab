# Task ID

TASK-MONO-046-7

# Title

GAP auth-service SAS deferred 8 IT — Cluster A (RT rotation/reuse/revoke 3) + Cluster B (userinfo tenant_id 1) + Cluster C (OAuth callback 4) 재활성화

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

[TASK-MONO-046-1](../in-progress/TASK-MONO-046-1-auth-sas-12.md) PR #235 (partial recovery) 에서 13 originally-disabled auth-service IT 중 5 메서드 복구 완료:

- login redirect HTML-only path fix
- RT jti column fix (domain store findByJti)
- account-service base-url lazy resolution fix
- OAuthLogin test class-level @BeforeAll setup

나머지 8 메서드는 더 깊은 SAS / Spring Security / Docker 환경 재현이 필요하여 `TASK-MONO-046-7` 으로 분리 이관. 본 task 에서 3 cluster 각각을 진단·수정·재활성화.

## 8 deferred 메서드

### Cluster A — public-client refresh_token / revoke 인증 (3 메서드)

| 클래스 | 메서드 | Disabled 이유 |
|--------|--------|---------------|
| `OAuth2RefreshTokenIntegrationTest` | `refreshTokenGrant_normalRotation` (Order=2) | 401 invalid_client — SAS stock PublicClientAuthenticationConverter 가 refresh_token grant 를 처리하지 않음 |
| `OAuth2RefreshTokenIntegrationTest` | `refreshTokenGrant_reuseDetected_returns400` (Order=4) | Order=2 실패 cascading |
| `OAuth2RevokeIntrospectIntegrationTest` | `authCode_revokeRefreshToken_introspectInactive` (Order=4) | /oauth2/revoke 에서 public client 인증 실패 (401) |

**가설**: SAS 1.4 `PublicClientAuthenticationConverter` 는 `authorization_code + code_verifier` POST 요청에만 매칭됨. `grant_type=refresh_token` 및 `/oauth2/revoke` 요청에서 `client_id` 만 전달하는 public PKCE client 인증이 누락 → 401 invalid_client.

046-1 iter 5 에서 `PublicClientRefreshTokenAuthentication{Converter,Provider}` 커스텀 구현을 시도했으나 `token_wrongCodeVerifier_returns400` 회귀 발생 → revert. 본 task 에서 회귀 없이 올바른 구현 경로 탐색.

### Cluster B — /oauth2/userinfo tenant_id claim 누락 (1 메서드)

| 클래스 | 메서드 | Disabled 이유 |
|--------|--------|---------------|
| `OAuth2AuthCodePkceIntegrationTest` | `userinfo_validToken_returnsOidcClaims` (Order=4) | /oauth2/userinfo 응답에 `tenant_id` claim 없음 |

**가설**: SAS UserInfo endpoint 는 JWT access token 의 custom claim 을 자동으로 UserInfo 응답에 포함하지 않음. `OidcUserInfoMapper` 가 `tenant_id` / `tenant_type` 을 명시적으로 맵핑해야 하나 누락 또는 `id_token` claims 만 복사하는 구현일 가능성.

### Cluster C — OAuth social login callback 4 메서드 (4 메서드)

| 클래스 | 메서드 | Disabled 이유 |
|--------|--------|---------------|
| `OAuthLoginIntegrationTest` | `googleHappyPath` | authorize→callback 전체 flow 실패 |
| `OAuthLoginIntegrationTest` | `kakaoHappyPath` | authorize→callback 전체 flow 실패 |
| `OAuthLoginIntegrationTest` | `microsoftHappyPath` | authorize→callback 전체 flow 실패 |
| `OAuthLoginIntegrationTest` | `microsoftPreferredUsernameFallback` | authorize→callback 전체 flow 실패 |

**가설**: WireMock + MockMvc 조합에서 OAuth2 redirect flow 가 올바르게 동작하지 않음. 가능한 원인:
1. SAS `/oauth2/authorize` redirect 가 MockMvc follow-redirect 를 지원하지 않는 경로
2. account-service social-signup WireMock stub 경로 불일치
3. `state` 파라미터 / session cookie 가 MockMvc 요청 간 공유되지 않음
4. WireMock JWKS stub 이 SAS token endpoint RS256 검증과 충돌

---

# Scope

## In Scope

### Cluster A 진단 및 수정

- SAS 1.4 `PublicClientAuthenticationConverter` 소스 분석 — `refresh_token` / revoke 엔드포인트 매칭 여부
- 회귀(`token_wrongCodeVerifier_returns400`) 없이 public client 인증 확장 방법 탐색:
  - SAS 공식 확장 포인트 활용 (e.g., `ClientAuthenticationProvider` extension, `OAuth2ClientAuthenticationFilter` customization)
  - 또는 `registered_client.token_settings` 에 `require-proof-key=true` + refresh_token grant 예외 처리
- 3 메서드 `@Disabled` 제거 + PASS

### Cluster B 진단 및 수정

- `OidcUserInfoMapper` 구현 분석 — `tenant_id` / `tenant_type` claim 복사 여부
- `OidcUserInfoService` / `UserInfo` customization 경로 확인
- 1 메서드 `@Disabled` 제거 + PASS

### Cluster C 진단 및 수정

- `OAuthLoginIntegrationTest` 의 `performAuthorize` / `performCallback` helper 분석
- MockMvc + SAS redirect 플로우 재현 가능성 검토
- WireMock stub 경로 정합성 확인 (Google/Kakao/Microsoft token endpoint, JWKS, account-service)
- 4 메서드 `@Disabled` 제거 + PASS

## Out of Scope

- TASK-MONO-046-6 (consumer-pipeline burst timing) — 별도 task
- TASK-MONO-046-5 (PiiMasking) — 별도 task
- 046-1 PR #235 에서 이미 recovered 된 5 메서드 재변경
- 신규 OAuth2 기능 추가 (SAS 설정 변경은 허용, 새로운 엔드포인트는 금지)

---

# Acceptance Criteria

## 통과

1. `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation` PASS
2. `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_reuseDetected_returns400` PASS
3. `OAuth2RevokeIntrospectIntegrationTest.authCode_revokeRefreshToken_introspectInactive` PASS
4. `OAuth2AuthCodePkceIntegrationTest.userinfo_validToken_returnsOidcClaims` PASS
5. `OAuthLoginIntegrationTest.googleHappyPath` PASS
6. `OAuthLoginIntegrationTest.kakaoHappyPath` PASS
7. `OAuthLoginIntegrationTest.microsoftHappyPath` PASS
8. `OAuthLoginIntegrationTest.microsoftPreferredUsernameFallback` PASS

## 회귀 없음

9. `OAuth2AuthCodePkceIntegrationTest.token_wrongCodeVerifier_returns400` — MUST remain PASS (046-1 iter 5 회귀 재발 금지)
10. 046-1 PR #235 에서 recovered 된 5 메서드 회귀 0
11. 나머지 auth-service IT 전체 회귀 0

## CI

12. main CI `Integration (GAP)` Job: auth-service 60 테스트 전체 PASS / 0 FAIL / 0 DISABLED (이 task 에서 모든 @Disabled 제거)
13. PR description 에 cluster 별 root cause 진단 결과 + fix approach 명시

---

# Related Specs

- [TASK-MONO-046-1](../in-progress/TASK-MONO-046-1-auth-sas-12.md) — **직접 선행** (이 task 의 `@Disabled` 가 046-1 partial-recovery PR #235 에서 추가됨 — 046-1 머지 후 착수)
- `projects/global-account-platform/specs/services/auth-service/`

---

# Related Contracts

- 없음 (SAS 내부 동작 + test-only 변경 — 외부 contract 변경 없음)

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/AuthorizationServerConfig.java`
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/OidcUserInfoMapper.java` (Cluster B)
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OAuth2RefreshTokenIntegrationTest.java`
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OAuth2RevokeIntrospectIntegrationTest.java`
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OAuth2AuthCodePkceIntegrationTest.java`
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OAuthLoginIntegrationTest.java`

---

# Edge Cases

1. **Cluster A — PKCE regression**: `token_wrongCodeVerifier_returns400` 는 `PublicClientAuthenticationConverter` 가 정상 동작할 때 통과함. 커스텀 converter 추가 시 이 테스트가 regression 되지 않도록 반드시 확인.
2. **Cluster A — revoke endpoint**: `/oauth2/revoke` 가 `clientAuthentication` 필터체인 범위에 포함되는지 SAS endpoint matcher 확인 필요.
3. **Cluster B — id_token vs UserInfo**: SAS UserInfo endpoint 는 access token scope 기반으로 claims 를 결정. `openid profile email` scope 에 `tenant_id` 가 포함되지 않으면 mapper 가 명시적으로 추가해야 함.
4. **Cluster C — MockMvc session**: `performAuthorize` 가 반환하는 `state` 값이 `performCallback` 에서 동일 세션으로 검증됨. MockMvc 가 session cookie 를 carry-over 하지 않으면 state mismatch 발생.
5. **Cluster C — WireMock dynamic port**: JWKS URL 이 WireMock 동적 포트로 설정되어야 함. SAS 가 startup 시 JWKS 를 캐시하면 port mismatch 가능.

---

# Failure Scenarios

## A. Cluster A: custom converter 구현 불가

SAS 1.4 internal API 변경으로 안전한 확장 포인트가 없을 경우 — `registered_client` 레벨에서 `requireProofKey = false` 로 설정하고 refresh_token grant 에서 client_secret_none 인증 방식을 사용하는 방향 검토.

## B. Cluster B: UserInfo mapper 구조 변경 필요

`OidcUserInfoMapper` 가 `id_token` claims 만 복사하는 구조일 경우 — access token JWT payload 에서 `tenant_id` / `tenant_type` 을 꺼내 UserInfo 응답에 주입하는 로직 추가 필요. 테스트 scope claim 확인 필수.

## C. Cluster C: MockMvc + redirect flow 불가

SAS redirect-based authorize flow 가 MockMvc 내에서 완전히 재현 불가한 구조일 경우 — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTemplate` / `WebTestClient` 기반으로 테스트 재작성 검토. `OAuthLoginIntegrationTest` 전체 리팩터링이 필요할 수 있음.

---

# Test Requirements

- 8 IT 메서드 `@Disabled` 제거 + 전체 PASS
- `token_wrongCodeVerifier_returns400` 포함 auth-service integrationTest 전체 통과
- main CI `Integration (GAP)` Job SUCCESS

---

# Definition of Done

- [ ] Cluster A root cause 진단 완료 + 회귀 없이 fix 적용
- [ ] Cluster B root cause 진단 완료 + fix 적용
- [ ] Cluster C root cause 진단 완료 + fix 적용
- [ ] 8 IT 메서드 `@Disabled` 제거
- [ ] local integrationTest PASS 확인
- [ ] main CI `Integration (GAP)` Job SUCCESS 확인
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — SAS 1.4 internal authentication filter chain, UserInfo claim mapping, MockMvc OAuth2 redirect 재현 등 복잡한 Spring Security 분석.
- **분량 추정**: medium-large (3 cluster 각각 별도 root cause + fix — 총 8 메서드).
- **dependency**:
  - `선행`: TASK-MONO-046-1 (본 task 의 `@Disabled` 가 046-1 partial-recovery PR #235 에서 추가됨 — 046-1 머지 후 착수).
  - `병렬`: TASK-MONO-046-6 (consumer-pipeline), TASK-MONO-046-5 (PiiMasking).
  - `후속`: 본 task + 046-6 + 046-5 모두 머지 시 main `Integration (GAP)` Job 전체 GREEN milestone.
