# Task ID

TASK-BE-031

# Title

security-service — Kafka DLQ 및 consumer lag 메트릭 배선

# Status

backlog

# Owner

backend

# Task Tags

- code
- deploy

# depends_on

- (없음)

---

# Goal

운영 성숙도 향상: 독성 메시지를 DLQ로 격리하고, consumer lag을 Prometheus로 노출해 장애 탐지와 재처리 루프를 확보한다.

---

# Scope

## In Scope

- security-service의 5개 consumer (`LoginAttemptedConsumer`, `LoginFailedConsumer`, `LoginSucceededConsumer`, `TokenRefreshedConsumer`, `TokenReuseDetectedConsumer`)에 `@RetryableTopic` 또는 수동 retry + DLQ 발행 설정
- DLQ 토픽 네이밍: `<source-topic>.DLQ`
- Micrometer 게이지: `kafka.consumer.lag{topic, group, partition}` — Spring Kafka micrometer 연동
- Retry 정책: exponential backoff 3회, 실패 시 DLQ로
- DLQ consumer는 이 태스크 범위 아님 (모니터링·수동 replay 전제)

## Out of Scope

- 자동 DLQ replay
- Grafana 대시보드 JSON (별도 태스크 여지)
- 다른 서비스 consumer

---

# Acceptance Criteria

- [ ] 의도적으로 역직렬화 실패 메시지 주입 → 3회 재시도 후 DLQ 토픽에 기록
- [ ] `/actuator/metrics/kafka.consumer.lag` 노출
- [ ] 기존 consumer 성공 경로 회귀 없음

---

# Related Specs

- `specs/services/security-service/architecture.md`
- `platform/observability.md` (있다면)

# Related Contracts

- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/security-service`

---

# Edge Cases

- retry 중 서비스 재시작 — offset은 commit되지 않아야 하며, 재시작 후 재시도 카운트 리셋 허용

---

# Failure Scenarios

- DLQ 토픽 자체 장애 → alert, 원본 consumer는 retry로 대기

---

# Test Requirements

- Integration: EmbeddedKafka + 독성 메시지 → DLQ 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Ready for review
