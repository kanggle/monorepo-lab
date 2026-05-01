# Task ID

TASK-BE-256

# Title

테넌트 onboarding API 계약 완성 — `POST/PATCH /api/admin/tenants` + audit + outbox 이벤트

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`specs/features/multi-tenancy.md` 라인 55에 "테넌트는 운영자가 admin-service 의 `POST /api/admin/tenants` 로 등록" 으로 선언되어 있으나, 실제 contract (`admin-api.md`) 에 요청/응답 스키마, 오류 코드, audit 기록 동작이 완성되지 않았다 (TASK-BE-250 가 이를 구현하기 전 단계 contract 부재).

본 태스크는 TASK-BE-250 (admin-service tenant management API) 의 **선행 contract 완성** 으로 위치한다. 계약 + audit + outbox 이벤트만 정의하고, 실제 admin-service 구현은 TASK-BE-250 에서 수행.

완료 시점:

1. `specs/contracts/http/admin-api.md` 에 다음 4개 endpoint 완성:
   - `POST /api/admin/tenants` (생성)
   - `GET /api/admin/tenants` (목록)
   - `GET /api/admin/tenants/{tenantId}` (단건)
   - `PATCH /api/admin/tenants/{tenantId}` (display_name, status 변경)
2. 각 endpoint 의 요청·응답 스키마, 오류 코드, audit 기록 (`admin_actions.action_code`), outbox 이벤트 발행 명시.
3. 신규 outbox 이벤트 contract 추가:
   - `tenant.created`
   - `tenant.suspended`
   - `tenant.reactivated`
   - `tenant.updated` (display_name 변경)
4. 본 태스크 머지 후 TASK-BE-250 가 본 contract 를 구현 대상으로 사용.

---

# Scope

## In Scope

- `specs/contracts/http/admin-api.md` 에 tenant management 섹션 신규 추가:
  - 4개 endpoint 의 메서드, 경로, 요청 body, 응답 body, 오류 코드 표
  - 인증: SUPER_ADMIN 권한 필수
  - rate limit: tenant 등록은 분당 5회 (운영자 단위)
- audit 기록 명시:
  - `admin_actions.action_code = TENANT_CREATE | TENANT_SUSPEND | TENANT_REACTIVATE | TENANT_UPDATE`
  - `admin_actions.target_id = tenant_id`
- outbox 이벤트 contract 신규 작성:
  - `specs/contracts/events/admin-events.md` 에 4개 신규 이벤트 추가 (또는 신규 파일 `tenant-events.md` — 기존 패턴 따라 결정)
- 입력 검증:
  - `tenant_id` 정규식 `^[a-z][a-z0-9-]{1,31}$` (이미 multi-tenancy.md 에 선언됨)
  - `tenant_id` 예약어 금지 (`admin`, `internal`, `system`, `null`, `default` 등) — 명시 필요
  - 중복 `tenant_id` → 409 `TENANT_ALREADY_EXISTS`
  - `display_name` 필수, 1~100자
  - `tenant_type` enum 검증
- 상태 전이 매트릭스:
  - 생성 시 status=ACTIVE 만 허용
  - PATCH `status=SUSPENDED` 가능 (ACTIVE → SUSPENDED)
  - PATCH `status=ACTIVE` 가능 (SUSPENDED → ACTIVE = reactivate)
  - DELETE 미지원 (테넌트는 삭제 불가, suspend 만)

## Out of Scope

- admin-service 의 실제 endpoint 구현 — TASK-BE-250 (이미 ready/ 에 있음)
- account-service 의 tenant entity 영속 구현 — admin → account 호출 흐름은 TASK-BE-250 에서 조립
- tenant 단위 제한 (e.g. 최대 사용자 수, 일일 로그인 quota) — 후속 ADR
- B2B 계약 메타 (계약일, 결제 주기 등) — 본 contract 범위 외
- tenant deletion (감사·외부 토큰 정합으로 인해 영구 보존)

---

# Acceptance Criteria

- [ ] `admin-api.md` 에 tenant management 섹션 추가 (4개 endpoint, 요청·응답·오류·audit·이벤트 모두 명시).
- [ ] 4개 outbox 이벤트 contract (`tenant.created` 등) 가 events 디렉터리에 추가 (신규 파일 또는 기존 admin-events.md 확장).
- [ ] 모든 endpoint 의 SUPER_ADMIN 권한 요구가 명시.
- [ ] `tenant_id` 예약어 목록이 contract 에 명시.
- [ ] 상태 전이 매트릭스가 contract 에 표 형태로 명시.
- [ ] TASK-BE-250 의 Goal/Scope 가 본 contract 를 참조하도록 갱신 (TASK-BE-250 task 파일 수정 — 본 태스크의 PR 에 포함).

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification.

- `specs/features/multi-tenancy.md` § "테넌트 등록 방식"
- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/security.md`
- `specs/contracts/http/admin-api.md` (확장 대상)
- `specs/contracts/events/admin-events.md` (확장 대상 또는 분리)
- `tasks/ready/TASK-BE-250-admin-service-tenant-management-api.md` (본 태스크의 후속 구현)

# Related Skills

- `.claude/skills/backend/` audit-trail / event-driven 관련

---

# Related Contracts

- `specs/contracts/http/admin-api.md`
- `specs/contracts/events/admin-events.md` (또는 신규 `tenant-events.md`)

---

# Target Service

- `admin-service` (계약 정의), `account-service` (이벤트 소비자 — `tenant.suspended` 시 해당 tenant 신규 로그인 차단)

---

# Architecture

- 본 태스크는 contract 작성. architecture.md 변경 없음.
- 단, `account-service/architecture.md` 에 "신규 tenant 이벤트 소비자: tenant.suspended 시 신규 로그인 차단" 한 줄 추가 권장.

---

# Implementation Notes

- **이벤트 파일 분리 vs. 기존 admin-events.md 확장**: 현재 `admin-events.md` 가 admin 운영 명령 이벤트만 담고 있다면 tenant 이벤트는 별도 파일 (`tenant-events.md`) 이 의미적으로 정합. 기존 `admin-events.md` 의 내용을 확인 후 결정.
- **`tenant_id` 예약어**: `admin`, `internal`, `system`, `null`, `default`, `public`, `gap`, `auth` 등 8개 정도 명시.
- **상태 전이 매트릭스**:
  ```
  current  → ACTIVE  → SUSPENDED
  -        → 생성    → (불가)
  ACTIVE   → no-op   → 가능
  SUSPENDED → reactivate → no-op
  ```
- **Audit 기록 정합**: TASK-BE-249 가 admin-service 에 `tenant_id` 컬럼을 추가했으므로, `TENANT_*` action 의 audit row 의 `tenant_id` 는 대상 tenant_id 로 채움.

---

# Edge Cases

- **운영자가 SUSPENDED tenant 의 신규 사용자 등록 시도**: 본 contract 범위 외 (account-internal-provisioning 의 별도 검증). 단, contract 에 "SUSPENDED tenant 는 신규 가입·로그인 차단" 명시.
- **동일 tenant_id 동시 생성 요청**: PK unique constraint 로 두 번째 요청 409.
- **이미 SUSPENDED tenant 에 SUSPEND PATCH**: 멱등 — 200, 이벤트 발행 안 함.
- **존재하지 않는 tenant 조회/수정**: 404.

---

# Failure Scenarios

- **TASK-BE-250 가 본 contract 와 다르게 구현됨**: PR 리뷰 시 cross-check. 본 태스크의 Definition of Done 에 "TASK-BE-250 의 Acceptance Criteria 가 본 contract 의 모든 endpoint 를 커버" 항목 추가.
- **이벤트 schema bump 누락**: 기존 admin-events.md 에 같은 이름의 이벤트가 있다면 버전 충돌. 사전 grep 으로 확인.

---

# Test Requirements

- 본 태스크는 contract 작성. 테스트는 TASK-BE-250 구현 시 추가.
- 단, contract 의 모든 example payload 가 JSON schema validator 로 self-consistent 한지 수동 검증 권장.

---

# Definition of Done

- [ ] `admin-api.md` 갱신 완료
- [ ] 4개 outbox 이벤트 contract 작성 완료
- [ ] TASK-BE-250 task 파일이 본 contract 참조하도록 갱신
- [ ] `multi-tenancy.md` § "테넌트 등록 방식" 가 본 contract 로 backlink
- [ ] Ready for review (구현은 TASK-BE-250)
