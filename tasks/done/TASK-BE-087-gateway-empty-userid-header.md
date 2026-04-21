# Task ID

TASK-BE-087

# Title

gateway-service 빈 X-User-Id 헤더 차단 — JWT에서 userId 추출 실패 시 401 반환

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

gateway-service의 `JwtAuthenticationFilter`에서 JWT 토큰 파싱 후 userId가 null이거나 빈 문자열인 경우 빈 `X-User-Id` 헤더를 downstream 서비스로 전달하는 문제를 수정한다.

현재 코드: `header("X-User-Id", userId != null ? userId : "")` — userId가 null이면 빈 문자열로 전달되어 downstream 서비스에서 인증되지 않은 요청이 통과할 수 있다.

수정 후: userId가 null이거나 빈 문자열이면 401 Unauthorized를 반환하고 요청을 차단한다.

---

# Scope

## In Scope

- `JwtAuthenticationFilter`에서 userId 빈 값 검증 추가
- userId 추출 실패 시 401 응답 반환
- X-User-Role 헤더도 동일하게 빈 값 검증
- 단위 테스트 추가

## Out of Scope

- downstream 서비스의 X-User-Id 검증 로직 변경
- JWT 서명 알고리즘 변경
- 공개 경로(public paths)의 헤더 처리

---

# Acceptance Criteria

- [ ] JWT에서 userId가 null 또는 빈 문자열로 파싱되면 401 Unauthorized를 반환한다
- [ ] userId가 정상적으로 추출되면 기존과 동일하게 X-User-Id 헤더를 전달한다
- [ ] 공개 경로(인증 불필요 경로)는 영향을 받지 않는다
- [ ] 단위 테스트가 빈 userId 시나리오를 검증한다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/platform/api-gateway-policy.md`
- `specs/services/gateway-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/gateway-api.md`

---

# Target Service

- `gateway-service`

---

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- `JwtAuthenticationFilter`의 헤더 주입 로직에서 userId/role 빈 값 체크 추가
- 빈 값인 경우 `ServerWebExchange`에 401 응답 설정 후 필터 체인 중단
- 기존 공개 경로 로직과 충돌하지 않도록 조건 확인 필요

---

# Edge Cases

- JWT가 유효하지만 userId claim이 없는 경우
- JWT payload에 userId가 빈 문자열인 경우
- 공개 경로에 JWT가 포함된 경우 (무시해야 함)

---

# Failure Scenarios

- userId 추출 실패 시 401 반환 확인
- 잘못된 claim 형식 (숫자 등) 시 처리

---

# Test Requirements

- userId null/빈 문자열 시 401 반환 단위 테스트
- 정상 userId 시 헤더 전달 확인 테스트
- 공개 경로 영향 없음 확인 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
