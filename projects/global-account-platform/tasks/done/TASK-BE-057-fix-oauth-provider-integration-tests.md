# Task ID

TASK-BE-057

# Title

OAuth provider integration tests — Google / Kakao / Microsoft @SpringBootTest 커버

# Status

ready

# Owner

backend

# Task Tags

- test

# depends_on

- TASK-BE-053
- TASK-BE-056

---

# Goal

Fix follow-up for [TASK-BE-056](../done/TASK-BE-056-oauth-microsoft-provider.md) (and backlog gap from TASK-BE-053): OAuth social login의 전체 callback 흐름에 대한 `@SpringBootTest` + WireMock 통합 테스트를 세 provider(Google / Kakao / Microsoft) 모두에 대해 추가한다. 현재 OAuth는 provider별 unit 테스트만 있고 end-to-end 통합 테스트(useCase → DB → outbox → 이벤트) 검증이 없다.

리뷰에서 식별된 사항: TASK-BE-056 Test Requirements의 Integration Tests 섹션을 충족하려 했으나, 동일 gap이 기존 Google/Kakao에도 존재하여 Microsoft만 추가하면 baseline 불일치. 이 태스크로 세 provider 동시에 수준을 올린다.

---

# Scope

## In Scope

1. **Integration test 클래스**: `apps/auth-service/src/test/java/com/example/auth/integration/OAuthLoginIntegrationTest.java` (또는 provider별 분리)
2. **커버 시나리오**:
   - Google provider: `/api/auth/oauth/authorize?provider=google&redirectUri=...` → authorize URL 생성 + state Redis 저장
   - Google provider: `/api/auth/oauth/callback` (WireMock mocked token endpoint) → JWT pair 발급 + `social_identities` row 생성 + outbox 이벤트 `loginMethod=OAUTH_GOOGLE`
   - Kakao provider: 동일 흐름, `loginMethod=OAUTH_KAKAO`. Kakao는 user-info endpoint 별도 호출하므로 두 번째 stub 필요
   - Microsoft provider: 동일 흐름, `loginMethod=OAUTH_MICROSOFT`, id_token `sub`/`email` 파싱 확인
   - 기존 email 계정 자동 연결: 세 provider 중 최소 1개로 시나리오 커버
   - state 불일치/만료: 401 `INVALID_STATE` 반환 확인
   - provider 장애(5xx): 502 `PROVIDER_ERROR` 반환 확인
3. **WireMock 셋업**: Testcontainers(MySQL + Redis + Kafka) + WireMock dynamic port, OAuthProperties.tokenUri를 테스트 profile에서 WireMock baseUrl로 재지정

## Out of Scope

- JWKS 서명 검증 강화 (별도 태스크)
- 실제 provider 엔드포인트 호출
- Frontend/gateway-service 계층 (gateway route 테스트는 gateway 전용 태스크)
- account-service 계층 통합 (이미 WireMock으로 mocked)

---

# Acceptance Criteria

- [ ] `OAuthLoginIntegrationTest` (또는 분리된 클래스들)가 세 provider 모두에 대해 authorize + callback happy path 커버
- [ ] outbox 이벤트에 `loginMethod` 값이 올바른 `OAUTH_GOOGLE | OAUTH_KAKAO | OAUTH_MICROSOFT`로 기록됨을 검증
- [ ] `social_identities` row가 올바른 `provider`, `provider_user_id`로 생성됨을 DB 조회로 검증
- [ ] state 만료 시나리오: Redis에서 state 삭제 후 callback 호출 → 401 반환
- [ ] provider 5xx 시나리오: WireMock이 500 반환 → 502 `PROVIDER_ERROR`
- [ ] 기존 email 계정 자동 연결: 사전 DB 시드 + 소셜 callback → `isNewAccount: false`, `social_identities` 생성
- [ ] `./gradlew :apps:auth-service:test` 통과
- [ ] Testcontainers가 없는 환경에서는 `@EnabledIfEnvironmentVariable` 또는 현재 프로젝트 관례에 맞춰 조건부 skip

---

# Related Specs

- `specs/features/oauth-social-login.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/redis-keys.md`
- `platform/testing-strategy.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/events/auth-events.md`
- `specs/contracts/http/internal/auth-to-account-social.md` (WireMock stub 대상)

---

# Target Service

- `apps/auth-service` (primary)

---

# Architecture

- `specs/services/auth-service/architecture.md` — Layered 4-layer

---

# Edge Cases

- WireMock 포트 충돌: dynamic port 사용
- Testcontainers 실행 불가 환경(CI 일부): `@EnabledIfDockerAvailable` 관례 확인 후 동일 적용
- 동시 callback (같은 state) 시나리오는 unit level에서 커버 가능하므로 integration 필수 아님

---

# Failure Scenarios

- WireMock token endpoint 5xx → 502
- WireMock token endpoint에서 id_token 없음 → 502
- Redis 미가동 → 테스트 자체 실패 (Testcontainers 설정 문제)
- Kafka 미가동 → outbox는 기록되나 publish relay가 실패. 테스트는 outbox row 존재만 검증하여 Kafka 장애와 분리

---

# Test Requirements

본 태스크 자체가 test-only 태스크다. Unit test 추가는 불필요.

## Integration Tests

위 Scope에서 정의됨.

---

# Definition of Done

- [ ] 세 provider 모두에 대한 통합 테스트 커버
- [ ] outbox + social_identities 검증
- [ ] 실패 시나리오 2건 이상 (state 만료, provider 5xx)
- [ ] `./gradlew :apps:auth-service:test` 통과
- [ ] Ready for review
