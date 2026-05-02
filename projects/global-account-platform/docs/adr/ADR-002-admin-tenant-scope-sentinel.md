# ADR-002: Admin Service — Tenant Scope Sentinel for Multi-Tenant Operator Isolation

**Status**: ACCEPTED
**Date**: 2026-05-02
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: ADR-001 (OIDC Adoption), TASK-BE-249

---

## Context

`global-account-platform`은 복수 테넌트(fan platform, game platform 등)를 단일 플랫폼에서 운영하는 멀티 테넌트 SaaS다. admin-service는 모든 테넌트 데이터에 대한 운영 명령(계정 잠금·해제, 감사 조회 등)을 처리하므로, 어떤 운영자가 어떤 테넌트 데이터에 접근할 수 있는지를 정확히 제어해야 한다.

초기 구현(V0001~V0022)에서는 테넌트 격리 없이 모든 운영자가 모든 데이터를 조회·명령할 수 있었다. 이는 단일 테넌트 환경에서는 문제가 없었지만, 멀티 테넌트 지원 요구가 생기면서 아키텍처적 보완이 필요해졌다.

### 해결해야 할 문제

1. 일반 운영자는 자신이 속한 테넌트 데이터만 조회/명령해야 한다.
2. SUPER_ADMIN은 모든 테넌트에 걸쳐 운영할 수 있어야 한다.
3. 감사 행(admin_actions)도 테넌트 컨텍스트를 기록해야 한다 (규정 감사 R9).
4. 기존 운영자 데이터(~V0022)는 기본 테넌트("fan-platform")로 소급 적용되어야 한다.

---

## Decision

### 핵심 결정: Sentinel Value `tenant_id = '*'`

SUPER_ADMIN 운영자의 플랫폼 스코프를 표현하기 위해 **sentinel 값 `'*'`**를 `tenant_id` 컬럼에 사용한다.

```sql
-- 일반 운영자
INSERT INTO admin_operators (..., tenant_id, ...) VALUES (..., 'fan-platform', ...);

-- SUPER_ADMIN (플랫폼 스코프)
INSERT INTO admin_operators (..., tenant_id, ...) VALUES (..., '*', ...);
```

이 방식은 Java 코드에서 `AdminOperator.PLATFORM_TENANT_ID = "*"` 상수로 참조된다.

### 대안과 기각 이유

| 대안 | 기각 이유 |
|---|---|
| 별도 `is_platform_scope BOOLEAN` 컬럼 | tenant_id 컬럼과 중복. 코드에서 두 컬럼을 조합해야 하는 복잡성 증가. |
| 별도 `platform_operators` 테이블 분리 | 스키마 복잡성 과도. 모든 쿼리에서 UNION이 필요. |
| Null tenant_id for SUPER_ADMIN | NULL은 "미설정" 의미. NOT NULL 제약으로 null 불가 + null 처리 버그 위험. |
| 권한(permission)만으로 크로스 테넌트 허용 | 권한 평가가 단일 체크로 확장되지 않음. 데이터 행 자체에 scope를 박아야 감사 조회 라우팅 가능. |

### DB 스키마 변경 (Flyway V0025)

3개 테이블에 `tenant_id` 컬럼 추가:

```sql
-- admin_operators: 기존 unique(email) → unique(tenant_id, email)
ALTER TABLE admin_operators ADD COLUMN tenant_id VARCHAR(32);
UPDATE admin_operators SET tenant_id = 'fan-platform';  -- backfill
UPDATE admin_operators o
  JOIN admin_operator_roles aor ON aor.operator_id = o.id
  JOIN admin_roles r ON r.id = aor.role_id AND r.name = 'SUPER_ADMIN'
  SET o.tenant_id = '*';  -- SUPER_ADMIN holders get platform scope
ALTER TABLE admin_operators MODIFY COLUMN tenant_id VARCHAR(32) NOT NULL;

-- admin_operator_roles: 역할 바인딩도 테넌트 명시
-- admin_actions: tenant_id + target_tenant_id 추가
```

2-Step 전략 (ADD NULL → backfill → ALTER NOT NULL)은 기존 데이터가 있는 테이블에서 NOT NULL 제약 위반 없이 마이그레이션하기 위한 표준 패턴이다.

### 감사 행 구성 규칙

| 운영자 유형 | `tenant_id` | `target_tenant_id` |
|---|---|---|
| 일반 운영자 자기 테넌트 행위 | `operator.tenantId` | `operator.tenantId` |
| SUPER_ADMIN 크로스 테넌트 행위 | `'*'` | `<대상 테넌트 ID>` |
| SUPER_ADMIN 자기 스코프(`*`) 행위 | `'*'` | `'*'` |

### `PermissionEvaluator.isTenantAllowed` 기본 규칙

```java
default boolean isTenantAllowed(AdminOperator operator, String targetTenantId) {
    if (operator == null) return false;
    if (operator.isPlatformScope()) return true;   // SUPER_ADMIN: 항상 허용
    String resolved = (targetTenantId == null) ? operator.tenantId() : targetTenantId;
    return operator.tenantId() != null && operator.tenantId().equals(resolved);
}
```

### 예외 및 HTTP 매핑

- `TenantScopeDeniedException` → 403 `TENANT_SCOPE_DENIED`
- 일반 운영자가 자신의 테넌트가 아닌 `tenantId`를 audit 조회에 지정하거나, `tenantId='*'` 운영자 생성을 시도할 때 발생

### 감사 쿼리 라우팅

```
tenantId 파라미터 | 운영자 유형        | Repository 메서드
-----------------+-------------------+------------------------
생략 또는 자기    | 일반 운영자        | findByTenantId(operator.tenantId)
생략             | SUPER_ADMIN        | findByTenantId('*')
'*'              | SUPER_ADMIN        | findByTenantId('*')  ← 플랫폼 행만
특정 테넌트       | SUPER_ADMIN        | searchCrossTenant(targetTenantId)
다른 테넌트       | 일반 운영자        | TenantScopeDeniedException
```

---

## Consequences

### 긍정적 효과

- **멀티 테넌트 격리**: 일반 운영자가 다른 테넌트 데이터를 조회·명령하는 것이 API 레벨과 DB 행 레벨에서 모두 차단된다.
- **감사 추적 완전성**: `admin_actions` 행에 `tenant_id + target_tenant_id`가 포함되어 크로스 테넌트 행위도 완전히 추적된다 (규정 감사 R9 준수).
- **Backward compat**: 모든 변경된 팩토리/레코드에 backward-compat 생성자/오버로드 추가로 기존 호출처 영향 최소화.

### 부정적 효과 / 트레이드오프

- **Sentinel 값 '`*`' 오용 위험**: `tenant_id='*'` 입력이 허용된 API 경로를 통해 일반 운영자가 SUPER_ADMIN 행을 생성하려는 시도가 있을 수 있다. 이는 `CreateOperatorUseCase`의 방어-심층(defense-in-depth) 체크로 차단 (`TenantScopeDeniedException`).
- **기존 테스트 수정 필요**: `admin_operators`, `admin_operator_roles` 시드 INSERT가 `tenant_id` 컬럼을 명시해야 한다. 기존 통합 테스트 시드 메서드 모두 업데이트 완료.
- **`*` 문자 URL 인코딩**: `GET /api/admin/audit?tenantId=*` 호출 시 일부 HTTP 클라이언트가 `%2A`로 인코딩할 수 있다. Spring의 `@RequestParam`은 양쪽 모두 정상 처리한다.

---

## Compliance Notes

- 규정 감사 R9 (감사 행 불변): `admin_actions`는 여전히 append-only. `tenant_id`/`target_tenant_id`는 INSERT 시점에 확정되고 이후 변경 불가 (V0010 트리거 보장).
- `tenant_id = '*'` 운영자 생성은 기존 플랫폼 스코프 운영자만 가능 — self-escalation 방지.

---

## References

- `TASK-BE-249` — 구현 작업 (tasks/review/)
- `specs/services/admin-service/architecture.md` §Tenant Scope Enforcement
- `specs/contracts/http/admin-api.md` §GET /api/admin/audit, §POST /api/admin/operators
- `db/migration/V0025__add_tenant_id_to_admin_tables.sql`
- `domain/rbac/AdminOperator.java` — `PLATFORM_TENANT_ID` constant, `isPlatformScope()`
- `domain/rbac/PermissionEvaluator.java` — `isTenantAllowed()` default method
