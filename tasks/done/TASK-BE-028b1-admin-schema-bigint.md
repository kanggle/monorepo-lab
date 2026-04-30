# Task ID

TASK-BE-028b1

# Title

admin-service — 스키마 BIGINT 전환 (admin_operators + admin_actions PK, 인덱스 정렬, seed idempotency)

# Status

ready

# Owner

backend

# Task Tags

- code
- db

# depends_on

- TASK-BE-028a

---

# Goal

스펙-부트스트랩 간 스키마 drift 해소. 이 태스크는 **DDL과 JPA entity 어댑터까지만** 처리하고, JWT claim 정리·envelope reshape 등 애플리케이션 레이어는 TASK-BE-028b2에서 이어서 처리한다.

---

# Scope

## In Scope

### admin_operators BIGINT PK + operator_id UUID 분리
- V0007 마이그레이션:
  - `admin_operators`에 `internal_id BIGINT AUTO_INCREMENT UNIQUE` 컬럼 추가
  - 기존 VARCHAR(36) `id` 값을 새 `operator_id VARCHAR(36) UNIQUE NOT NULL` 컬럼으로 이관
  - 기존 PK 제약 제거 후 `internal_id`를 PK로 승격, 컬럼명을 `id`로 rename (MySQL: DROP+ADD PK + CHANGE COLUMN)
  - 개발 데이터가 비어있으면 DROP+CREATE 단순 경로 허용 (실제 V0004 seed만 있는 상태면 이 쪽이 깨끗)
- `AdminOperatorJpaEntity` 업데이트: `@Id Long id` + `private String operatorId`
- `AdminOperatorJpaRepository`에 `Optional<AdminOperatorJpaEntity> findByOperatorId(String operatorId)` 추가
- `PermissionEvaluatorImpl`: JWT sub(UUID 문자열) → `findByOperatorId(...)` → 내부 `id`로 role/permission 조인
- `admin_operator_roles.operator_id`와 `admin_role_permissions.granted_by`, `admin_operator_roles.granted_by`를 BIGINT FK로 전환 (V0007에 포함)
- seed 마이그레이션 V0006 재실행 안전성은 V0009(별도 파일)에서 처리 — 이 마일스톤에서는 seed row 잔존 호환만 보장

### admin_actions BIGINT PK
- V0008 마이그레이션:
  - `admin_actions`에 `new_id BIGINT AUTO_INCREMENT UNIQUE` 컬럼 추가, 모든 row에 번호 할당
  - `legacy_audit_id VARCHAR(36)` 컬럼 추가, 기존 UUID `id` 값 복사
  - 기존 PK 제거, `new_id`를 PK로 승격, `id`로 rename
  - `operator_id VARCHAR(36) NULL` → `BIGINT NULL` (기존 UUID 값은 admin_operators.operator_id 조회로 매핑; 개발 데이터가 비어있으면 단순 DROP+RE-ADD)
  - 기존 trigger `trg_admin_actions_finalize_only` 재작성 — BIGINT PK 기준으로 WHERE 절 수정
  - 인덱스 `idx_admin_actions_operator_time`의 컬럼명을 실제 테이블 컬럼과 정렬 (V0005에서 `started_at` 사용 여부 확인 후 data-model.md와 맞춤)
- `AdminActionJpaEntity`: `@Id Long id` + `String legacyAuditId` + `Long operatorId`
- 응답 DTO(`LockAccountResponse`, `UnlockAccountResponse`, `RevokeSessionResponse` 등)가 현재 `auditId`(String)을 반환하면 **API 호환성 유지 위해 `legacyAuditId` 문자열을 그대로 반환**. 내부 PK는 BIGINT로만 사용.
- 기존 `AdminActionJpaRepository` 쿼리 전수 조사 — UUID 문자열 비교 로직이 있으면 legacy 컬럼 기반으로 수정

### Seed idempotency
- V0009 마이그레이션: rbac.md Seeding Strategy 준수. `INSERT IGNORE INTO admin_roles ...` + `INSERT IGNORE INTO admin_role_permissions ...`로 4개 role과 매트릭스가 누락된 경우 back-add. V0006이 이미 적용된 환경에서는 no-op이어야 함.

### Index column alignment
- V0005에서 `idx_admin_actions_operator_time (operator_id, started_at)`이 실제 테이블에 존재하는 컬럼을 쓰고 있는지 확인. data-model.md canonical은 `occurred_at`. 실제 컬럼이 `started_at`이면 data-model.md 업데이트 쪽 선택도 가능 (spec이 틀렸을 수 있음) — 실제 상태를 기준으로 수렴.

## Out of Scope (028b2로 이관)

- JWT claim(`scope` → `token_type`) 전환, `roles` claim 제거
- `@PreAuthorize` 제거, `OperatorRole` enum 삭제
- Aspect deny-by-default 가드레일
- AuditController cross-permission DENIED의 `recordDenied` 호출
- Outbox envelope reshape, `AdminPiiMaskingUtils`, jti 캡처
- 새 테스트 작성 (AdminPiiMaskingUtilsTest 등)

---

# Acceptance Criteria

- [ ] `admin_operators.id`가 BIGINT PK, `operator_id` VARCHAR(36) UNIQUE 컬럼으로 분리
- [ ] `admin_actions.id`가 BIGINT PK, `legacy_audit_id` 컬럼 보존, `operator_id` BIGINT FK
- [ ] `trg_admin_actions_finalize_only` trigger가 새 PK 기준으로 동작
- [ ] `PermissionEvaluatorImpl`이 external operator UUID → internal BIGINT 매핑 경로로 동작
- [ ] 응답 DTO는 기존대로 `auditId` 문자열(legacy UUID) 반환 — API 호환
- [ ] V0009 재실행 시 no-op (`INSERT IGNORE` 검증)
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/data-model.md`
- `specs/services/admin-service/rbac.md`

# Related Contracts

- `specs/contracts/http/admin-api.md` (auditId 응답 shape 확인)

---

# Target Service

- `apps/admin-service`

---

# Implementation Notes

- 기존 `@PreAuthorize` / `OperatorContext.roles` / `OperatorRole` enum / JWT 필터 `scope` 체크는 **일체 건드리지 않는다**. 028b2 대상.
- 028a에 남아 있는 4개 `// TODO(TASK-BE-028b):` 마커 중 "migrate admin_actions.id to BIGINT" 관련만 이번 태스크에서 제거. 나머지는 028b2에서.

---

# Edge Cases

- MySQL AUTO_INCREMENT는 기존 PK 제거 후 재설정 필요 — 단일 트랜잭션 불가하니 마이그레이션 단계 분리
- `admin_actions`에 FK 제약 이미 다른 테이블에서 걸려있으면 DROP 전에 DISABLE

# Failure Scenarios

- 마이그레이션 중 PK 전환 실패 → Flyway `out-of-order`가 아닌 fresh 환경에서 재현. 개발 환경 reset 전제.

---

# Test Requirements

- 기존 테스트 regression 없음
- 신규 테스트는 028b2 범위

---

# Definition of Done

- [ ] DDL·entity·repository·evaluator 수정 완료
- [ ] 전체 테스트 통과
- [ ] 028b2 착수 가능한 상태 (TODO 마커 부분 정리)
- [ ] Ready for review
