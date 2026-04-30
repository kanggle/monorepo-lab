# TASK-BE-241: fix — TASK-BE-237 OAuthClientBeanRegistrationTest 슬라이스 경량화

## Goal

TASK-BE-237 리뷰에서 발견된 이슈 수정.

`OAuthClientBeanRegistrationTest`는 OAuth 클라이언트 빈 등록 회귀를 막기 위한 가드 테스트로 추가됐으나, `AbstractIntegrationTest`를 상속해서 MySQL + Kafka + Redis 컨테이너 3개가 모두 떠야 실행된다. 어느 하나라도 불가용이면 `DockerAvailableCondition`이 발동해서 3개 테스트가 **사유 없이 silently skipped** 처리된다 (XML 리포트에서 `<skipped/>` 빈 메시지). 이는 BE-237 acceptance criterion("새 테스트가 Docker 불가용 시 silently skip되지 않을 것")을 위반한다.

또한 Redis `GenericContainer` 선언에 `.withStartupTimeout(Duration.ofMinutes(3))`이 빠져있어 `platform/testing-strategy.md`의 Wait Strategy 규칙을 어긴다.

테스트의 본질은 ApplicationContext가 OAuth 클라이언트 빈 3개를 정상 등록하는지 확인하는 것이므로, MySQL/Kafka 의존성을 제거한 가벼운 슬라이스로 재구성한다.

## Scope

**In:**
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/oauth/OAuthClientBeanRegistrationTest.java` 재작성:
  - `AbstractIntegrationTest` 상속 제거
  - `@SpringBootTest` + `@TestPropertySource` 또는 mocked DataSource/KafkaTemplate으로 MySQL/Kafka 의존성 제거
  - Redis 의존성도 가급적 mock(@MockBean) 또는 제거 — OAuth 빈 자체는 Redis에 의존하지 않음. JwksClient의 RestClient만 있으면 충분
  - Docker 없이도 실행 가능한 형태가 목표 (no Testcontainers)
- 만약 Redis 컨테이너가 본질적으로 필요하다면 `.withStartupTimeout(Duration.ofMinutes(3))` 추가

**Out:**
- `GoogleOAuthClient`, `MicrosoftOAuthClient`, `KakaoOAuthClient` 코드 변경 없음 (BE-237 본 fix는 정상)
- 다른 OAuth 테스트 변경 없음

## Acceptance Criteria

- [ ] `OAuthClientBeanRegistrationTest` 3개 테스트가 Docker 없이도 실행됨 (skipped 0개)
- [ ] 테스트 실행 시 OAuth 클라이언트 빈 3개(`GoogleOAuthClient`, `KakaoOAuthClient`, `MicrosoftOAuthClient`)가 모두 등록되는지 검증
- [ ] 만약 어떤 컨테이너 의존성이 남는다면 `.withStartupTimeout(Duration.ofMinutes(3))` 명시
- [ ] `./gradlew :apps:auth-service:test --tests "*OAuthClientBeanRegistrationTest*"` 통과 (skipped 없이)
- [ ] 기존 OAuth 단위 테스트 전체 회귀 없음

## Related Specs

- `platform/testing-strategy.md` (Wait Strategy and Startup Timeout)
- `specs/services/auth-service/architecture.md`

## Related Contracts

- 없음 (테스트 슬라이스 재구성만)

## Edge Cases

- `OAuthProperties` (`@ConfigurationProperties`)는 `@EnableConfigurationProperties(OAuthProperties.class)`가 `AuthApplication`에 있어 `@SpringBootTest`로는 자동 로드됨. 슬라이스 테스트로 좁힐 경우 명시적으로 `@EnableConfigurationProperties` 또는 `@Import` 필요
- OAuth 클라이언트가 RestClient를 생성자에서 직접 빌드하므로 외부 호출이 발생하지 않게 단위 수준 검증으로 충분 — 빈 인스턴스 생성 자체가 회귀 신호

## Failure Scenarios

- 슬라이스를 너무 좁히면 실제 ApplicationContext 와 다른 wiring 결과를 낼 수 있음 → `@SpringBootTest` 유지하되 의존 인프라 빈만 mock
- 의존 빈 mock 누락으로 컨텍스트 로드 실패 → 실패 메시지에서 누락된 빈 식별 후 `@MockBean` 추가
