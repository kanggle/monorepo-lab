# Task ID

TASK-BE-044

# Title

admin-service Open Items 8/8 closure (retroactive — PR #273 traceability)

# Status

done — PR #273 머지 (2026-05-09). 본 task 는 **retroactive filing** — PR #273 작업 자체는 TASK ID 없이 housekeeping 으로 진행되었으며, 이 파일은 추후 audit / cross-link anchor 용 traceability artifact 다.

# Owner

spec / backend (analysis)

# Task Tags

- spec
- chore

---

# Required Sections

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts

---

# Goal

`projects/wms-platform/specs/services/admin-service/architecture.md § Open Items` 의 8건이 admin-service 첫 `TASK-BE-*` impl task 의 ready/ 진입을 게이팅하고 있었다 (CLAUDE.md "Required Workflow" / "Hard Stop Rules" 적용). 본 task 는 그 8건을 모두 close 하여 게이팅 해소.

---

# Scope

## In Scope (PR #273)

### 신규 작성 (4 file)

1. `specs/contracts/http/admin-service-api.md` (834 lines) — REST 계약 6 surface (Dashboard / User / Role / Assignment / Settings / Ops health), 인증·idempotency·페이지네이션·낙관적락·에러 envelope, Auth 매트릭스
2. `specs/contracts/events/admin-events.md` (449 lines) — `wms.admin.user/role/assignment/settings.v1` 4 publish topic + 14 read-model projection consumed topic, last-write-wins, DLT, backwards-compat 정책
3. `specs/services/admin-service/idempotency.md` (429 lines) — REST 24h Redis + Kafka 30d eventId dedupe + last_event_at last-write-wins 가드, append-only PK 안전망, 30일 replay 위험 분석
4. `specs/services/admin-service/runbooks/read-model-rebuild.md` (433 lines) — 9 step ops 절차 (preflight → freeze → truncate → offset reset → rescale → catch-up → smoke → counter cross-check → announce), acknowledgement preservation, failure modes

### 기존 보강 (4 file)

5. `platform/error-handling.md` — Admin `[domain: wms]` 섹션 신설 (11 코드: USER_NOT_FOUND, ROLE_NOT_FOUND, ASSIGNMENT_NOT_FOUND, SETTING_NOT_FOUND, USER_EMAIL_DUPLICATE, ROLE_CODE_DUPLICATE, USER_HAS_ACTIVE_ASSIGNMENTS, ROLE_IN_USE, ROLE_BUILTIN_IMMUTABLE, SETTING_VALIDATION_ERROR, SETTING_IMMUTABLE_FIELD)
6. `rules/domains/wms.md` — Standard Error Codes 에 `### Admin / Operations` 카탈로그 cross-reference 추가
7. `projects/wms-platform/specs/services/gateway-service/architecture.md` + `public-routes.md` — `/api/v1/admin/**` → `admin-service:8086` 라우트 + admin tier rate limit (60 rpm/IP)
8. `projects/wms-platform/PROJECT.md § Overrides` — admin-service Layered 예외 mirror (`architecture.md § Override Declaration` 와 동기화)

## Out of Scope (별도 task)

- admin-service 첫 BE impl task (예: bootstrap / dashboard / user mgmt) — 본 task close 후 ready/ 진입 가능
- read-model 재구성 (테스트 / 운영 cross-check)
- gateway-service 코드 변경 (라우트 spec 만 정의)

---

# Acceptance Criteria

- [x] AC-01 — architecture.md § Open Items 8/8 ✅ 마킹
- [x] AC-02 — domain-model.md § Open Items 동기화 ✅ 마킹
- [x] AC-03 — `platform/error-handling.md` Admin 섹션 등록 + `rules/domains/wms.md` cross-reference 일치
- [x] AC-04 — gateway-service 라우트 표 (`architecture.md` + `public-routes.md`) sibling (`/api/v1/master/**`, `/api/v1/inbound/**`) 와 형식 일치
- [x] AC-05 — `PROJECT.md § Overrides` 와 `admin-service/architecture.md § Override Declaration` 의 reason / scope / expiry 일치
- [x] AC-06 — PR #273 main 머지 (squash → 17a7dd7a)

---

# Related Specs

- `specs/services/admin-service/architecture.md` (open items 발신지)
- `specs/services/admin-service/domain-model.md`
- `specs/services/admin-service/idempotency.md` (신규)
- `specs/services/admin-service/runbooks/read-model-rebuild.md` (신규)
- `specs/services/gateway-service/architecture.md`
- `specs/services/gateway-service/public-routes.md`

# Related Contracts

- `specs/contracts/http/admin-service-api.md` (신규)
- `specs/contracts/events/admin-events.md` (신규)
- `platform/error-handling.md` (Admin 섹션 신설)
- `rules/domains/wms.md` (Standard Error Codes Admin/Operations cross-ref)

---

# Notes

- **Retroactive filing rationale**: PR #273 진행 시점에는 housekeeping 성격이라 TASK ID 부여 안 함. 사후 audit / 후속 admin-service impl task 의 cross-link anchor 가 필요하다는 판단 (사용자 옵션 (a) 채택, 2026-05-09).
- **D4 churn freeze 영향**: `platform/error-handling.md` + `rules/domains/wms.md` 변경이 shared 영역 churn freeze (ADR-MONO-003 Phase 5 게이팅) 시계를 1일 reset (재평가 ≥ 2026-06-08). 추가형 카탈로그 등록이라 reshape 는 아니지만 정책 상 churn 카운트.
- **다음 추천**: TASK-BE-043 notification-service bootstrap 또는 admin-service 첫 BE impl task 신규 발행.
