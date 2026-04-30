# Task ID

TASK-BE-028b

# Title

admin-service — admin_actions BIGINT PK 전환, audit envelope reshape, JWT claim 정리

# Status

backlog

# Owner

backend

# Task Tags

- code
- db
- adr
- event

# depends_on

- TASK-BE-028a

---

# Goal

28a에서 미뤄둔 구조적 스펙 준수 작업을 일괄 처리한다. 기능 변화는 없고 스키마·envelope·JWT 계약을 스펙 canonical 형태로 맞춘다.

---

# Scope

## In Scope

### admin_actions 스키마 전환
- Flyway 마이그레이션: `admin_actions.id`를 VARCHAR(36) → BIGINT AUTO_INCREMENT로 전환. 기존 UUID는 별도 `legacy_audit_id VARCHAR(36)` 컬럼으로 보존(조회 호환). 또는 full replace + backfill — 실제 데이터 양에 맞춰 선택.
- `operator_id`를 VARCHAR(36) → BIGINT FK로 전환 (28a에서 문자열 컬럼으로 선 도입했던 값을 `admin_operators.id` 대응으로 옮김)
- 기존 DB trigger(`trg_admin_actions_finalize_only`) BIGINT PK 기준으로 재작성
- `idx_admin_actions_operator_time` 인덱스의 컬럼명을 data-model.md와 정렬 (28a에서 `started_at` 사용 — canonical은 `occurred_at`). 실제 컬럼명 확인 후 한쪽으로 수렴.

### admin_operators 스키마 정정 (28a에서 deviation 발생)
- 28a는 `admin_operators.id`를 VARCHAR(36) PK로 생성했으나, `data-model.md` canonical은 `id BIGINT PK AUTO_INCREMENT` + 별도 `operator_id VARCHAR(36) UNIQUE NOT NULL` (외부 UUID / JWT sub).
- Flyway 마이그레이션: `admin_operators`에 `id BIGINT` 추가 → 기존 VARCHAR id 값을 새 `operator_id` 컬럼으로 이관 → BIGINT를 PK로 승격 → `operator_id`를 UNIQUE index.
- `admin_operator_roles.operator_id`, `admin_operator_roles.granted_by`, `admin_role_permissions.granted_by`, `admin_actions.operator_id`를 BIGINT FK로 전환.
- `PermissionEvaluatorImpl`를 `operator_id`(외부 UUID) 조회 → 내부 BIGINT로 매핑하도록 변경. 기존 `findById(operatorId)` 호출 사이트 모두 `findByOperatorId(...)` 등으로 교체.

### JWT claim 정리
- operator JWT 발급/검증 코드에서 `scope="admin"` → `token_type="admin"` 전환
- `roles` claim 제거 (DB 조회로 대체, 28a에서 이미 evaluator는 DB 사용)
- 기존 `@PreAuthorize` 어노테이션 및 `OperatorRole` enum 제거, `@RequiresPermission` 단일 체크 레이어로 수렴
- annotation 누락 경로를 aspect 수준에서 deny-by-default + `permission_used="<missing>"` 기록

### Audit envelope reshape (outbox)
- `admin.action.performed` 페이로드를 기존 flat 구조 → canonical envelope로 전환:
  `{ eventId, occurredAt, actor{type,id,sessionId}, action{permission,endpoint,method}, target{type,id,displayHint}, outcome, reason }`
- `actor.sessionId` = operator JWT `jti` (지금은 `OperatorContext`에 없으므로 capture 경로 추가)
- HTTP 엔드포인트/메서드를 `AdminActionAuditor`로 전달할 수단 추가 (`HttpServletRequest` 주입 또는 interceptor 메타데이터)
- `target.displayHint` = `AdminPiiMaskingUtils` 마스킹 결과 (신규 유틸. `security-service`의 `PiiMaskingUtils` 참조 구조)

### 테스트 조정
- `OperatorJwtTestFixture`의 `roles` claim 제거, `token_type` 적용
- `@WebMvcTest` 슬라이스 및 `AdminIntegrationTest`가 새 envelope/스키마와 정합
- 기존 테스트에 hardcode된 UUID audit id assertions → BIGINT 매칭으로 수정

### 28a 리뷰에서 이관된 항목
- **AuditController cross-permission DENIED**: 현재 단순 `throw PermissionDeniedException`이며 `admin_actions` row 미기록. Aspect 단일 경로로 수렴하면서 `auditor.recordDenied(actionCode=AUDIT_QUERY, permissionUsed="audit.read+security.event.read", ...)` 호출 추가.
- **RequiresPermissionAspect `@Order` 명시화**: 현재 `@Order(100)`이 Spring Security `MethodSecurityInterceptor`와 충돌 가능. 명시적으로 `@Order(200)`(또는 확인된 우선순위)로 설정하고 주석에 Spring Security 기본값 인용.
- **Seed 마이그레이션 idempotency**: 현재 bare `INSERT`. `INSERT IGNORE` 또는 `ON DUPLICATE KEY UPDATE` 적용하여 재실행 안전성 확보(rbac.md Seeding Strategy 요구).
- **AdminActionAuditor.recordDenied target_type 파생 개선**: 현재 하드코딩 `"TARGET"`/`"AUDIT_QUERY"`. `actionCode`/`permissionKey` → `target_type`(ACCOUNT/SESSION/AUDIT_QUERY) 매핑 도입.

## Out of Scope

- Redis 권한 캐시, UUIDv7 generator (28c)
- 새로운 permission/role 추가
- Retention 정책 실제 cron/batch 구현 (retention.md는 정책 선언만)

---

# Acceptance Criteria

- [ ] `admin_actions.id`가 BIGINT AUTO_INCREMENT이고 `operator_id`가 BIGINT FK
- [ ] `admin_operators.id`가 BIGINT PK, `operator_id`가 VARCHAR(36) UNIQUE 컬럼으로 분리
- [ ] `admin.action.performed` outbox 페이로드가 canonical envelope shape 준수
- [ ] `actor.sessionId`에 JWT `jti` 값이 들어가고, `target.displayHint`가 마스킹된 문자열
- [ ] JWT `token_type="admin"`로 통일, `roles` claim 제거
- [ ] AuditController cross-permission DENIED가 `admin_actions` row 기록
- [ ] Seed 마이그레이션 재실행 시 충돌 없음 (INSERT IGNORE 또는 동등)
- [ ] RequiresPermissionAspect `@Order`가 Spring Security와 비교해 명시적 우선순위
- [ ] `@PreAuthorize` 제거, aspect 단일 권한 체크
- [ ] annotation 누락 컨트롤러 메서드 → 런타임 403 (deny-by-default)
- [ ] 전체 테스트 통과

---

# Related Specs

- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/data-model.md`
- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases / Failure Scenarios

- 마이그레이션 중 기존 `admin_actions` 데이터가 있는 경우 — rollback plan + staged deploy 고려 (현 단계 개발 환경이라면 DB reset 가능)
- JWT 재발급 호환: 구 토큰(`scope="admin"`)을 transition window 동안 수용할지 선택 — 이 태스크 범위에서는 즉시 전환 (개발 단계)

---

# Test Requirements

- Unit: envelope serializer, `AdminPiiMaskingUtils`
- Slice/Integration: `AdminEventPublisherTest` — 새 envelope 검증; `AdminIntegrationTest` — BIGINT id + envelope 통합

---

# Definition of Done

- [ ] 구현·테스트 완료
- [ ] 28a TODO 주석 제거
- [ ] Ready for review
