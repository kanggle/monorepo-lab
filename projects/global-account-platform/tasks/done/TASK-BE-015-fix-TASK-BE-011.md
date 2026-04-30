# Task ID

TASK-BE-015

# Title

Fix TASK-BE-011 — @Transactional on protected methods, lockRequestResult DB/event inconsistency

# Status

ready

# Owner

backend

# Task Tags

- code

# depends_on

- TASK-BE-011

---

# Goal

TASK-BE-011 리뷰에서 발견된 Warning 이슈들을 수정한다:
1. `DetectSuspiciousActivityUseCase.recordSuspiciousEvent`와 `updateLockResult`가 `protected` + `@Transactional` → Spring AOP 프록시가 self-invocation에서 가로채지 못함 → 트랜잭션 미적용
2. `SuspiciousEvent.lockRequestResult` DB 컬럼 값 (`"INVALID_TRANSITION"`)과 이벤트 payload 값 (`"FAILURE"`)이 불일치
3. `AccountServiceClient.ALREADY_LOCKED` 검출이 `bodyStr.contains("\"previousStatus\":\"LOCKED\"")` 문자열 매칭 — JSON 포맷 변경에 취약
4. 통합 테스트 부재: velocity → AUTO_LOCK → suspicious_events row assertion E2E 없음

---

# Scope

## In Scope

1. **트랜잭션 경계 수정**:
   - `recordSuspiciousEvent`와 `updateLockResult`를 별도 `@Service` 빈 (`SuspiciousEventPersistenceService`)으로 추출
   - `DetectSuspiciousActivityUseCase`가 이 빈을 주입받아 호출 → AOP 프록시 통해 `@Transactional` 적용됨
   - 대안: `DetectSuspiciousActivityUseCase.detect()` 전체를 `@Transactional`로 감싸고 account-service HTTP 호출을 `TransactionSynchronizationManager.registerSynchronization(afterCommit)` 으로 분리

2. **lockRequestResult 일관화**:
   - DB 컬럼과 이벤트 payload 값이 동일하게: `"SUCCESS" | "FAILURE" | "ALREADY_LOCKED"` (contract value) 사용
   - `DetectSuspiciousActivityUseCase.triggerAutoLock`의 `INVALID_TRANSITION` → `FAILURE`로 정규화
   - `SecurityEventPublisher.mapStatus` 제거 또는 간소화

3. **AccountServiceClient JSON 파싱 개선**:
   - `bodyStr.contains(...)` 대신 `JsonNode` 또는 타입화된 record (`LockResponse(String previousStatus, String currentStatus, ...)`)로 역직렬화
   - `previousStatus == "LOCKED"` 명확히 체크

4. **통합 테스트 추가**:
   - `SecurityServiceIntegrationTest` 또는 신규 `DetectionE2EIntegrationTest`에:
     - `auth.login.failed` 10회 전송 → VelocityRule 트리거 → AUTO_LOCK
     - WireMock으로 account-service `/internal/accounts/{id}/lock` 응답 스텁
     - `suspicious_events` row 존재 확인 + `security.auto.lock.triggered` 이벤트 발행 확인
   - `@EnabledIf("isDockerAvailable")` 적용

## Out of Scope

- 탐지 규칙 알고리즘 변경
- MaxMind GeoLite2 DB 번들링

---

# Acceptance Criteria

- [ ] `recordSuspiciousEvent`와 `updateLockResult`가 실제 트랜잭션 내 실행 (self-invocation 회피)
- [ ] `suspicious_events.lockRequestResult` DB 값과 `security.auto.lock.triggered` 이벤트 payload의 `lockRequestResult` 값 일치
- [ ] `AccountServiceClient`가 `previousStatus`를 타입화된 필드로 파싱
- [ ] 통합 테스트: velocity rule trigger → auto-lock → DB row + 이벤트 검증 통과
- [ ] 기존 unit 테스트 모두 통과

---

# Related Specs

- `specs/features/abnormal-login-detection.md`
- `specs/services/security-service/architecture.md`
- `platform/coding-rules.md` — Spring transaction proxy 규칙

# Related Contracts

- `specs/contracts/events/security-events.md`
- `specs/contracts/http/internal/security-to-account.md`

---

# Target Service

- `apps/security-service`

---

# Edge Cases

- `SuspiciousEventPersistenceService`와 `DetectSuspiciousActivityUseCase` 사이의 순환 의존 회피 (인터페이스 기반 주입)
- afterCommit 콜백에서 HTTP 호출 실패 → outbox로 이미 이벤트 발행되었으므로 consumer 재시도 로직 활용

---

# Failure Scenarios

- afterCommit 콜백 실패 → 로그 경고 + 다음 스캐너 사이클에서 재시도 (outbox 패턴)

---

# Test Requirements

- Unit: 트랜잭션 범위 검증 (proxied 호출 확인)
- Integration: velocity → AUTO_LOCK E2E, `@EnabledIf("isDockerAvailable")`

---

# Definition of Done

- [ ] AOP 프록시 이슈 해결
- [ ] DB/event 값 일관성
- [ ] JSON 파싱 타입 안전
- [ ] E2E 통합 테스트 통과
- [ ] Ready for review
