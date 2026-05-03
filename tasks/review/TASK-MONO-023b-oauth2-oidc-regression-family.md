# Task ID

TASK-MONO-023b

# Title

OAuth2 / OIDC 통합 테스트 회귀 family fix (7 테스트 클래스)

# Status

review

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

[TASK-MONO-023](../in-progress/TASK-MONO-023-main-baseline-integration-cleanup.md) 의 분류 매트릭스에서 식별된 **OAuth2 / OIDC 관련 7 통합 테스트** 의 회귀를 fix 한다:

| 테스트 클래스 | 증상 |
|---|---|
| `auth.OAuth2AuthorizationServerIntegrationTest` | Status 200 vs 401 |
| `auth.OAuth2JpaPersistenceIntegrationTest` | Status 200 vs 401 |
| `auth.OAuth2RevokeIntrospectIntegrationTest` | Status 200 vs 401 |
| `auth.OAuth2AuthCodePkceIntegrationTest` | Status 400 vs REDIRECTION |
| `auth.OAuth2RefreshTokenIntegrationTest` | Status 400 vs REDIRECTION |
| `auth.OAuthLoginIntegrationTest` | Status 200 vs 503 (소셜 OAuth WireMock 503) |
| `auth.AuthIntegrationTest` | Status 429 vs 200 (rate limit 미동작) |

이 테스트들은 PR #107 의 SAS 도입 (TASK-BE-251) 이후 main 에 머지됐으나 통합 테스트가 그 시점부터 실패 누적. 공통 root cause 추정:

1. **`oauth_clients` seed 누락** — 테스트가 기대하는 client_id 가 DB 에 등록되지 않음 → 401
2. **issuer / JWKS URI 불일치** — `iss` claim 검증 실패 → 401
3. **redirect_uri 검증 strict** — 등록되지 않은 URI 로 인한 400
4. **WireMock 503 시뮬레이션 실패** — 외부 OAuth provider mock setup 회귀
5. **rate limit Redis 키 mismatch** — multi-tenant 도입 후 키 패턴 변경

이 태스크 완료 후 위 7 테스트가 모두 PASS.

---

# Scope

## In Scope

- 7 테스트 클래스의 첫 PASS 시점 (PR #107 머지 직전) 과 비교 — git blame
- 공통 root cause 가능성 (seed / issuer / 키 패턴) 별로 grouped fix
- TASK-BE-252 (OAuth2 JPA 영속화) 의 `oauth_clients` seed 가 통합 테스트에서 로드되는지 확인
- `application-test.yml` / `application-integration.yml` 의 OIDC 설정 검토
- 회귀 fix 시 production 코드 변경 가능 (보통 config 수정)
- 5회 연속 PASS 검증

## Out of Scope

- Provisioning 회귀 — TASK-MONO-023a
- Audit / Anonymization 회귀 — TASK-MONO-023c
- Outbox 회귀 — TASK-MONO-023d
- Community JPA 격리 — TASK-MONO-023e
- 새 OAuth2 기능 추가 — 본 태스크 범위 밖

---

# Acceptance Criteria

- [ ] 7 테스트 클래스 모두 PASS (단일 + 5회 연속)
- [ ] 공통 root cause 식별 + 단일 fix 또는 분리 fix 모두 문서화
- [ ] `oauth_clients` seed 가 통합 테스트 환경에서 로드됨 확인
- [ ] config 변경 시 spec / production 환경 영향 분석

---

# Related Specs

- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md` — SAS 도입 결정
- `projects/global-account-platform/specs/services/auth-service/architecture.md`
- `projects/global-account-platform/specs/features/authentication.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` § OAuth2 / OIDC Endpoints

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/`
  - `OAuth2AuthorizationServerIntegrationTest.java`
  - `OAuth2JpaPersistenceIntegrationTest.java`
  - `OAuth2RevokeIntrospectIntegrationTest.java`
  - `OAuth2AuthCodePkceIntegrationTest.java`
  - `OAuth2RefreshTokenIntegrationTest.java`
  - `OAuthLoginIntegrationTest.java`
  - `AuthIntegrationTest.java`
- `projects/global-account-platform/apps/auth-service/src/main/resources/application*.yml` (config 검토)
- `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0008__create_oauth_tables.sql`, `V0009__seed_*` (seed 검토)

---

# Implementation Notes

- 첫 단계: 7 테스트 중 2~3 개를 직접 실행하여 stack trace 와 실제 401/400 의 root error 파악
  - `./gradlew :apps:auth-service:integrationTest --tests "OAuth2AuthorizationServerIntegrationTest" --info`
- `oauth_clients` seed 가 통합 테스트의 Flyway 마이그레이션에 포함되는지 확인 (`spring.flyway.locations`)
- TASK-BE-252 의 fix commit 이후 회귀 발생 가능 — `git log --oneline -- '**OAuth2*Integration*' --since='2026-04-25'`
- WireMock 503 처리: `WireMockExtension` 의 stub 이 의도대로 작동하는지 확인
- rate limit: `RedisRateLimiter` 의 키 패턴이 `tenant_id` 도입 후 변경되었는지 확인 (TASK-BE-248)

---

# Edge Cases

- 7 테스트 모두 같은 root cause → 단일 fix 로 해결
- 4~5 개는 같은 cause + 2~3 개는 별개 cause → 단일 PR 에 grouped fix
- 7 개가 모두 별개 cause → sub-sub-task 분할 (023b1~b7)

---

# Failure Scenarios

- production 코드 변경이 다른 OIDC 흐름에 영향 → 회귀 영향 분석
- seed 추가가 prod 환경에 영향 → seed 만 통합 테스트 profile 에 한정 (`db/migration-integration/`)

---

# Test Requirements

- 7 통합 테스트 단일 PASS
- 5회 연속 PASS
- production fix 가 있다면 단위 테스트로도 cover

---

# Definition of Done

- [ ] 7 테스트 클래스 PASS
- [ ] root cause 분석 결과 PR description 에 기록
- [ ] production / config / seed fix
- [ ] 단위 테스트 보강 (필요 시)
- [ ] Ready for review
