# Task ID

TASK-R-02

# Title

libs/java-messaging에 Outbox 패턴 공통 코드 추출

# Status

review

# Owner

backend

# Task Tags

- refactor
- shared-library
- event

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

order-service, shipping-service, promotion-service에 중복된 Outbox 패턴 코드(OutboxWriter, OutboxPublisher, OutboxJpaEntity, OutboxPollingScheduler, ProcessedEvent 등)를 libs/java-messaging으로 추출한다. Outbox 패턴은 이벤트 발행 신뢰성을 보장하는 공통 기술 인프라 패턴이며, shared-library-policy의 "messaging abstractions used by multiple services" 허용 범주에 해당한다.

---

# Scope

## In Scope

- libs/java-messaging에 Outbox 패턴 공통 클래스 추출:
  - OutboxWriter (아웃박스 테이블에 이벤트 저장)
  - OutboxPublisher (아웃박스 테이블에서 미발행 이벤트 조회 및 Kafka 발행)
  - OutboxJpaEntity (아웃박스 JPA 엔티티)
  - OutboxPollingScheduler (주기적 아웃박스 폴링)
  - ProcessedEvent / ProcessedEventJpaEntity (멱등성 보장용 처리 완료 이벤트 기록)
- order-service, shipping-service, promotion-service에서 중복 Outbox 코드 제거
- 3개 서비스의 build.gradle에 libs/java-messaging 의존성 추가
- 서비스별 커스터마이징 포인트 제공 (topic 설정, serializer 등)

## Out of Scope

- Outbox 패턴의 동작 로직 변경
- 새로운 이벤트 타입 추가
- Kafka producer/consumer 설정 변경
- Outbox 패턴을 사용하지 않는 서비스에 적용

---

# Acceptance Criteria

- [ ] libs/java-messaging에 OutboxWriter 클래스가 존재한다
- [ ] libs/java-messaging에 OutboxPublisher 클래스가 존재한다
- [ ] libs/java-messaging에 OutboxJpaEntity 클래스가 존재한다
- [ ] libs/java-messaging에 OutboxPollingScheduler 클래스가 존재한다
- [ ] libs/java-messaging에 ProcessedEvent 관련 클래스가 존재한다
- [ ] order-service의 중복 Outbox 코드가 제거되고 libs/java-messaging을 참조한다
- [ ] shipping-service의 중복 Outbox 코드가 제거되고 libs/java-messaging을 참조한다
- [ ] promotion-service의 중복 Outbox 코드가 제거되고 libs/java-messaging을 참조한다
- [ ] 각 서비스에서 Outbox 기반 이벤트 발행이 정상 동작한다
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/platform/shared-library-policy.md`
- `specs/platform/event-driven-policy.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, 이벤트 계약 변경 없음. Outbox를 통해 발행되는 이벤트 페이로드는 기존과 동일)

---

# Target Service

- `libs/java-messaging`
- `order-service`
- `shipping-service`
- `promotion-service`

---

# Architecture

Follow:

- `specs/platform/shared-library-policy.md`
- `specs/platform/event-driven-policy.md`
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- 공통 Outbox 클래스는 서비스별 설정(topic name, serializer 등)을 주입받을 수 있도록 설계한다.
- OutboxJpaEntity는 공통 테이블 구조(id, aggregate_type, aggregate_id, event_type, payload, created_at, published, published_at)를 따른다.
- 서비스별 DB 마이그레이션(Flyway)은 각 서비스에 유지한다 (테이블 생성 DDL은 서비스 소유).
- ProcessedEvent는 consumer 멱등성 보장을 위한 event_id 기록 테이블이다.
- 공통 라이브러리는 Spring Boot auto-configuration을 활용하여 서비스가 의존성만 추가하면 자동 설정되도록 한다.

---

# Edge Cases

- 서비스별 OutboxJpaEntity 컬럼이 미세하게 다른 경우 -> 공통 필드만 추출하고 서비스별 확장 허용
- libs/java-messaging이 아직 존재하지 않는 경우 -> 라이브러리 모듈 신규 생성
- 서비스별 Outbox polling 주기가 다른 경우 -> 설정값으로 외부화하여 서비스별 오버라이드 가능하게 함
- ProcessedEvent 테이블 구조가 서비스마다 다른 경우 -> 공통 구조로 통일

---

# Failure Scenarios

- 공통 라이브러리 추출 시 서비스별 커스터마이징 포인트 누락으로 기능 장애 -> 서비스별 통합 테스트로 검증
- JPA 엔티티 매핑 변경으로 기존 데이터 호환성 문제 -> 동일한 테이블 구조 유지 확인
- auto-configuration 충돌로 Bean 생성 실패 -> 조건부 Bean 등록(@ConditionalOnMissingBean 등) 활용
- import 경로 변경 누락으로 컴파일 오류 -> 전 서비스 빌드 확인

---

# Test Requirements

- libs/java-messaging OutboxWriter 단위 테스트
- libs/java-messaging OutboxPublisher 단위 테스트
- 각 서비스의 기존 Outbox 관련 통합 테스트 통과 확인
- 각 서비스의 이벤트 발행 통합 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
