# Task ID

TASK-BE-010

# Title

admin-service 부트스트랩 — 운영자 lock/unlock, 강제 로그아웃, 감사 조회, 감사 원장

# Status

backlog

# Owner

backend

# Task Tags

- code
- api
- event

# depends_on

- TASK-BE-004
- TASK-BE-005
- TASK-BE-008

---

# Goal

admin-service를 운영자 전용 명령 게이트웨이로 초기화한다. 계정 lock/unlock, 강제 로그아웃, 감사 조회(통합 뷰)를 구현하고, 모든 행위를 `admin_actions` append-only 테이블에 기록한다.

---

# Scope

## In Scope

- `apps/admin-service/` 모듈 생성
- 패키지 구조: `presentation / application / infrastructure` ([architecture.md](../../specs/services/admin-service/architecture.md)) — domain 레이어 없음
- Flyway: `admin_actions` (append-only, DB 트리거), `outbox_events`
- 운영자 인증: operator scope JWT 검증 필터 (auth-service 발급 토큰의 `scope: admin` claim)
- 역할 기반 권한: SUPER_ADMIN / ACCOUNT_ADMIN / AUDITOR
- 명령 구현: lock, unlock, force-logout, audit-query
- 내부 HTTP 클라이언트: AccountServiceClient, AuthServiceClient, SecurityServiceClient
- 모든 명령에 Idempotency-Key + X-Operator-Reason 필수
- `admin.action.performed` outbox 이벤트
- fail-closed: `admin_actions` INSERT 실패 → 명령 전체 실패

## Out of Scope

- 운영자 계정 생성/관리 (별도 IdP 또는 auth-service admin scope 발급 경로)
- admin 대시보드 프론트엔드
- bulk 작업 (다수 계정 동시 lock)

---

# Acceptance Criteria

- [ ] `./gradlew :apps:admin-service:bootRun` 성공, `/actuator/health` → 200
- [ ] 비-admin 토큰으로 `/api/admin/*` 접근 → 401 또는 403
- [ ] `POST /api/admin/accounts/{id}/lock` (ACCOUNT_ADMIN) → 200 + audit row + 이벤트
- [ ] `POST /api/admin/accounts/{id}/unlock` → 200 + audit row
- [ ] `POST /api/admin/sessions/{id}/revoke` → 200 + auth-service force-logout 호출 확인
- [ ] `GET /api/admin/audit` (AUDITOR) → 200 + 통합 감사 뷰 (admin_actions + login_history + suspicious_events)
- [ ] 감사 조회 자체가 `admin_actions`에 AUDIT_QUERY로 기록 (meta-audit)
- [ ] reason 누락 → 400 `REASON_REQUIRED`
- [ ] `admin_actions` UPDATE/DELETE → DB 트리거 거부
- [ ] downstream 실패 → audit row outcome=FAILURE + 502 응답
- [ ] `admin_actions` INSERT 실패 → 명령 중단 (fail-closed A10)

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/overview.md`
- `specs/services/admin-service/dependencies.md`
- `specs/features/admin-operations.md`
- `specs/features/audit-trail.md`
- `specs/use-cases/admin-lock-unlock.md` (UC-11~14)

# Related Contracts

- `specs/contracts/http/admin-api.md`
- `specs/contracts/http/internal/admin-to-auth.md`
- `specs/contracts/http/internal/admin-to-account.md`
- `specs/contracts/events/admin-events.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 이미 LOCKED 계정 lock → 멱등 200 (previousStatus=LOCKED)
- downstream 타임아웃 + retry 후 성공 → audit에 최종 SUCCESS 기록
- 동일 Idempotency-Key 재요청 → downstream 멱등 응답

---

# Failure Scenarios

- account-service 장애 → circuit open → 502 + audit FAILURE
- auth-service 장애 → force-logout 불가 → 502 + audit FAILURE
- MySQL 장애 → audit 기록 불가 → **명령 자체 차단** (fail-closed)
- security-service 조회 실패 → 감사 조회 부분 응답 (admin_actions만)

---

# Test Requirements

- Unit: command orchestration, role-based access
- Slice: `@WebMvcTest` — operator auth filter, role 검증
- Integration: Testcontainers (MySQL + Kafka) + WireMock (auth/account/security) — lock → audit row → 이벤트 E2E
- Security: 비-admin token 접근 → 403
- Audit immutability: UPDATE/DELETE 시도 → 거부

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts match
- [ ] fail-closed verified
- [ ] Ready for review
