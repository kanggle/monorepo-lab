# Task ID

TASK-BE-076

# Title

product-service 통합 테스트 Kafka 설정 추가 — KafkaTemplate Bean 누락으로 컨텍스트 로딩 실패

# Status

done

# Owner

backend

# Task Tags

- code, test

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

product-service의 `@SpringBootTest` 통합 테스트가 `KafkaTemplate` Bean 누락으로 Spring 컨텍스트 로딩에 실패하는 문제를 해결한다. `KafkaProductEventPublisher`가 `KafkaTemplate<String, Object>`를 주입받지만, 통합 테스트 환경에 Kafka 관련 설정이 없어 `AdjustStockService` → `KafkaProductEventPublisher` 의존성 체인에서 `NoSuchBeanDefinitionException`이 발생한다.

---

# Scope

## In Scope

- 통합 테스트에서 `KafkaTemplate` Bean을 제공하는 설정 추가 (TestConfiguration 또는 `@MockBean`)
- 기존 `ProductRepositoryIntegrationTest` 포함 전체 통합 테스트 컨텍스트 로딩 정상화
- TASK-BE-063에서 추가한 soft-delete 통합 테스트 통과 확인

## Out of Scope

- Kafka 이벤트 발행/소비 통합 테스트 (Testcontainers Kafka 도입은 별도 태스크)
- `KafkaProductEventPublisher` 로직 변경
- 단위 테스트 변경

---

# Acceptance Criteria

- [ ] `ProductRepositoryIntegrationTest`의 모든 테스트가 통과한다
- [ ] `@SpringBootTest` 기반 통합 테스트에서 Spring 컨텍스트가 정상 로딩된다
- [ ] Kafka 관련 Bean 누락 에러가 발생하지 않는다

---

# Related Specs

- `specs/services/product-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- 다른 통합 테스트 클래스에서도 동일 설정이 적용되는지 확인
- Mock 처리된 `KafkaTemplate`이 실제 Kafka 연결을 시도하지 않는지 확인

---

# Failure Scenarios

- 설정 누락 시 모든 `@SpringBootTest` 통합 테스트가 컨텍스트 로딩 실패로 전체 실패
- TASK-BE-063 soft-delete 통합 테스트 검증 불가

---

# Test Requirements

- 기존 `ProductRepositoryIntegrationTest` 전체 테스트 통과
- 다른 `@SpringBootTest` 통합 테스트 존재 시 동일하게 통과 확인
