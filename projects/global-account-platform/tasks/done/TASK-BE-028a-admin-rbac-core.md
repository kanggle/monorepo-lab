# Task ID

TASK-BE-028a

# Title

admin-service — RBAC 핵심 (tables + PermissionEvaluator + @RequiresPermission + DENIED audit row)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- db

# depends_on

- TASK-BE-027
- TASK-BE-037

---

# Goal

admin-service에 RBAC 최소 구성을 이식한다. 기존 `admin_actions` 스키마(UUID `id`, 문자열 `actor_id`)와 기존 `@PreAuthorize`/OperatorRole enum은 28b·28c가 정리할 때까지 유지하고, 이 태스크는 **behavior 수준의 ACL 전환**만 완성한다.

---

# Scope

## In Scope

### 마이그레이션
- `admin_operators` (UUID v7는 28c 범위; 이번엔 VARCHAR(36) + `CHAR(36) DEFAULT UUID()` 또는 기존 관례에 맞는 id 전략)
- `admin_roles`, `admin_role_permissions`, `admin_operator_roles`
- Seed: 4 roles + 매트릭스 row (SUPER_ADMIN / SUPPORT_READONLY / SUPPORT_LOCK / SECURITY_ANALYST)
- `admin_actions` ALTER — `operator_id VARCHAR(36) NULL` (FK 제약은 28b에서 BIGINT 전환 시 재수립; 지금은 단순 컬럼), `permission_used VARCHAR(64) NULL`, `outcome` enum에 DENIED 추가 (ENUM 확장 또는 VARCHAR로 전환 방식 중 admin_actions 현황에 맞게 선택)

### 도메인
- `AdminOperator`, `AdminRole` (POJO), `Permission` 상수 클래스 (5개 키)
- `PermissionEvaluator` 포트: `hasPermission(operatorId, permission)`, `hasAllPermissions(operatorId, Collection<String>)`

### 인프라
- 4개 JPA entity + adapter (POJO ↔ JPA 패턴은 `membership-service`의 `SubscriptionRepositoryAdapter` 참조)
- `PermissionEvaluatorImpl` — 매 요청마다 DB 조회. Redis 캐시는 **TASK-BE-028c**에서.

### 프레젠테이션
- `@RequiresPermission("account.lock")` 어노테이션 + HandlerInterceptor 또는 Spring AOP Aspect
- 실패 시 `PermissionDeniedException` → 403 PERMISSION_DENIED 매핑 (admin-api.md의 기존 응답 shape 준수)
- `AccountAdminController`, `AuditQueryController` 등 mutation/read 엔드포인트에 annotation 부착
- Audit 특수 경로: `GET /api/admin/audit?source=login_history|suspicious` → `audit.read` + `security.event.read` 교집합 검증. 간단한 구현: 컨트롤러 메서드 내부에서 `PermissionEvaluator.hasAllPermissions(...)` 직접 호출 + 실패 시 수동 `PermissionDeniedException` throw (annotation-driven 단일성 원칙은 단일 permission 케이스에 한정).
- 기존 `@PreAuthorize`는 **유지**. 새 annotation은 추가 레이어로 올라오며, 두 체크 모두 통과해야 한다는 요구는 없음 — `@PreAuthorize`가 먼저 실행되므로 role 기반 최소 가드 + permission 기반 세밀 가드가 중첩. 28b에서 `@PreAuthorize` 제거.

### Audit row 기록
- 기존 `AdminActionAuditor`에 DENIED 경로 추가. Aspect/interceptor가 거부 시 `outcome=DENIED`, `operator_id=<JWT sub>`, `permission_used=<required>` row 1건 insert.
- 성공 경로 기존 IN_PROGRESS → SUCCESS 흐름은 그대로. `operator_id`/`permission_used` 컬럼만 이 태스크에서 채우기 시작.
- DB trigger(`trg_admin_actions_finalize_only`)는 INSERT에 DENIED를 허용하므로 추가 조치 불필요. UPDATE 흐름은 변경 없음.

### Annotation 누락 가드레일
- rbac.md D2 "missing annotation = deny by default, `permission_used="<missing>"`"는 이번 태스크에서 **기본 차단 대신 테스트에서 검출** 방식으로 처리: `AspectCoverageTest`가 admin-service의 모든 mutation 컨트롤러 메서드에 `@RequiresPermission`이 붙어 있는지 검증. 프로덕션 deny-by-default는 28b의 aspect 일원화 시 적용.

## Out of Scope (28b/28c로 이관)

- `admin_actions` BIGINT PK 전환, `operator_id` FK 제약
- 이벤트 envelope reshape (`actor/action/target/...`), `actor.sessionId`(JWT jti), `target.displayHint` 마스킹, `AdminPiiMaskingUtils`
- JWT claim contract 전환(`scope` → `token_type`, roles claim 제거)
- Redis 10s 권한 캐시
- UUID v7 generator 도입
- 기존 `@PreAuthorize` 제거 및 OperatorRole enum 정리
- Seed 스크립트로 operator 계정 생성 (운영 도구 책임)

---

# Acceptance Criteria

- [ ] 4개 RBAC 테이블 + seed 데이터 마이그레이션 적용
- [ ] `admin_actions` ALTER 후 기존 테스트 회귀 없음
- [ ] SUPPORT_READONLY operator가 `POST /api/admin/accounts/{id}/lock` 호출 → 403 + `admin_actions.outcome=DENIED, permission_used=account.lock` 기록
- [ ] SUPER_ADMIN operator가 동일 호출 → 200 + `outcome=SUCCESS, permission_used=account.lock`
- [ ] `GET /api/admin/audit?source=login_history`를 SUPPORT_LOCK이 호출 → 403 (audit.read는 있으나 security.event.read 부재)
- [ ] 동일 호출을 SECURITY_ANALYST가 → 200
- [ ] `AspectCoverageTest`가 mutation 컨트롤러 전수 검증 통과
- [ ] `./gradlew :apps:admin-service:test` 전체 통과

---

# Related Specs

- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/data-model.md`
- `specs/services/admin-service/retention.md` (참조만)

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Implementation Notes

- `admin_actions.id` 유지 (UUID 문자열). BIGINT 전환은 28b에서.
- 기존 `OperatorContext` / `OperatorRole` enum을 바꾸지 않는다. 새 `Permission` 상수는 annotation 인자 전용 문자열로 사용.
- Aspect 위치: `apps/admin-service/src/main/java/com/example/admin/presentation/aspect/RequiresPermissionAspect.java` 또는 interceptor/config 레이어 기존 관례에 맞춤.
- 실패 시 audit row 트리거 위치: aspect 내부에서 `AdminActionAuditor.recordDenied(...)` 호출. Auditor에 DENIED용 오버로드 추가.

---

# Edge Cases

- operator_id가 JWT `sub`에 있는 UUID 문자열 — 새 `admin_operators.id`와 매핑. 개발 환경에서는 `admin_operators`에 테스트용 operator를 seed하는 대신 test fixture가 직접 row 삽입.
- 여러 role을 가진 operator: permission union.
- 비활성 operator: 401 — rbac.md 알고리즘의 pre-check 단계.

---

# Failure Scenarios

- DB 장애로 permission 조회 실패 → fail-closed 403. (캐시는 28c.)

---

# Test Requirements

- Unit: `PermissionEvaluatorTest` — role union, 누락/비활성 operator.
- Slice: `RequiresPermissionAspectTest` 또는 `@WebMvcTest`에서 mock evaluator로 SUPPORT_READONLY vs SUPER_ADMIN 케이스.
- Unit/Slice: audit 교집합 엔드포인트 3종 시나리오.
- Verify: `AspectCoverageTest`.
- 기존 `AdminIntegrationTest`는 SUPER_ADMIN 컨텍스트로 기본 실행되어 회귀 없음.

---

# Definition of Done

- [ ] 구현 완료, 전체 테스트 통과
- [ ] 28b·28c로 이관된 항목이 이 태스크 코드에 명시적 TODO로 남아 있음 (추적 가능성)
- [ ] Ready for review
