# Task ID

TASK-BE-027

# Title

admin-service — RBAC 스펙·계약 정의 (operators, roles, permissions)

# Status

backlog

# Owner

backend

# Task Tags

- api
- adr

# depends_on

- (없음)

---

# Goal

admin-service의 하드코딩된 SUPER_ADMIN 전제를 대체할 RBAC 모델과 HTTP 계약을 확정한다. 구현은 TASK-BE-028.

---

# Scope

## In Scope

- `specs/services/admin-service/rbac.md` 신규:
  - 테이블: `admin_operators`, `admin_roles`, `admin_role_permissions`, `admin_operator_roles`
  - Seed 역할: `SUPER_ADMIN`, `SUPPORT_READONLY`, `SUPPORT_LOCK`, `SECURITY_ANALYST`
  - 권한 키: `account.lock`, `account.unlock`, `account.force_logout`, `audit.read`, `security.event.read`
  - role → permission 매트릭스 명시
- `specs/contracts/http/admin-api.md` 엔드포인트별 required permission 선언
- `admin_actions` 감사 테이블 확장 스펙: `operator_id`, `permission_used` 컬럼 추가, 권한 거부 시에도 row 기록 (outcome=DENIED)

## Out of Scope

- 구현 (TASK-BE-028)
- 2FA (TASK-BE-029)
- 외부 IAM 연동

---

# Acceptance Criteria

- [ ] `rbac.md` 존재, 4 테이블·5 권한·4 role·매트릭스 명시
- [ ] admin-api.md 모든 mutation 엔드포인트에 permission 태그
- [ ] `admin_actions` 확장 마이그레이션 계획 포함

---

# Related Specs

- `specs/services/admin-service/architecture.md`

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- operator 여러 role 소유 시 union permission

---

# Failure Scenarios

- permission 키 오탈자 — 컴파일 타임 상수화 권장 (구현 태스크에서 처리)

---

# Test Requirements

- 스펙 리뷰

---

# Definition of Done

- [ ] Specs created
- [ ] Contracts updated
- [ ] Ready for implementation task (TASK-BE-028)
