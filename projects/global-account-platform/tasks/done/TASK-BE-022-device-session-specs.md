# Task ID

TASK-BE-022

# Title

Device/session specs and contracts — `device_sessions` model, list/revoke endpoints, concurrent-session policy

# Status

review

# Owner

backend

# Task Tags

- api
- event
- adr

# depends_on

- (없음)

---

# Goal

auth-service에 device/session 관리 도메인을 추가하기 위한 스펙과 HTTP/이벤트 계약을 확정한다. 구현은 TASK-BE-023에서 수행한다.

---

# Scope

## In Scope

- `specs/services/auth-service/device-session.md` 신규:
  - `device_sessions` 테이블 컬럼(account_id, device_id opaque ID, device_fingerprint, user_agent, ip_last, geo_last, issued_at, last_seen_at, revoked_at)
  - `device_id` 생성 정책: fingerprint + 첫 관측 시점 기반의 opaque ID. fingerprint 자체를 `device_id`로 사용하지 않는다.
  - concurrent-session policy: 계정당 `max_active_sessions`(기본 10), 초과 시 가장 오래된 device eviction 및 연결된 refresh token revoke
  - `refresh_tokens.device_id` → `device_sessions.device_id` 논리적 참조 (기존 `device_fingerprint` 컬럼 역할 대체)
- `specs/contracts/http/auth-api.md` 업데이트:
  - `GET /api/accounts/me/sessions`
  - `GET /api/accounts/me/sessions/current`
  - `DELETE /api/accounts/me/sessions/{deviceId}`
  - `DELETE /api/accounts/me/sessions` (현재 디바이스 제외 일괄 폐기)
- `specs/contracts/events/auth-events.md` 업데이트: `auth.session.created`, `auth.session.revoked` (outbox)

## Out of Scope

- 구현, 마이그레이션, 컨트롤러 코드 (TASK-BE-023)
- DeviceChangeRule 갱신 (TASK-BE-025)

---

# Acceptance Criteria

- [ ] `specs/services/auth-service/device-session.md` 존재, 위 컬럼·정책 모두 기술
- [ ] `specs/contracts/http/auth-api.md`에 4개 엔드포인트 request/response 스키마와 401/403/404 에러 코드 정의
- [ ] `specs/contracts/events/auth-events.md`에 2개 이벤트 페이로드 스키마 정의
- [ ] `device_id` 생성 규칙과 concurrent-session policy가 명세상 모호하지 않음

---

# Related Specs

- `platform/entrypoint.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/data-model.md`

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/auth-service`

---

# Edge Cases

- 동일 fingerprint로 여러 계정이 로그인할 때 `device_id`는 (account_id, fingerprint, first_seen)으로 유니크
- 익명/비어있는 fingerprint 처리 — unknown 디바이스 정책 명시

---

# Failure Scenarios

- concurrent-session eviction 중 refresh token revoke 실패 → 전체 트랜잭션 롤백 (device_session도 생성하지 않음)

---

# Test Requirements

- 스펙 리뷰 — 구현 태스크(TASK-BE-023)의 Related Specs 참조만으로 구현 가능해야 함

---

# Definition of Done

- [ ] Spec files created/updated
- [ ] Contracts updated
- [ ] Ready for implementation task (TASK-BE-023)
