# Task ID

TASK-BE-004

# Title

account-service 부트스트랩 — 회원가입, 프로필 CRUD, 계정 상태 기계, Flyway 마이그레이션

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event

# depends_on

- TASK-BE-002
- TASK-BE-003

---

# Goal

account-service를 실행 가능한 Spring Boot 애플리케이션으로 초기화하고, 회원가입(`POST /api/accounts/signup`), 프로필 조회(`GET /api/accounts/me`), 프로필 수정(`PATCH /api/accounts/me/profile`), 계정 상태 조회(`GET /api/accounts/me/status`)의 최소 골든패스를 구현한다. `AccountStatusMachine` 도메인 객체를 통해 상태 전이를 관리하고, 내부 HTTP 엔드포인트(`/internal/accounts/*`)를 credential lookup 및 잠금 명령용으로 제공한다.

---

# Scope

## In Scope

- `apps/account-service/` 모듈 생성 (`settings.gradle` include 추가)
- 패키지 구조: `presentation / application / domain / infrastructure` ([architecture.md](../../specs/services/account-service/architecture.md))
- Flyway 마이그레이션: `accounts`, `profiles`, `account_status_history`, `outbox_events` 테이블
- 공개 API: signup, GET me, PATCH profile, GET status, DELETE me (유예 진입)
- 내부 API: credential lookup (`GET /internal/accounts/credentials`), 상태 조회 (`GET /internal/accounts/{id}/status`), 잠금 (`POST /internal/accounts/{id}/lock`), 해제 (`POST /internal/accounts/{id}/unlock`)
- `AccountStatusMachine` 도메인 객체 (전이 규칙 정의)
- `account_status_history` append-only 기록 (DB 트리거 포함)
- Outbox 이벤트: `account.created`, `account.status.changed`, `account.locked`, `account.unlocked`, `account.deleted`
- 단위 + slice + 통합 테스트

## Out of Scope

- 이메일 검증 (백로그)
- PII 익명화 배치 (백로그)
- 실제 이메일/SMS 발송 (mock)
- 프론트엔드

---

# Acceptance Criteria

- [ ] `./gradlew :apps:account-service:bootRun` 성공, `/actuator/health` → 200
- [ ] `POST /api/accounts/signup` → 201 + `account.created` outbox 이벤트 기록
- [ ] 중복 이메일 가입 → 409 `ACCOUNT_ALREADY_EXISTS`
- [ ] `GET /api/accounts/me` → 200 + 프로필 (phoneNumber 마스킹됨)
- [ ] `PATCH /api/accounts/me/profile` → 200 + 변경 반영
- [ ] `GET /api/accounts/me/status` → 200 + 현재 상태
- [ ] `DELETE /api/accounts/me` → 202 + 상태 DELETED + `account.deleted` 이벤트
- [ ] `POST /internal/accounts/{id}/lock` → ACTIVE→LOCKED + `account_status_history` row + `account.locked` 이벤트
- [ ] 불허 전이 (DELETED→LOCKED) → 400 `STATE_TRANSITION_INVALID`
- [ ] `account_status_history` UPDATE/DELETE 시도 → DB 트리거로 거부
- [ ] Flyway 마이그레이션 정상 실행
- [ ] `AccountStatusMachine` 단위 테스트: 모든 허용 전이 성공, 모든 금지 전이 예외
- [ ] Controller slice 테스트: 공개 + 내부 DTO validation

---

# Related Specs

- `specs/services/account-service/architecture.md`
- `specs/services/account-service/overview.md`
- `specs/services/account-service/dependencies.md`
- `specs/services/account-service/data-model.md`
- `specs/features/signup.md`
- `specs/features/account-lifecycle.md`
- `specs/use-cases/signup-and-login.md` (UC-1)

# Related Skills

- `.claude/skills/service-types/rest-api-setup/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/validation/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/account-api.md`
- `specs/contracts/http/internal/auth-to-account.md`
- `specs/contracts/http/internal/security-to-account.md`
- `specs/contracts/http/internal/admin-to-account.md`
- `specs/contracts/events/account-events.md`

---

# Target Service

- `apps/account-service`

---

# Architecture

`specs/services/account-service/architecture.md` — Layered + 명시적 상태 기계.

---

# Edge Cases

- 동시 가입 요청 race → DB unique constraint (email) → 409
- 상태 기계 동시 전이 → 낙관적 락 (version) → 409 CONFLICT
- Flyway 마이그레이션 실패 → 앱 기동 중단 (fail-fast)
- 내부 API에 외부 요청 도달 → Spring Security 또는 URL 필터로 차단

---

# Failure Scenarios

- MySQL 미기동 → 앱 기동 실패 (Spring Boot datasource check)
- Redis 미기동 → 가입 dedup 불가, MySQL unique로 fallback
- outbox relay 미동작 → 이벤트 발행 지연 (로그인은 정상)

---

# Test Requirements

- Unit: `AccountStatusMachine` 전이 규칙 전체, `Email` 값 객체 검증
- Slice: `@WebMvcTest` — SignupController, ProfileController, AccountStatusController, InternalControllers
- Integration: `@SpringBootTest` + Testcontainers (MySQL) — 가입 → 상태 변경 → 이력 확인 E2E

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts match (account-api.md, internal contracts)
- [ ] Flyway migration applied
- [ ] `account_status_history` immutability verified
- [ ] Ready for review
