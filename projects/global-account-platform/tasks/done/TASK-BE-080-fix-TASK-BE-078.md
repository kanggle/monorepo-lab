# Task ID

TASK-BE-080

# Title

fix(test): TASK-BE-078 review — LoginHistoryImmutabilityTest 미이행 마이그레이션

# Status

done

> **리뷰 결과 (2026-04-24)**: fix_needed — TASK-BE-082 승계
>
> LoginHistoryImmutabilityTest 의 AbstractIntegrationTest 상속 및 MySQL/Kafka container 제거는
> 올바르게 구현되었으나, 다음 두 가지 구조적 결함이 발견되었다:
>
> 1. `libs/java-test-support` 의 HEAD 상태에서 `AbstractIntegrationTest` 가 `src/main/java` 에 위치하고
>    `build.gradle` 에 `java-test-fixtures` 플러그인이 없음. TASK-BE-078 (7cabe8b) 에서 testFixtures 이동이
>    구현되었으나 master 브랜치에 병합되지 않아 현재 Testcontainers 클래스가 production classpath 로 노출됨.
> 2. `apps/security-service/build.gradle` 이 `testImplementation project(...)` (일반 의존성) 를 사용하며
>    testFixtures 구조가 복원되면 `testImplementation testFixtures(project(...))` 로 변경이 필요함.
>
> TASK-BE-082 에서 수정 예정.

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-078 (done)

---

# Goal

TASK-BE-078 리뷰에서 발견된 누락 항목을 수정한다.

`apps/security-service/.../LoginHistoryImmutabilityTest` 가 여전히 자체 `@Container static MySQLContainer` / `KafkaContainer` (`confluentinc/cp-kafka:7.5.0`) 를 선언하고 있다.
TASK-BE-078 Scope C 에는 "LoginHistoryImmutabilityTest 도 `7.5.0` 을 직접 참조하는 경우 동일하게 마이그레이션 적용" 이라고 명시되어 있으나 구현이 누락되었다.

---

# Scope

## In Scope

- `apps/security-service/.../LoginHistoryImmutabilityTest` 를 `AbstractIntegrationTest` 를 상속하도록 수정
- 자체 `@Container static MySQLContainer` / `@Container static KafkaContainer` 선언 및 관련 `@DynamicPropertySource` 에서 MySQL/Kafka 속성 제거
- Redis `@Container` 및 관련 `@DynamicPropertySource` 는 유지
- `confluentinc/cp-kafka:7.5.0` 직접 참조 제거 (base class 의 `7.6.0` 자동 사용)

## Out of Scope

- 다른 테스트 클래스 수정
- 기존 기능 변경

---

# Acceptance Criteria

- [x] `LoginHistoryImmutabilityTest` 가 `AbstractIntegrationTest` 를 extend
- [x] 클래스에서 자체 `@Container MySQLContainer` / `KafkaContainer` 선언 제거
- [x] `@DynamicPropertySource` 에서 MySQL/Kafka 관련 속성 제거
- [x] `confluentinc/cp-kafka:7.5.0` 직접 참조 없음
- [ ] `platform/testing-strategy.md` Container Lifecycle MUST 규칙 위반 없음 ← TASK-BE-082 에서 수정
- [ ] 기존 green 테스트 회귀 없음 (`./gradlew :apps:security-service:test`) ← 미검증

---

# Related Specs

- `platform/testing-strategy.md` (Container Lifecycle 섹션)
- `platform/shared-library-policy.md`

---

# Related Contracts

없음 (test infra)

---

# Target Service

- `apps/security-service`
