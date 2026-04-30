# Task ID

TASK-BE-016

# Title

Add missing @WebMvcTest slice tests + Testcontainers integration tests for admin-service

# Status

ready

# Owner

backend

# Task Tags

- test

# depends_on

- TASK-BE-015 (fix for TASK-BE-010)

---

# Goal

TASK-BE-015 (BE-010 fix) 리뷰에서 발견된 테스트 커버리지 gap을 메운다.

기존 TASK-BE-010 및 TASK-BE-015의 Acceptance Criteria에 명시된 (a) `@WebMvcTest` slice 테스트, (b) Testcontainers 통합 테스트가 모두 부재. 현재 admin-service 테스트는 unit 테스트만 존재.

---

# Scope

## In Scope

1. **@WebMvcTest slice test** — `apps/admin-service/src/test/java/com/example/admin/presentation/`
   - `AccountAdminControllerTest` — JWT 필터 동작, role 검증 (SUPER_ADMIN / ACCOUNT_ADMIN / AUDITOR), `X-Operator-Reason` 누락 400, `Idempotency-Key` 누락 처리
   - `SessionAdminControllerTest` — 같은 패턴
   - `AuditControllerTest` — AUDITOR 권한 검증

2. **Testcontainers + WireMock 통합 테스트** — `apps/admin-service/src/test/java/com/example/admin/integration/AdminIntegrationTest.java`
   - MySQL + Kafka + WireMock (auth-service, account-service, security-service)
   - 시나리오: lock 명령 → IN_PROGRESS audit row 기록 → downstream 호출 → SUCCESS audit 기록 + outbox `admin.action.performed` 이벤트
   - downstream 실패 시: FAILURE audit + 502 응답 + Resilience4j 재시도 확인
   - `@EnabledIf("isDockerAvailable")` 적용

## Out of Scope

- admin-service 신규 기능
- 분산 트레이싱 통합

---

# Acceptance Criteria

- [ ] `@WebMvcTest` slice 테스트 3개 (Account, Session, Audit 컨트롤러) 통과
- [ ] `AdminIntegrationTest` E2E 시나리오 통과 (Docker 가용 시)
- [ ] Docker 미가용 시 `@EnabledIf`로 skip
- [ ] `./gradlew :apps:admin-service:test -Dorg.gradle.workers.max=1` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/features/admin-operations.md`
- `specs/features/audit-trail.md`
- `platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/http/admin-api.md`
- `specs/contracts/events/admin-events.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- JWT 서명 키 테스트 고정 (test fixture에서 auth-service와 동일 키 페어 사용)
- Idempotency-Key 누락 시 응답 코드 명확히 (`VALIDATION_ERROR` 400)

---

# Failure Scenarios

- Testcontainers가 Docker 없이 실행 → `@EnabledIf` skip

---

# Test Requirements

- Slice: `@WebMvcTest` 3개
- Integration: `@SpringBootTest` + Testcontainers + WireMock 1개

---

# Definition of Done

- [ ] 3 slice + 1 integration 테스트 추가
- [ ] 모든 테스트 통과
- [ ] Ready for review
