# Task ID

TASK-BE-126

# Title

Fix TASK-BE-118: AccountEventPublisherIntegrationTest를 AbstractIntegrationTest 상속으로 교체 및 MySQLContainer 스타트업 타임아웃 추가

# Status

ready

# Owner

backend

# Task Tags

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

# Goal

TASK-BE-118 리뷰에서 발견된 `AccountEventPublisherIntegrationTest`의 두 가지 테스팅 전략 위반을 수정한다.

1. **AbstractIntegrationTest 미상속**: `platform/testing-strategy.md` "Container Lifecycle" 규칙에 따르면 MySQL/Kafka가 필요한 통합 테스트는 반드시 `libs/java-test-support/.../AbstractIntegrationTest`를 상속해야 한다. 현재 구현은 자체 `@Container static MySQLContainer`를 선언하여 이 규칙을 위반한다.
2. **MySQLContainer 스타트업 타임아웃 누락**: `platform/testing-strategy.md` "Wait Strategy and Startup Timeout" 규칙에 따르면 모든 MySQL/Kafka 컨테이너에 `.withStartupTimeout(Duration.ofMinutes(3))`를 선언해야 한다. `AbstractIntegrationTest` 상속 시 공유 컨테이너가 이 설정을 이미 갖고 있으므로 자동으로 해결된다.

참고: `AccountSignupIntegrationTest`와 `SignupRollbackIntegrationTest`도 동일한 위반 패턴을 갖고 있으나, 이 태스크는 TASK-BE-118 범위인 `AccountEventPublisherIntegrationTest`만 수정한다.

---

# Scope

## In Scope

- `AccountEventPublisherIntegrationTest`를 `AbstractIntegrationTest` 상속으로 변경
- 자체 `@Container static MySQLContainer`, 클래스-레벨 `@DynamicPropertySource`, 자체 `isDockerAvailable()` 메서드 제거
- `@Testcontainers` 어노테이션 제거 (서브클래스 자체 @Container 없으면 불필요)
- `@EnabledIf("isDockerAvailable")` 제거 (AbstractIntegrationTest의 DockerAvailableCondition이 처리)
- `internal.api.token` 등 service-specific 프로퍼티를 `@TestPropertySource` 또는 `application-test.yml`로 공급
- 빌드 및 모든 통합 테스트 통과 확인

## Out of Scope

- `AccountSignupIntegrationTest`, `SignupRollbackIntegrationTest` 수정 (별도 태스크)
- 이벤트 계약 또는 도메인 로직 변경 없음
- 새로운 테스트 시나리오 추가 없음

---

# Acceptance Criteria

- [ ] `AccountEventPublisherIntegrationTest`가 `AbstractIntegrationTest`를 상속한다
- [ ] 클래스 내 자체 `@Container MySQLContainer` 필드, `isDockerAvailable()` 메서드, 클래스-레벨 `@DynamicPropertySource`가 제거된다
- [ ] `AbstractIntegrationTest.MYSQL` 공유 컨테이너를 통해 MySQL이 공급된다
- [ ] `internal.api.token` 등 필수 service-specific 프로퍼티가 올바르게 공급된다
- [ ] 기존 두 테스트 메서드의 검증 로직(UUID v7 eventId, 계약 필드, actorId null 처리)이 동일하게 통과된다
- [ ] `./gradlew :apps:account-service:test` 통과

---

# Related Specs

- `platform/testing-strategy.md` — Container Lifecycle, Wait Strategy and Startup Timeout 섹션
- `specs/services/account-service/architecture.md` — Testing Expectations 섹션

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/account-events.md` — account.locked payload (검증 로직은 유지)

---

# Target Service

- `account-service`

---

# Architecture

Follow:

- `specs/services/account-service/architecture.md` — Layered Architecture
- `platform/testing-strategy.md` — Container Lifecycle: AbstractIntegrationTest 상속 의무

---

# Implementation Notes

- `AbstractIntegrationTest`는 MySQL + Kafka를 static initializer에서 1회 시작하고 `@DynamicPropertySource`로 `spring.datasource.*`와 `spring.kafka.bootstrap-servers`를 등록한다. 서브클래스는 이 프로퍼티를 중복 등록할 필요 없다.
- `AccountEventPublisherIntegrationTest`는 KafkaTemplate을 MockitoBean으로 스텁하므로 Kafka 실제 통신은 불필요하지만, AbstractIntegrationTest가 Kafka도 기동하므로 부작용 없다.
- `internal.api.token` 프로퍼티는 `@TestPropertySource(properties = "internal.api.token=test-internal-token")`으로 공급하거나, `application-test.yml`에 기본값을 추가한다.
- AbstractIntegrationTest는 `@ExtendWith(DockerAvailableCondition.class)`를 통해 Docker 가용성을 처리하므로 서브클래스에서 별도 `@EnabledIf` 불필요.

---

# Edge Cases

- `internal.api.token` 미공급 시 Spring 컨텍스트 로드 실패 → `@TestPropertySource` 또는 `application-test.yml` 기본값으로 처리
- AbstractIntegrationTest Docker 미가용 시 → `DockerAvailableCondition`이 테스트 클래스를 자동으로 SKIPPED 처리

---

# Failure Scenarios

- AbstractIntegrationTest 상속 후 MySQL 프로퍼티 충돌 → 자체 `@DynamicPropertySource` 중복 등록을 피하고 AbstractIntegrationTest 등록만 사용
- 컨텍스트 로드 실패 시 → 누락된 프로퍼티 확인 후 보완

---

# Test Requirements

- 기존 `AccountEventPublisherIntegrationTest`의 두 테스트가 AbstractIntegrationTest 상속 후에도 동일하게 통과되어야 한다
- `./gradlew :apps:account-service:test` 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
