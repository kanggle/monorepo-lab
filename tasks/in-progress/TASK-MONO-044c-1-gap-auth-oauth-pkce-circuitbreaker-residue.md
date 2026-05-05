# Task ID

TASK-MONO-044c-1

# Title

GAP auth-service 잔존 17건 fix — OAuth2 authorize/PKCE 12 + OAuthLogin circuit-breaker 4 + downstream-fault 1

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

[TASK-MONO-044c](../done/TASK-MONO-044c-gap-integration-downstream-failures.md) 가 33건 중 16건을 fix 하여 머지되었으나 `Integration (global-account-platform, Testcontainers)` Job 에 17건이 잔존. 044c § Failure Scenario B (복수 cause + 일부 production 코드 회귀) 로 분기된 follow-up. main CI 의 `Integration (GAP)` Job FAILURE → SUCCESS 를 본 task 가 완료한다.

CI run `25355285563` (PR #208 의 가장 최신 push, c80bcc23 commit) 의 `Integration (GAP)` Job 잔존 분포:

```
gateway-service (1 fail):
  Gateway Rate Limit 통합 테스트 > 다운스트림 연결 리셋(Fault) → 게이트웨이가 5xx 반환

auth-service (16 fail):
  # OAuth2 authorize/PKCE/refresh/revoke (12)
  OAuth2AuthCodePkceIntegrationTest > authorize: authenticated user → 302 redirect to redirect_uri with code param
  OAuth2AuthCodePkceIntegrationTest > token: authorization_code + code_verifier → access_token + id_token with tenant_id
  OAuth2AuthCodePkceIntegrationTest > userinfo: valid access_token → 200 with sub + email + tenant_id
  OAuth2AuthCodePkceIntegrationTest > token: wrong code_verifier → 400 (PKCE S256 validation fails)
  OAuth2AuthCodePkceIntegrationTest > userinfo: no token → 401
  OAuth2RefreshTokenIntegrationTest > authCode flow: refresh_token issued → persisted in domain JPA store
  OAuth2RefreshTokenIntegrationTest > refresh_token grant: normal rotation → new tokens, old RT revoked in domain store
  OAuth2RefreshTokenIntegrationTest > refresh_token grant: new access_token still contains tenant_id + tenant_type claims
  OAuth2RefreshTokenIntegrationTest > reuse detection: reusing a rotated refresh_token → 400 invalid_grant
  OAuth2RefreshTokenIntegrationTest > refresh_token grant: unknown token → 400 invalid_grant
  OAuth2RefreshTokenIntegrationTest > cross-tenant: refresh_token with mismatched tenant rejected → 400 invalid_grant
  OAuth2RevokeIntrospectIntegrationTest > authCode flow: issue refresh_token → revoke → introspect → active=false

  # OAuthLogin social (4)
  OAuthLoginIntegrationTest > Microsoft: email absent → preferred_username fallback is used as email
  OAuthLoginIntegrationTest > Google: authorize + callback → tokens, social_identities row, outbox OAUTH_GOOGLE
  OAuthLoginIntegrationTest > Kakao: authorize + callback (access_token + userinfo) → outbox OAUTH_KAKAO
  OAuthLoginIntegrationTest > Microsoft: authorize + callback (id_token sub/email) → outbox OAUTH_MICROSOFT
```

가설:

- **RC#1 (OAuth2 authorize/PKCE/refresh/revoke 12건, 단일 root cause)**: 모두 `/oauth2/authorize → 400 [invalid_request] OAuth 2.0 Parameter: response_type` 에서 시작 (`demo-spa-client` 대상). TASK-BE-251 ADR-001 SAS 도입 (commit `512cbbd4`) 이후 노출. SAS 1.4.1 + `JpaRegisteredClientRepository` 가 `response_type=code` 를 인식 못 하는 config drift 의심. **production 코드 회귀 가능**.
- **RC#2 (OAuthLogin 4건)**: resilience4j circuit-breaker bean 이 `@SpringBootTest` context 안에서 클래스 간 공유 → 첫 stub 실패 시 breaker 가 OPEN 으로 trip 되고 후속 케이스로 503 cascade. **테스트 isolation refactor**.
- **RC#3 (downstream-fault 1건, sporadic)**: Redis 컨테이너 reset 이 WireMock fault stub 와 race. system-out 상 connection reset 의 origin 이 Redis 라는 단서. **TASK-MONO-044 § AC #8 nightly 회귀 방지 영역**으로 이관 검토.

본 task 가 fix 후 main CI 의 `Integration (GAP)` Job FAILURE → SUCCESS 로 회복.

---

# Scope

## In Scope

### RC#1 — OAuth2 authorize/PKCE/refresh/revoke 12건 (단일 root cause 가설)

- 가설 검증 단계:
  1. 로컬에서 `OAuth2AuthCodePkceIntegrationTest > authorize` 1건만 격리 실행 + `--debug-jvm` 으로 stack trace 수집.
  2. SAS `OAuth2AuthorizationEndpointFilter` → `OAuth2AuthorizationCodeRequestAuthenticationProvider` 경로에서 `response_type` 파라미터가 어디서 missing 으로 평가되는지 확인.
  3. `JpaRegisteredClientRepository` 가 `demo-spa-client` 를 정상 반환하는지 + `RegisteredClient.authorizationGrantTypes` 에 `AUTHORIZATION_CODE` 가 포함되는지.
  4. `JpaRegisteredClient → RegisteredClient` 변환 (`RegisteredClientEntityToDomainMapper` 또는 동등 mapper) 에서 `authorization_grant_types` JSON column 파싱 회귀 의심.
  5. `git log --since=2026-04-25 --oneline -- projects/global-account-platform/apps/auth-service/src/main/java | grep -i 'oauth\|sas\|registered'` 로 도입 commit 추적.
- root cause 별 fix:
  - **production 코드 회귀**: SAS config / RegisteredClient mapper / JPA persistence 회귀 fix. PR description 에 production 영향 명시.
  - **test fixture 회귀**: V0008/V0009/V0010 의 `demo-spa-client` seed 가 SAS 1.4.1 변경 contract 와 drift 한 경우 seed/fixture 갱신.

### RC#2 — OAuthLogin 4건 (테스트 isolation)

- resilience4j `CircuitBreakerRegistry` 를 per-test reset 하는 `@TestConfiguration` 도입:
  - 옵션 (i): `@BeforeEach` 에서 `circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> cb.reset())`.
  - 옵션 (ii): 각 OAuth provider (Google/Microsoft/Kakao) 별 별도 `CircuitBreaker` instance 분리 + per-test transition.
- WireMock stub 로 production 도메인 변경 없이 OAuth callback 시나리오 격리.

### RC#3 — downstream-fault 1건 (sporadic)

- 본 task 안에서 재현 시도 (10회 반복 실행) → 100% 재현되면 deterministic 으로 fix 시도.
- sporadic (≤ 30%) 면 `@DisabledIf` 로 일단 격리 + TASK-MONO-044 § AC #8 nightly 회귀 task 로 이관.

## Out of Scope

- 새 OAuth2 시나리오 추가 (현 60 → 새 70+)
- production 도메인 로직 변경 (OAuth2 정책/scope/grant type 정책 변경) — 단, RC#1 이 production 회귀로 판명되면 fix 가 함께 들어감
- TASK-MONO-044 의 다른 회귀 (044e/044f)
- nightly CI 회귀 자동화 (TASK-MONO-044 § AC #8 별도 후속)

---

# Acceptance Criteria

## 부팅 + 통과

1. `:projects:global-account-platform:apps:auth-service:integrationTest` PASS — 60/60 (현 44/60 → 60/60)
2. `:projects:global-account-platform:apps:gateway-service:integrationTest` PASS — 34/34 (현 33/34 → 34/34, RC#3 deterministic fix 시. sporadic 분리 시 33/34 유지 + `@DisabledIf` 명시)
3. main CI `Integration (global-account-platform, Testcontainers)` Job FAILURE → SUCCESS

## 진단 + 분류

4. PR description 에 RC#1/RC#2/RC#3 별 root cause 분류표 + fix 전략 기록
5. RC#1 production 회귀 여부 명시
6. RC#3 sporadic 분리 시 TASK-MONO-044 § AC #8 nightly task 후속 issue/task 발행 명시

## 회귀 0

7. 044c 머지 이후 노출된 다른 서비스 회귀 없음
8. 회귀 보고서 `knowledge/incidents/2026-05-05-ci-regression.md` § GAP 단락에 잔존 17건 fix 결과 1 단락 추가

---

# Related Specs

- [TASK-MONO-044c (선행)](../done/TASK-MONO-044c-gap-integration-downstream-failures.md)
- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md) § Job 1
- `projects/global-account-platform/specs/services/auth-service/architecture.md` § OAuth2 Authorization Server
- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 + introspect/revoke
- `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0008__create_oauth_tables.sql` (`demo-spa-client` seed)

---

# Related Contracts

- `auth-api.md` § OAuth2 Authorization Code + PKCE Flow
- `auth-api.md` § Refresh Token Rotation
- `auth-api.md` § Revoke + Introspect
- `auth-api.md` § Social Login (Google/Microsoft/Kakao)

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/` — main src (RC#1 production 회귀 가능) + integrationTest
- `projects/global-account-platform/apps/gateway-service/src/test/.../GatewayRateLimitIntegrationTest.java` (RC#3)

---

# Implementation Notes

- **첫 단계**: RC#1 12건의 단일 root cause 가설을 빠르게 검증/기각. `OAuth2AuthCodePkceIntegrationTest > authorize` 1건의 stack trace 가 `response_type` 파라미터 처리 경로를 정확히 가리키므로 1~2시간 안에 root cause 확정 가능 예상.
- RC#1 이 production SAS config drift 로 판명되면 `512cbbd4` 이후 `git log -p` 로 변경 추적 + revert 또는 forward fix.
- RC#2 는 resilience4j 패턴 (`CircuitBreakerRegistry.reset()`) 적용. `OAuthLoginIntegrationTest` 안에서 self-contained.
- RC#3 의 sporadic 여부 판정에 충분한 시간 투자 — deterministic 화 시 영구 회귀 방지.

---

# Edge Cases

1. **RC#1 이 SAS 1.4.1 의 알려진 breaking change**: SAS upstream 변경 사항 추적 후 우리 코드가 새 contract 에 적응. `JpaRegisteredClientRepository` mapping 갱신 가능.
2. **RC#1 이 V0008 seed `demo-spa-client` 의 grant_type JSON 미스매치**: seed 의 `authorization_grant_types` column 값이 SAS 가 기대하는 형식과 drift. seed 갱신.
3. **RC#1 이 어떤 단일 root cause 도 아님**: 12건이 서로 다른 cause → 044c-1-1 / 044c-1-2 sub-task 분할.
4. **RC#2 의 circuit-breaker reset 이 production 상태에 영향**: production `CircuitBreaker` bean 자체에는 영향 없도록 `@TestConfiguration` 으로 격리.
5. **RC#3 가 deterministic 도 sporadic 도 아닌 environmental 한 의존**: `@DisabledOnOs` 또는 GitHub Actions runner 자원 의존을 감안한 timeout 조정 검토.

---

# Failure Scenarios

## A. RC#1 단일 root cause + production 회귀 아님

이상적. 1 production fix + 1 회귀 보고서 단락.

## B. RC#1 production 회귀 + 비-trivial fix

PR scope 가 커지면 sub-task 분할 검토. 회귀 origin commit 까지 추적.

## C. RC#2 fix 가 다른 OAuth 테스트에 부수 영향

per-test breaker reset 이 다른 테스트 (OAuth2AuthorizationServer 등) 에서 의도된 breaker 상태를 깨뜨릴 수 있음. PR 검증에 포함.

## D. RC#3 가 sporadic 으로 판정 → nightly task 이관

본 task 는 RC#1+RC#2 16건 fix 로 종결 + RC#3 sporadic 격리 + TASK-MONO-044 § AC #8 nightly task 후속 발행.

---

# Test Requirements

- auth-service `integrationTest` PASS — 60/60 (RC#1 12 + RC#2 4 = 16 회복)
- gateway-service `integrationTest` PASS — 34/34 또는 33/34 (RC#3 deterministic vs sporadic)
- main CI `Integration (GAP)` Job 의 다음 run SUCCESS 확인
- 회귀 보고서 § GAP 단락 갱신 (044c-1 결과 1 단락)

---

# Definition of Done

- [ ] RC#1 12건 stack trace 수집 + root cause 분류
- [ ] RC#1 fix commit (production 또는 fixture)
- [ ] RC#2 fix commit (test isolation)
- [ ] RC#3 deterministic fix 또는 sporadic 격리 + nightly task 발행
- [ ] auth-service + gateway-service `integrationTest` 로컬 PASS
- [ ] main CI `Integration (GAP)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — RC#1 SAS 1.4.1 + JpaRegisteredClientRepository config drift 분석 + 잠재적 production 회귀 판정 + 12건 단일/복수 root cause 판정. RC#1 이 단일이고 fixture 이슈로 판명되면 사후 Sonnet 으로 다운그레이드 가능.
- **분량 추정**: RC#1 단일 production fix 면 small PR. 복수 cause + production 회귀면 medium PR.
- **dependency**:
  - `선행`: TASK-MONO-044c (머지 완료)
  - `후속`: 없음 (단, RC#3 sporadic 시 TASK-MONO-044 § AC #8 nightly task 발행)
- **CI gating**: 본 PR 자체는 `Integration (GAP)` Job 만 FAIL → SUCCESS 로 회복하는 것이 검증. 다른 Job 영향 0.
