# Task ID

TASK-BE-025

# Title

security-service — DeviceChangeRule을 device_id 기반으로 전환

# Status

backlog

# Owner

backend

# Task Tags

- code
- test

# depends_on

- TASK-BE-023

---

# Goal

현재 `DeviceChangeRule`은 fingerprint 비교로 '새 디바이스' 판정을 내리기 때문에, 브라우저 업데이트 등으로 fingerprint가 달라지면 등록된 디바이스에서도 ALERT가 뜬다. TASK-BE-023의 `device_id` 도입 이후에는 auth-service의 device_sessions 등록 여부를 기준으로 판정한다.

---

# Scope

## In Scope

- `DeviceChangeRule` 갱신: login event에서 fingerprint 대신 `device_id` (또는 device_id 해석 실패 시 fingerprint fallback) 사용
- security-service가 auth-service의 device_session 존재 여부를 질의할 경로:
  - Option A: login event payload에 `device_id`와 `is_new_device` boolean을 auth-service가 실어서 발행 (Recommended — 추가 동기 호출 없음)
  - Option B: security-service가 internal API로 조회
- 스펙상 선택지 중 A를 채택. `auth.login.succeeded` 이벤트 스키마에 `device_id`, `is_new_device` 필드 추가 (spec 갱신 필요)

## Out of Scope

- 다른 rule 변경
- ML 기반 디바이스 스코어링

---

# Acceptance Criteria

- [ ] 동일 `device_id`로 재로그인 → ALERT 미발행
- [ ] `is_new_device=true`인 로그인만 ALERT
- [ ] fingerprint 기반 기존 테스트 케이스는 fallback 경로로 유지
- [ ] `specs/contracts/events/auth-events.md` 갱신

---

# Related Specs

- `specs/services/security-service/architecture.md`
- `specs/services/auth-service/device-session.md`

# Related Contracts

- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/security-service` (+ `apps/auth-service` payload 확장)

---

# Edge Cases

- event payload에 `device_id`가 null (레거시 event) → fingerprint fallback

---

# Failure Scenarios

- 이벤트 스키마 하위호환 위반 — consumer가 unknown field tolerant하게 파싱하도록 유지

---

# Test Requirements

- Unit: `DeviceChangeRuleTest` — is_new_device true/false 분기
- Contract: 이벤트 payload 스키마 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Event contract updated
- [ ] Ready for review
