# Task ID

TASK-BE-013

# Title

security-service 리뷰 수정 — 멱등성 트랜잭션 경계, 아키텍처 위반, DB 테이블명, 메트릭, 인증, 테스트 보완

# Status

ready

# Owner

backend

# Task Tags

- code
- event

# depends_on

- TASK-BE-008

---

# Goal

TASK-BE-008 리뷰에서 발견된 Critical·Warning 이슈를 수정한다.

---

# Scope

## In Scope

1. **[Critical] 멱등성 트랜잭션 경계 수정** (`AbstractAuthEventConsumer` + `RecordLoginHistoryUseCase` + `EventDedupService`):
   - `isDuplicate()` 체크, 비즈니스 처리, `markProcessed()` 를 하나의 `@Transactional` 범위 안에서 원자적으로 실행하도록 재설계
   - 동시 중복 이벤트 수신 시 `login_history` 중복 row가 생성되지 않음을 보장

2. **[Critical] `query/` → `consumer/` 패키지 의존성 제거** (`SecurityQueryService`):
   - `AuthEventMapper.maskIp()` / `truncateFingerprint()` 호출을 `consumer/` 패키지에서 제거
   - 마스킹/truncate 로직을 `domain/` 또는 `query/` 내 유틸리티로 이동
   - `architecture.md` Allowed Dependencies 준수

3. **[Critical] Flyway 마이그레이션 테이블명 수정** (`V0004__create_outbox_events.sql`):
   - 테이블명을 `outbox` → `outbox_events` 로 수정 (spec 일치)

4. **[Critical] `security_dlq_depth` 메트릭 구현** (`SecurityMetricsConfig`):
   - Kafka `AdminClient`를 사용하여 실제 DLQ 토픽 offset 기반 depth를 계산
   - placeholder `return 0` 제거, 실제 값 반환

5. **[Critical] 표준 에러 응답 형식 적용** (`LoginHistoryQueryController`):
   - `MissingServletRequestParameterException` 에 대한 `@ExceptionHandler` 또는 `@ControllerAdvice` 추가
   - `security-query-api.md` Common Error Format (`{"code": "VALIDATION_ERROR", "message": "...", "timestamp": "..."}`) 준수

6. **[Warning] OTel trace propagation 교체** (`AbstractAuthEventConsumer`):
   - 수동 MDC 주입 대신 `libs/java-observability` KafkaPropagator 사용
   - producer span 에 linked span 생성

7. **[Warning] 내부 query 엔드포인트 인증 추가** (`LoginHistoryQueryController`, `SuspiciousEventQueryController`):
   - `libs/java-security` 활용 또는 service token 헤더 검증 필터 추가
   - `security-query-api.md` Auth 요구사항 충족

8. **[Warning] DLQ 라우팅 테스트 추가** (`SecurityServiceIntegrationTest` 또는 별도 테스트):
   - 3회 처리 실패 시 이벤트가 `auth.login.succeeded.dlq` 에 도달함을 검증

9. **[Warning] login_history 불변성 테스트 추가**:
   - `login_history` UPDATE/DELETE 시도 → DB 트리거 거부 검증 테스트

## Out of Scope

- 비정상 탐지 규칙 (TASK-BE-011 범위)
- Flyway delimiter 방식 변경 (V0002 트리거 마이그레이션) — 별도 검토 필요

---

# Acceptance Criteria

- [ ] 동일 `eventId` 2회 동시 수신 → `login_history` 1 row만 생성 (트랜잭션 원자성 검증)
- [ ] `SecurityQueryService` 가 `consumer/` 패키지를 import하지 않음
- [ ] `outbox_events` 테이블이 정상 생성됨 (Flyway 마이그레이션 성공)
- [ ] `security_dlq_depth` Prometheus endpoint에서 실제 값 반환 (0이 아닌 경우 비율 포함)
- [ ] `GET /internal/security/login-history` 에서 `accountId` 누락 시 `{"code": "VALIDATION_ERROR", ...}` 400 응답
- [ ] Kafka traceparent 헤더 → OTel span 연계 검증 (MDC traceId 포함)
- [ ] 인증 없는 query 엔드포인트 호출 시 403 반환
- [ ] 3회 실패 이벤트 → `.dlq` 토픽 도달 테스트 통과
- [ ] `login_history` UPDATE 시도 → 트리거 거부 테스트 통과
- [ ] `./gradlew :apps:security-service:test` 통과

---

# Related Specs

- `specs/services/security-service/architecture.md`
- `specs/services/security-service/dependencies.md`
- `specs/contracts/http/security-query-api.md`
- `platform/service-types/event-consumer.md`
- `platform/event-driven-policy.md`
- `platform/coding-rules.md`

# Related Contracts

- `specs/contracts/events/auth-events.md`
- `specs/contracts/http/security-query-api.md`

---

# Target Service

- `apps/security-service`

---

# Architecture

`specs/services/security-service/architecture.md` — Consumer-Driven Layered + narrow read-only query.

---

# Edge Cases

- Redis 장애 시에도 MySQL `processed_events` upsert로 멱등성 보장
- DLQ AdminClient 조회 실패 시 metric 0 반환 + warn 로그 (graceful degradation)
- 인증 실패 시 PII 포함 응답 금지

---

# Failure Scenarios

- `processed_events` unique constraint 충돌 → duplicate event skip (정상 동작)
- AdminClient Kafka 연결 불가 → DLQ depth metric 0 반환, 알림 별도

---

# Test Requirements

- Unit: 트랜잭션 경계 내 dedup + save 원자성 검증
- Integration: DLQ 라우팅 (3회 실패 → `.dlq`), login_history 불변성 (UPDATE/DELETE 트리거 거부)
- Slice: query controller 400 에러 응답 형식, 403 인증 거부

---

# Definition of Done

- [ ] 모든 Critical 이슈 수정 완료
- [ ] 모든 Warning 이슈 수정 완료
- [ ] Tests passing
- [ ] Ready for review
