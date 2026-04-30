# Task ID

TASK-BE-033

# Title

admin-service — 다운스트림 호출 Resilience4j CircuitBreaker 성숙화

# Status

backlog

# Owner

backend

# Task Tags

- code
- test

# depends_on

- (없음)

---

# Goal

admin-service가 account-service, security-service 내부 API를 호출하는 경로에 Resilience4j CircuitBreaker를 부착하고, fallback 정책(fail-closed for mutations, fail-open for reads)을 명시한다.

---

# Scope

## In Scope

- admin-service의 HTTP client (`AccountServiceClient`, `SecurityServiceClient` 등) 전수 조사
- Resilience4j CB + retry + timeout 데코레이터 적용
- Mutation 경로(lock/unlock/force-logout): CB open 시 fail-closed → 503 반환 + `admin_actions.outcome=FAILURE` 기록
- Read 경로(audit query): CB open 시 fail-fast → 503 (stale 데이터 반환 금지)
- `/actuator/health/circuitbreakers` 노출
- CB state 전이 이벤트 로깅 (DEBUG)

## Out of Scope

- 글로벌 서비스 메시 CB (Istio 등)
- account-service/security-service 쪽 CB (각 서비스 담당)

---

# Acceptance Criteria

- [ ] 다운스트림 503 반복 주입 → CB OPEN, 후속 호출 즉시 503, `admin_actions` FAILURE 기록
- [ ] CB HALF_OPEN 이후 정상 응답 → CLOSED 복귀
- [ ] `/actuator/health/circuitbreakers`에 각 CB 상태 노출

---

# Related Specs

- `specs/services/admin-service/architecture.md`

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- CB와 retry 상호작용: retry 완료 후 CB 카운터 갱신

---

# Failure Scenarios

- 네트워크 지연 vs 서버 오류 구분 — 슬로우콜 비율도 OPEN 트리거

---

# Test Requirements

- Integration: WireMock + CB OPEN 전이 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Ready for review
