# Task ID

TASK-BE-006

# Title

Kafka 토픽 설계 + auth-service outbox relay producer 구현

# Status

ready

# Owner

backend

# Task Tags

- code
- event

# depends_on

- TASK-BE-003
- TASK-BE-005

---

# Goal

Kafka 토픽을 공식적으로 생성하고, auth-service의 outbox relay가 `auth.*` 이벤트를 실제 Kafka 토픽에 발행하도록 구현한다.

---

# Scope

## In Scope

- Kafka 토픽 생성 스크립트/설정: `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`, `auth.token.refreshed`, `auth.token.reuse.detected`, `account.created`, `account.status.changed`, `account.locked`, `account.unlocked`, `account.deleted`, `session.revoked`, `security.suspicious.detected`, `security.auto.lock.triggered`, `admin.action.performed` + 각 `.dlq`
- 토픽 설정: partition 수, replication factor, retention, cleanup policy
- auth-service outbox relay 구현: `outbox_events` 테이블 폴링 → Kafka 발행 → `published_at` 갱신
- `libs/java-messaging` outbox producer wrapper 활용
- outbox lag 메트릭: `auth_outbox_lag_seconds`

## Out of Scope

- consumer 구현 (TASK-BE-008에서)
- 다른 서비스의 outbox relay (각 서비스 태스크에서)
- Kafka Connect / Debezium (현재 폴링 기반)

---

# Acceptance Criteria

- [ ] `docker compose up` 후 Kafka UI에서 14개 메인 토픽 + 14개 DLQ 토픽 확인 (28개)
- [ ] 토픽 생성이 **멱등** (재실행 시 에러 없음)
- [ ] auth-service 로그인 성공 → outbox row 생성 → relay가 `auth.login.succeeded` 토픽에 발행 → `published_at` 갱신
- [ ] Kafka UI에서 메시지 확인: envelope 형식 (`eventId`, `eventType`, `source`, `occurredAt`, `schemaVersion`, `partitionKey`, `payload`)
- [ ] 파티션 키가 `account_id`로 설정됨 (같은 계정의 이벤트 순서 보장)
- [ ] `auth_outbox_lag_seconds` 메트릭이 Prometheus에 노출
- [ ] Kafka 장애 시 outbox에 계속 쌓이고 복구 후 일괄 발행

---

# Related Specs

- `specs/contracts/events/auth-events.md`
- `specs/contracts/events/account-events.md`
- `specs/contracts/events/security-events.md`
- `specs/contracts/events/admin-events.md`
- `specs/contracts/events/session-events.md`
- `platform/event-driven-policy.md`
- `specs/services/auth-service/dependencies.md` — Kafka producer 섹션

# Related Skills

- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md` (이 태스크에서 실제 발행)
- 나머지 이벤트 컨트랙트 (토픽 생성만, 발행은 각 서비스)

---

# Target Service

- `apps/auth-service` (producer)
- root (토픽 생성 스크립트)

---

# Implementation Notes

- 토픽 생성: Docker Compose에 `kafka-init` 서비스 추가 또는 `scripts/kafka-create-topics.sh` 스크립트
- Partition: 메인 토픽 3개 (로컬 개발용), DLQ 1개
- Retention: 7일 (기본), DLQ 30일
- Outbox relay: `@Scheduled` 폴링 (1초 주기) 또는 libs/java-messaging의 기본 구현 활용
- 발행 성공 시 `published_at = now()` UPDATE. 실패 시 다음 폴링에 재시도
- relay는 auth-service 프로세스 내에서 실행 (별도 프로세스 불필요, 로컬 개발 규모)

---

# Edge Cases

- Kafka 브로커 미기동 상태에서 auth-service 기동 → outbox에 쌓이고 Kafka 복구 후 발행 (비블로킹)
- 동일 outbox row 중복 발행 → consumer 측 eventId dedupe로 방어 (T8)
- 토픽 생성 스크립트 재실행 → 이미 존재하면 skip (멱등)

---

# Failure Scenarios

- Kafka 장애 → outbox lag 증가 → 알림. 로그인 경로는 영향 없음 (outbox write만 트랜잭션 포함)
- Outbox relay 장애 → lag 증가. relay 재시작 시 미발행 건 일괄 처리

---

# Test Requirements

- Integration: `@SpringBootTest` + Testcontainers (Kafka + MySQL) — 로그인 → outbox → Kafka 토픽에 메시지 도착 확인
- Idempotency: 같은 outbox row 2회 relay 시도 → Kafka에 1회만 도착 (또는 consumer dedupe로 보완)
- Topic creation: 스크립트 2회 실행 → 에러 없음

---

# Definition of Done

- [ ] 28개 토픽 생성 완료 (14 main + 14 DLQ)
- [ ] auth-service outbox relay 동작 확인
- [ ] Kafka UI에서 이벤트 메시지 확인
- [ ] outbox lag 메트릭 노출
- [ ] Tests passing
- [ ] Ready for review
