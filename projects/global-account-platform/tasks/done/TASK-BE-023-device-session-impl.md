# Task ID

TASK-BE-023

# Title

auth-service — device_session 도메인·리포지토리·use-case·컨트롤러 구현

# Status

backlog

# Owner

backend

# Task Tags

- code
- api
- event
- db

# depends_on

- TASK-BE-022

---

# Goal

TASK-BE-022에서 확정된 스펙대로 auth-service에 device/session 관리 기능을 구현한다. 로그인·리프레시 플로우가 device_session을 등록/갱신하고, 사용자는 자신의 세션을 조회·폐기할 수 있다.

---

# Scope

## In Scope

- Flyway 마이그레이션: `device_sessions` 테이블 + 인덱스 (account_id, last_seen_at DESC)
- 도메인: `DeviceSession` aggregate (POJO), `DeviceSessionRepository` 포트
- 인프라: `DeviceSessionJpaEntity`, `DeviceSessionRepositoryAdapter`, `DeviceSessionJpaRepository`
- 애플리케이션:
  - `RegisterOrUpdateDeviceSessionUseCase` — 로그인/리프레시 경로에서 호출. 기존 session 있으면 `last_seen_at` 갱신, 없으면 신규 생성 후 concurrent-session policy 적용
  - `EnforceConcurrentLimitUseCase` — `max_active_sessions` 초과 시 가장 오래된 세션 revoke + 해당 refresh token revoke
  - `ListSessionsUseCase`, `GetCurrentSessionUseCase`
  - `RevokeSessionUseCase` (단일), `RevokeAllOtherSessionsUseCase`
- 프레젠테이션: `AccountSessionController` — 4개 엔드포인트 (GET list/current, DELETE single/bulk)
- 기존 `LoginUseCase`, `RefreshTokenUseCase`에서 `RegisterOrUpdateDeviceSessionUseCase` 호출 통합. **`SessionContext`(domain/session/SessionContext.java)를 그대로 활용**하고 fingerprint 추출 로직 중복 구현하지 않을 것
- Outbox 이벤트 발행: `auth.session.created`, `auth.session.revoked`

## Out of Scope

- `refresh_tokens.device_fingerprint` 컬럼 삭제/마이그레이션 (TASK-BE-024에서 처리)
- DeviceChangeRule 변경 (TASK-BE-025)
- 프론트엔드 UI

---

# Acceptance Criteria

- [ ] Flyway 마이그레이션 실행 시 `device_sessions` 테이블 생성
- [ ] 로그인 시 새 디바이스면 row 생성, 기존이면 `last_seen_at` 업데이트
- [ ] 11번째 디바이스 로그인 시 가장 오래된 디바이스가 revoke되고 해당 refresh token도 revoke
- [ ] `GET /api/accounts/me/sessions` — 활성 세션만 반환 (revoked 제외)
- [ ] `DELETE /api/accounts/me/sessions/{deviceId}` — 해당 디바이스 세션·refresh token 동시 revoke, 멱등
- [ ] `auth.session.created` / `auth.session.revoked` outbox row 생성 확인
- [ ] `./gradlew :apps:auth-service:test` 통과

---

# Related Specs

- `specs/services/auth-service/device-session.md`
- `specs/services/auth-service/architecture.md`

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/auth-service`

---

# Edge Cases

- concurrent-session eviction이 같은 트랜잭션에서 실패하면 로그인 자체를 실패시킬지/계속할지 — 스펙의 실패 정책 따름
- `device_id` 생성 타이밍: fingerprint 첫 관측 시에만 생성, 이후 재사용

---

# Failure Scenarios

- DB unique 위반(동시 로그인 경합) → 재시도 1회 후 실패 시 500

---

# Test Requirements

- Unit: `DeviceSession` 도메인, `EnforceConcurrentLimitUseCase`
- Slice: `AccountSessionController` `@WebMvcTest`
- Repository: `@DataJpaTest` + Testcontainers — unique constraint, last_seen_at 정렬
- Integration: 별도 태스크(TASK-BE-026)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Outbox 이벤트 검증
- [ ] Ready for review
