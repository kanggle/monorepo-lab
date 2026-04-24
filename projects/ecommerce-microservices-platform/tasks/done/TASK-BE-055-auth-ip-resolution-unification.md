# Task ID

TASK-BE-055

# Title

auth-service 클라이언트 IP 해석 로직 통일 — Rate Limit 우회 방지

# Status

done

# Owner

backend

# Task Tags

- code
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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

LoginRateLimitFilter와 AuthController에서 서로 다른 IP 해석 로직을 사용하는 문제를 수정한다.

현재 상태:
- LoginRateLimitFilter: `request.getRemoteAddr()` 사용
- AuthController.resolveClientIp(): `X-Forwarded-For` 헤더 파싱

이로 인해 프록시 뒤에서 Rate Limit이 우회될 수 있다.

---

# Scope

## In Scope

- 공통 IP 해석 유틸리티 또는 컴포넌트 생성 (`ClientIpResolver`)
- LoginRateLimitFilter와 AuthController에서 동일 로직 사용
- AuditLog에 기록되는 IP와 Rate Limit 대상 IP가 동일함을 보장

## Out of Scope

- X-Forwarded-For 스푸핑 방지 (gateway-service 레벨에서 처리)
- Rate Limit 임계값 변경

---

# Acceptance Criteria

- [ ] `ClientIpResolver` 컴포넌트가 생성되어 단일 IP 해석 로직을 제공한다
- [ ] LoginRateLimitFilter가 ClientIpResolver를 사용한다
- [ ] AuthController가 ClientIpResolver를 사용한다
- [ ] X-Forwarded-For 헤더가 있을 때와 없을 때 모두 동일 결과를 반환한다
- [ ] 기존 Rate Limit 테스트가 통과한다

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md`

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

---

# Implementation Notes

- `ClientIpResolver`는 infrastructure 레이어에 배치
- X-Forwarded-For 파싱 시 첫 번째 IP를 사용 (가장 가까운 클라이언트)
- X-Forwarded-For가 없으면 `request.getRemoteAddr()` fallback
- Spring Bean으로 등록하여 Filter와 Controller 모두에서 주입 가능하게 구성

---

# Edge Cases

- X-Forwarded-For에 여러 IP가 콤마로 구분된 경우
- X-Forwarded-For가 빈 문자열인 경우
- IPv6 주소가 포함된 경우
- 프록시 없이 직접 접근하는 경우

---

# Failure Scenarios

- X-Forwarded-For 헤더가 악의적으로 조작된 경우 — 첫 번째 IP만 사용
- ClientIpResolver 빈 생성 실패 시 애플리케이션 기동 실패 (fail-fast)

---

# Test Requirements

- 단위 테스트: X-Forwarded-For 파싱 (단일 IP, 다중 IP, 빈 값, null)
- 통합 테스트: Rate Limit 필터와 Audit Log에 동일 IP가 기록되는지 검증
- 기존 Rate Limit 테스트 회귀 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
