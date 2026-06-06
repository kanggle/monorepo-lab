# Task ID

TASK-BE-026

# Title

auth-service — device/session 통합 테스트 (@SpringBootTest + Testcontainers)

# Status

backlog

# Owner

backend

# Task Tags

- test

# depends_on

- TASK-BE-023
- TASK-BE-024

---

# Goal

device/session 전체 플로우를 엔드투엔드로 검증한다.

---

# Scope

## In Scope

- `DeviceSessionIntegrationTest` (`@SpringBootTest` + MySQL + Kafka Testcontainers):
  - 서로 다른 user-agent로 2회 로그인 → `GET /api/accounts/me/sessions` 2 entries
  - 동일 fingerprint 재로그인 → device_id 재사용, last_seen_at 갱신
  - 11번째 디바이스 로그인 → 가장 오래된 session revoked, 해당 refresh token revoked, outbox `auth.session.revoked` 기록
  - `DELETE /api/accounts/me/sessions/{id}` → 연결된 refresh token 다음 `/refresh` 호출에서 401
  - `DELETE /api/accounts/me/sessions` (bulk) → 현재 디바이스 제외 나머지 모두 revoked
- outbox 이벤트 검증 (publisher까지 갔는지)

## Out of Scope

- UI 테스트
- 성능/부하 테스트

---

# Acceptance Criteria

- [ ] 위 5개 시나리오 전부 PASS
- [ ] Testcontainers 환경에서 재현 가능
- [ ] `./gradlew :apps:auth-service:integrationTest` (또는 기존 integration task) 성공

---

# Related Specs

- `specs/services/auth-service/device-session.md`

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/auth-service`

---

# Edge Cases

- Testcontainers 포트 할당 — 기존 패턴(WireMock 동적 포트) 따름

---

# Failure Scenarios

- Kafka 컨테이너 지연 시 outbox assertion timing — Awaitility 사용

---

# Test Requirements

- Integration: 위 명시 시나리오

---

# Definition of Done

- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
