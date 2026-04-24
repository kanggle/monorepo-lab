# Task ID

TASK-BE-119

# Title

auth-service AuthEvent → Kafka 발행 브리지 구현 — 인메모리 이벤트를 실제 Kafka로 전송

# Status

review

# Owner

backend

# Task Tags

- code
- event
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

auth-service의 `SpringAuthEventPublisher.publish()`가 현재 Spring `ApplicationEventPublisher`로 인메모리 이벤트만 발행하고, 이를 Kafka로 전송하는 브리지가 존재하지 않는 버그를 수정한다.

본 태스크 완료 후: 가입(`SignupService`, `OAuthService`) 시 `auth.user.signed-up` 이벤트가 Kafka로 정상 발행되어 user-service가 `user_profiles` 행을 자동 생성한다.

---

# Scope

## In Scope

- `AuthEventKafkaBridge` 인프라 컴포넌트 생성 (`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`)
- event 타입별 토픽 매핑: `UserSignedUp` → `auth.user.signed-up`
- `KafkaTemplate<String, String>` + JSON 직렬화, eventId를 메시지 key
- `application.yml` Kafka producer 설정
- 실패 시 `AuthMetricsRecorder.incrementEventPublishFailure` 호출
- 단위 + Testcontainers 통합 테스트

## Out of Scope

- Transactional Outbox 패턴
- 신규 이벤트 타입
- user-service 변경
- 과거 유저 백필
- DLQ/재시도 튜닝

---

# Acceptance Criteria

- [ ] `POST /api/auth/signup` 시 `auth.user.signed-up` 토픽에 메시지 발행
- [ ] 메시지 payload가 `specs/contracts/events/auth.user.signed-up.md` 스키마와 일치
- [ ] OAuth 최초 로그인에도 발행
- [ ] republish 엔드포인트 시 모든 users 행 발행
- [ ] Kafka 장애 시 `incrementEventPublishFailure` 호출, signup 성공 유지
- [ ] Testcontainers 통합 테스트 포함
- [ ] 기존 auth-service 테스트 통과

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/platform/error-handling.md`
- `specs/services/auth-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/events/auth.user.signed-up.md`

---

# Target Service

- `auth-service`

---

# Architecture

`specs/services/auth-service/architecture.md`

---

# Edge Cases

- Kafka 미기동 → signup 성공, 메트릭만
- republish는 트랜잭션 외부 → `fallbackExecution = true`
- 중복 eventId → user-service 멱등

---

# Failure Scenarios

- Kafka 장애 → 메트릭, signup 성공
- 직렬화 실패 → 로그 + 메트릭
- Spring 이벤트 전파 실패 → 기동 실패

---

# Test Requirements

- 단위: `AuthEventKafkaBridge`의 토픽/키/payload 검증
- 통합(Testcontainers Kafka): signup, republish 경로

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
