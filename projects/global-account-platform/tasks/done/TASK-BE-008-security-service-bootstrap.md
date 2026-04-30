# Task ID

TASK-BE-008

# Title

security-service 부트스트랩 — Kafka consumer, 로그인 이력 적재, 멱등 처리, 내부 조회 API

# Status

ready

# Owner

backend

# Task Tags

- code
- event

# depends_on

- TASK-BE-003
- TASK-BE-006

---

# Goal

security-service를 Kafka event-consumer 기반 Spring Boot 애플리케이션으로 초기화하고, auth-service의 로그인 이벤트를 소비하여 `login_history` 테이블에 append-only로 적재하는 최소 골든패스를 구현한다. admin-service용 내부 조회 API도 포함.

---

# Scope

## In Scope

- `apps/security-service/` 모듈 생성
- 패키지 구조: `consumer / application / domain / query / infrastructure` ([architecture.md](../../specs/services/security-service/architecture.md))
- Kafka consumer: `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`, `auth.token.refreshed`, `auth.token.reuse.detected`
- 멱등 처리: Redis `security:event-dedup:{eventId}` + MySQL `processed_events` fallback
- Flyway: `login_history` (append-only, DB 트리거), `processed_events`, `outbox_events`
- 내부 조회 API: `GET /internal/security/login-history`, `GET /internal/security/suspicious-events`
- consumer lag 메트릭, DLQ 이관 (3회 재시도)
- trace propagation: Kafka `traceparent` 헤더 → MDC

## Out of Scope

- 비정상 탐지 규칙 (VelocityRule, GeoAnomalyRule 등 — TASK-BE-011)
- 자동 잠금 HTTP 호출 (TASK-BE-011)
- `suspicious_events` 테이블 적재 (탐지 규칙과 함께)
- PII 익명화

---

# Acceptance Criteria

- [ ] `./gradlew :apps:security-service:bootRun` 성공, `/actuator/health` → 200
- [ ] auth-service에서 `auth.login.succeeded` 이벤트 발행 → security-service가 소비 → `login_history` row 생성
- [ ] 동일 `eventId` 2회 수신 → 1회만 처리 (dedupe 확인)
- [ ] 3회 처리 실패 → DLQ(`auth.login.succeeded.dlq`) 이관
- [ ] `login_history` UPDATE/DELETE 시도 → DB 트리거 거부
- [ ] `GET /internal/security/login-history?accountId=xxx` → 200 + 페이지네이션된 이력
- [ ] 응답에서 IP 마스킹(`192.168.1.***`), 이메일 미포함
- [ ] consumer lag 메트릭(`security_consumer_lag`) Prometheus 노출
- [ ] DLQ depth 메트릭 노출
- [ ] Kafka 헤더 `traceparent` → MDC `traceId` 복원 확인

---

# Related Specs

- `specs/services/security-service/architecture.md`
- `specs/services/security-service/overview.md`
- `specs/services/security-service/dependencies.md`
- `specs/services/security-service/redis-keys.md`
- `specs/features/login-history.md`
- `platform/service-types/event-consumer.md`

# Related Skills

- `.claude/skills/service-types/event-consumer-setup/SKILL.md`
- `.claude/skills/messaging/idempotent-consumer/SKILL.md`
- `.claude/skills/messaging/consumer-retry-dlq/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md` (소비)
- `specs/contracts/http/security-query-api.md` (내부 조회)

---

# Target Service

- `apps/security-service`

---

# Architecture

`specs/services/security-service/architecture.md` — Consumer-Driven Layered + narrow read-only query.

---

# Edge Cases

- Kafka 파티션 리밸런싱 중 이벤트 중복 전달 → dedupe로 방어
- Redis dedupe miss + MySQL processed_events에 이미 존재 → skip (이중 방어)
- login_history에 대량 이벤트 적재 시 MySQL bulk insert 최적화

---

# Failure Scenarios

- Kafka 장애 → consumer 중단. 복구 시 last committed offset부터 재개
- MySQL 장애 → 처리 실패 → 재시도 → DLQ. auth-service 로그인은 영향 없음
- Redis 장애 → dedupe 빠른 경로 miss → MySQL fallback (느리지만 정확)
- DLQ 쌓임 → 알림. 운영자 수동 조사

---

# Test Requirements

- Unit: dedupe 로직, 이벤트 → `LoginHistoryEntry` 매핑
- Consumer slice: Spring Kafka Test — 이벤트 produce → consume → DB 확인
- Integration: Testcontainers (Kafka + MySQL + Redis) — E2E 소비 + dedupe + DLQ
- Immutability: `login_history` UPDATE/DELETE → DB 트리거 거부

---

# Definition of Done

- [ ] Implementation completed
- [ ] Consumer 동작 확인 (이벤트 → DB)
- [ ] Dedupe 검증
- [ ] DLQ 이관 검증
- [ ] Append-only 검증
- [ ] Internal query API contract match
- [ ] Tests passing
- [ ] Ready for review
