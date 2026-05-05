# Task ID

TASK-MONO-046-1

# Title

GAP auth-service IT 12건 deferred (TASK-MONO-046 § Failure Scenario B 분리)

# Status

ready

# Owner

backend / qa

# Task Tags

- test
- code

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

[TASK-MONO-046](TASK-MONO-046-gap-integration-residual-31.md) 의 fix PR 가 security-service 19 건은 deterministic root cause (5 cluster) 로 종결했으나, **auth-service 12건은 SAS 1.4.1 + JpaRegisteredClientRepository tracing 영역**으로 046 § Failure Scenario B 의 deferred 경로 적용 — 본 task 가 12 건의 SAS-side 회귀를 해소.

### 12건 분류

| Test class | 실패 수 | Cluster |
|---|---|---|
| OAuth2RefreshTokenIntegrationTest | 6/7 | refresh_token absent from authCode token response |
| OAuth2RevokeIntrospectIntegrationTest | 1/4 | authCode flow refresh_token (same cluster) |
| OAuth2AuthCodePkceIntegrationTest | 1/N | userinfo missing tenant_id claim |
| OAuthLoginIntegrationTest | 4/N | social-login `/oauth/callback/{provider}` returns non-200 |

12 건 모두 046 PR 에서 `@Disabled("TASK-MONO-046-1: SAS ...")` 마킹 — 본 task 머지 시 `@Disabled` 제거 + 실제 fix.

---

# Scope

## In Scope

### Cluster A — refresh_token absent (7건)

OAuth2RefreshToken 6 + OAuth2RevokeIntrospect 1. authorization_code → /oauth2/token 응답에 `refresh_token` 필드가 누락. 가설:

- (a) JpaRegisteredClientRepository → OAuthClientMapper.deserializeTokenSettings 가 `refresh-token-time-to-live` Duration 직렬화 round-trip 실패 → SAS 가 RT 발급 skip
- (b) DomainSyncOAuth2AuthorizationService 가 RT 발급 path 에서 SAS 의 RT generator 호출을 가로채는 회귀
- (c) demo-spa-client seed (V0008 line 208 `["authorization_code","refresh_token"]`) 의 grant_types 가 Mapper 단계에서 누락

### Cluster B — userinfo tenant_id (1건)

OAuth2AuthCodePkce > userinfo. `OidcUserInfoMapper` 가 access_token 의 `tenant_id` claim 을 userinfo 응답에 전달하지 못함.

### Cluster C — OAuth social-login callback regression (4건)

OAuthLoginIntegrationTest Google/Kakao/Microsoft happyPath + Microsoft preferredUsername fallback. `/oauth/callback/{provider}` 가 200 OK 가 아닌 4xx/5xx. 가설:

- (a) AccountServiceClient 가 잘못된 WireMock URL 사용 (044c-1 RC#2 잔재 — `@DirtiesContext` 후에도 회귀)
- (b) ProviderTokenExchange 의 tenant context 요구사항 변화
- (c) social_identities INSERT 가 schema 변경 후 truncation

### 진단 + fix

- 위 3 cluster 별 stack trace 수집 (CI artifact 또는 Docker-가용 환경 로컬 reproduce)
- Production fix 우선 — schema 가 source of truth
- Schema 변경 필요 시 별도 migration 분리

## Out of Scope

- security-service 19 건 (TASK-MONO-046 으로 종결)
- DLQ Routing 4 건 (별도 follow-up — 046 머지 시 통과 여부 확인 후 결정)
- Sporadic flakiness (TASK-MONO-044 § AC #8 영역)

---

# Acceptance Criteria

## 부팅 + 통과

1. `:projects:global-account-platform:apps:auth-service:integrationTest` PASS — 12 건 모두 `@Disabled` 제거 + 통과
2. main CI `Integration (GAP)` Job 다음 run SUCCESS

## 진단 + 분류

3. PR description 에 cluster 별 root cause + fix 전략 기록
4. Schema 변경 시 migration + 회귀 보고서 단락 추가

## 회귀 0

5. 046 시리즈 + security-service IT (20/20) 회귀 0
6. `knowledge/incidents/2026-05-05-ci-regression.md` 에 본 task 결과 단락 추가

---

# Related Specs

- [TASK-MONO-046](TASK-MONO-046-gap-integration-residual-31.md) — 직접 선행
- [TASK-MONO-044c-1](../done/TASK-MONO-044c-1-gap-integration-residual-17.md) — RC#2 해결 시도
- `projects/global-account-platform/specs/services/auth-service/`
- `projects/global-account-platform/specs/contracts/http/auth-api.md`

---

# Related Contracts

- `auth-api.md` § OAuth2 Refresh Token / UserInfo
- `auth-api.md` § OAuth Social Login Callback

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/src/main/java/...oauth2/`
- `projects/global-account-platform/apps/auth-service/src/main/java/...controller/...OAuthCallbackController.java`
- `projects/global-account-platform/apps/auth-service/src/test/java/...integration/`

---

# Implementation Notes

- **첫 단계**: WSL2 + Docker Desktop WSL 통합 활성화로 `:auth-service:integrationTest` 로컬 reproduce. 또는 CI 의 `--info` 로그 + JUnit XML 로 stack trace 수집.
- 2단계: cluster A 부터 — Mapper round-trip JSON 로그 출력 + RegisteredClient.getAuthorizationGrantTypes() 단위 검증.
- cluster C 의 (a) 가설 cross-check — OAuthLoginIntegrationTest 의 wireMock 인스턴스 재사용 vs 새 인스턴스 분포 검증.
- **검증 명령**:
  ```
  ./gradlew :projects:global-account-platform:apps:auth-service:integrationTest
  ```

---

# Edge Cases

1. **Cluster A 가 Mapper 회귀 (가설 (a))**: serializeSettings → deserializeSettings round-trip 단위 테스트 추가. 1 production fix.
2. **Cluster C 가 RC#2 잔재 (가설 (a))**: `@DirtiesContext` 가 충분치 않은 환경 발견 — Spring context 캐시 키 변경 또는 컨테이너별 isolated context.
3. **Schema 회귀**: V0014 onwards 에서 token settings JSON 컬럼 폭이 줄어들었거나 social_identities 컬럼 truncation. Migration + test fixture 동시 변경.

---

# Failure Scenarios

## A. SAS 1.4.1 → 1.4.2/1.5.x 업그레이드 필요

Spring Security 6.4 + SAS 1.4 의 known-issue 면 의존성 bump + behavior 변화 spec.

## B. RegisteredClient JPA round-trip 회귀

V0008 seed 의 token_settings JSON 폼이 SAS 1.4.1 의 SecurityJackson2Modules 와 호환 안 됨 — seed re-format + Mapper 검증.

## C. Cluster C 가 사실은 회귀가 아님

OAuth callback 이 본래 200 외 status (예: 302 redirect) 를 반환하도록 spec 변경된 거면 테스트가 stale — assertion 갱신.

---

# Test Requirements

- auth-service integrationTest 모두 PASS (60/60, `@Disabled` 제거 후)
- main CI `Integration (GAP)` Job 다음 run SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] Cluster A/B/C 별 stack trace 수집 + root cause 확정
- [ ] cause 별 production fix commit
- [ ] 12 건 `@Disabled` 제거
- [ ] auth-service integrationTest 로컬 PASS
- [ ] main CI `Integration (GAP)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — SAS internals + 3 cluster 동시 분석.
- **분량 추정**: medium (Mapper round-trip 회귀 단일이면 small). Schema 변경 동반 시 large.
- **dependency**:
  - `선행`: TASK-MONO-046 (`@Disabled` 마커 머지 후 본 task 가 제거)
  - `후속`: 본 task 머지 시 main `Integration (GAP)` Job 100% 통과 — 046 시리즈 완전 종결.
- **CI gating**: 본 PR 자체 영향 = `Integration (GAP)` Job 12 더 통과 (보전 16 → 28).
