# TASK-BE-208: application-test.yml 추가 — security-service

## Goal

security-service가 다른 서비스들과 달리 `src/test/resources/application.yml`을 사용하는
비표준 패턴을 수정한다.

프로젝트 표준 패턴:
- `src/test/resources/application-test.yml` + `@ActiveProfiles("test")` on `@SpringBootTest` tests

security-service 현재 패턴:
- `src/test/resources/application.yml` (항상 로드, 프로파일 불필요)
- `@ActiveProfiles("test")` 없음

## Scope

1. `src/test/resources/application-test.yml` 신규 생성 (현재 `application.yml` 내용 이전)
2. `src/test/resources/application.yml` 삭제 (`git rm`)
3. 6개 테스트 클래스에 `@ActiveProfiles("test")` 추가 (`security-service.internal-token` 로드 필요):
   - `apps/security-service/src/test/java/com/example/security/integration/DetectionE2EIntegrationTest.java`
   - `apps/security-service/src/test/java/com/example/security/integration/DlqRoutingIntegrationTest.java`
   - `apps/security-service/src/test/java/com/example/security/integration/LoginHistoryImmutabilityIntegrationTest.java`
   - `apps/security-service/src/test/java/com/example/security/integration/SecurityServiceIntegrationTest.java`
   - `apps/security-service/src/test/java/com/example/security/query/internal/SuspiciousEventQueryControllerTest.java` (@WebMvcTest)
   - `apps/security-service/src/test/java/com/example/security/query/internal/LoginHistoryQueryControllerTest.java` (@WebMvcTest)

비대상:
- `@DataJpaTest` 테스트 3개 (`AccountLockHistoryJpaRepositoryTest`, `LoginHistoryJpaRepositoryTest`, `SuspiciousEventJpaRepositoryTest`) — `@DynamicPropertySource`로 필요한 설정 모두 제공, `@ActiveProfiles` 불필요

## Acceptance Criteria

- [ ] `src/test/resources/application-test.yml` 생성 — 기존 `application.yml` 내용 동일
- [ ] `src/test/resources/application.yml` 삭제
- [ ] 6개 테스트 클래스에 `@ActiveProfiles("test")` 어노테이션 및 import 추가
- [ ] `./gradlew :apps:security-service:test` BUILD SUCCESSFUL

## Related Specs

- `platform/testing-strategy.md`

## Related Contracts

없음

## Edge Cases

- Redis host/port는 통합 테스트의 `@DynamicPropertySource`가 override하므로 `application-test.yml` 상속값 문제 없음
- `@DataJpaTest` 테스트는 `src/main/resources/application.yml`의 `spring.jpa.hibernate.ddl-auto: validate`를 상속하며, datasource/flyway는 `@DynamicPropertySource`로 제공 → 기존 동작 유지

## Failure Scenarios

- `@DataJpaTest` 테스트가 `spring.kafka.*` auto-configuration을 시도해 실패할 수 있음 — `@DataJpaTest`는 Kafka를 auto-configure하지 않으므로 해당 없음
