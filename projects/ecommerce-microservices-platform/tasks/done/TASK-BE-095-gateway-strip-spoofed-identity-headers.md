# Task ID

TASK-BE-095

# Title

gateway-service 외부 클라이언트 X-User-Id / X-User-Email 헤더 스푸핑 방어 — 모든 경로에서 수신 헤더 제거

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

`JwtAuthenticationFilter`에서 외부 클라이언트가 `X-User-Id`, `X-User-Email` 헤더를 직접 보낸 경우 이를 제거하지 않고 다운스트림에 전달하는 보안 취약점을 수정한다.

현재 두 가지 경로에 문제가 있다:

1. **인증 경로**: `request.mutate().header("X-User-Id", userId)`는 기존 헤더에 값을 **추가**한다. 외부 클라이언트가 가짜 `X-User-Id`를 보내면 다운스트림 서비스에 두 값이 전달되어 스푸핑이 가능하다.
2. **공개 경로**: JWT 검증 없이 `chain.filter(exchange)`를 호출하므로 외부 클라이언트가 보낸 `X-User-Id`가 그대로 다운스트림에 전달된다.

모든 요청에서 수신된 `X-User-Id`, `X-User-Email` 헤더를 먼저 제거한 뒤, 인증된 경우에만 게이트웨이가 직접 주입하도록 수정한다.

gateway-service 코드 리뷰에서 발견된 이슈.

---

# Scope

## In Scope

- `JwtAuthenticationFilter.filter()`에서 모든 요청의 `X-User-Id`, `X-User-Email` 헤더를 제거하는 로직 추가
- 인증 경로: 기존 헤더 제거 후 JWT에서 추출한 값으로 설정
- 공개 경로: 기존 헤더 제거 후 헤더 없이 다운스트림 전달
- 관련 단위 테스트 추가

## Out of Scope

- 다운스트림 서비스의 헤더 검증 로직
- 다른 커스텀 헤더 처리
- 공개 경로 목록 변경

---

# Acceptance Criteria

- [ ] 인증 경로: 외부 클라이언트가 `X-User-Id` 헤더를 보내도 JWT의 subject로 덮어쓰기된다
- [ ] 인증 경로: 외부 클라이언트가 `X-User-Email` 헤더를 보내도 JWT의 email claim으로 덮어쓰기된다
- [ ] 공개 경로: 외부 클라이언트가 보낸 `X-User-Id`, `X-User-Email` 헤더가 다운스트림에 전달되지 않는다
- [ ] 정상적인 JWT 인증 흐름에 영향 없음
- [ ] 단위 테스트에서 헤더 스푸핑 시나리오 검증

---

# Related Specs

- `specs/platform/security-rules.md` — "Services must not accept X-User-Id from external clients directly"
- `specs/platform/api-gateway-policy.md` — "On valid JWT: forward the request with the verified X-User-Id and X-User-Email headers"
- `specs/services/gateway-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- 없음 (내부 보안 수정)

---

# Target Service

- `gateway-service`

---

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- `ServerHttpRequest.mutate()`에서 기존 헤더를 제거하려면 `headers(h -> h.remove("X-User-Id"))` 패턴 사용
- 필터 진입 시점에서 일괄 제거하는 것이 가장 안전함 (공개/인증 분기 이전)
- `request.mutate().headers(h -> { h.remove("X-User-Id"); h.remove("X-User-Email"); })` 후, 인증 성공 시 `.header("X-User-Id", userId)` 추가

---

# Edge Cases

- 클라이언트가 `X-User-Id` 헤더를 여러 개 보내는 경우 → `headers.remove()`로 모두 제거됨
- 클라이언트가 대소문자 변형 (`x-user-id`, `X-USER-ID`)으로 보내는 경우 → HTTP 헤더는 case-insensitive이므로 `remove()`로 처리됨
- 공개 경로에서 유효한 JWT를 가진 요청 → 공개 경로이므로 헤더 제거만 하고 JWT 검증하지 않음 (현재 동작 유지)

---

# Failure Scenarios

- 헤더 제거 로직 누락 시 → 외부 클라이언트가 가짜 사용자 ID로 다운스트림 서비스 접근 가능 (권한 상승 취약점)
- 인증 경로에서 제거 후 재설정 누락 시 → 인증된 사용자의 ID가 다운스트림에 전달되지 않음

---

# Test Requirements

- 인증 경로에서 외부 `X-User-Id` 헤더가 JWT subject로 덮어쓰기되는지 검증
- 인증 경로에서 외부 `X-User-Email` 헤더가 JWT email로 덮어쓰기되는지 검증
- 공개 경로에서 외부 `X-User-Id` 헤더가 제거되는지 검증
- 공개 경로에서 외부 `X-User-Email` 헤더가 제거되는지 검증
- 정상 인증 흐름 (외부 스푸핑 헤더 없음) 동작 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
