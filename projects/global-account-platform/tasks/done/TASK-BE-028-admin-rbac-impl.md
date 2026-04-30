# Task ID

TASK-BE-028

# Title

admin-service — RBAC 구현 (permission evaluator, @RequiresPermission, admin_actions 확장)

# Status

backlog

# Owner

backend

# Task Tags

- code
- api
- db

# depends_on

- TASK-BE-027

---

# Goal

TASK-BE-027 스펙대로 admin-service에 RBAC을 실제 적용한다. 모든 operator 행동이 명시 permission을 통과해야 하고, `admin_actions`에 `operator_id`, `permission_used`, 그리고 DENIED outcome이 기록된다.

---

# Scope

## In Scope

- Flyway 마이그레이션: `admin_operators`, `admin_roles`, `admin_role_permissions`, `admin_operator_roles` + `admin_actions` 컬럼 추가
- 도메인: `AdminOperator`, `AdminRole`, `Permission` enum/string 상수
- 애플리케이션: `PermissionEvaluator.hasPermission(operatorId, Permission)`, 실패 시 `PermissionDeniedException`
- 프레젠테이션: `@RequiresPermission("account.lock")` annotation + HandlerInterceptor 또는 AOP Aspect
- 기존 컨트롤러 메서드에 annotation 부착 (`AccountAdminController`, `AuditQueryController` 등)
- `admin_actions` 기록 로직 확장: 성공/거부 둘 다 row 생성
- Seed migration: SUPER_ADMIN role + 권한 풀세트 기본 삽입

## Out of Scope

- 2FA (TASK-BE-029)
- bulk ops (TASK-BE-030)
- UI

---

# Acceptance Criteria

- [ ] SUPPORT_READONLY operator가 `POST /api/admin/accounts/{id}/lock` 호출 → 403 + `admin_actions.outcome=DENIED`, `permission_used=account.lock`
- [ ] SUPER_ADMIN operator가 동일 호출 → 200 + `admin_actions.outcome=SUCCESS`, `permission_used=account.lock`
- [ ] 기존 테스트 영향 없음 (통합 테스트에서 SUPER_ADMIN 기본 컨텍스트 제공)
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/architecture.md`

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- operator 삭제/비활성 상태 — 모든 permission 거부
- annotation 누락된 컨트롤러 메서드 — 정책: 기본 deny (가드레일)

---

# Failure Scenarios

- permission 쿼리 실패 (DB 장애) → fail-closed (403)

---

# Test Requirements

- Unit: `PermissionEvaluatorTest`
- Slice: `@WebMvcTest` + annotated controller
- Integration: SUPER_ADMIN vs SUPPORT_READONLY 케이스

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Ready for review
