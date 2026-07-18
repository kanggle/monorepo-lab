# Feature Spec: 운영자 관리 (Operator Management)

## Overview

SUPER_ADMIN 역할을 보유한 운영자가 다른 운영자 계정을 생성하고, 역할을 부여·회수하며, 계정 상태를 변경할 수 있는 기능. admin-service 내부에서 처리되며 별도 외부 IdP 의존 없음.

---

## Actors

| Actor | 조건 |
|---|---|
| SUPER_ADMIN | `operator.manage` 권한 보유. 모든 운영자 관리 작업 수행 가능 |
| 기타 역할 | 읽기 전용 — 자기 자신의 정보(`GET /api/admin/me`)만 조회 가능 |

---

## Endpoints Summary

| Method | Path | 설명 | 필요 권한 |
|---|---|---|---|
| `GET` | `/api/admin/me` | 현재 로그인 운영자 정보 조회 | 없음 (유효한 operator JWT만 필요) |
| `GET` | `/api/admin/operators` | 전체 운영자 목록 조회 (페이지네이션) | `operator.manage` |
| `POST` | `/api/admin/operators` | 신규 운영자 계정 생성 | `operator.manage` |
| `PATCH` | `/api/admin/operators/{operatorId}/roles` | 운영자 역할 전체 교체 | `operator.manage` |
| `PATCH` | `/api/admin/operators/{operatorId}/status` | 운영자 상태 변경 (ACTIVE ↔ SUSPENDED) | `operator.manage` |

---

## 동작 규칙

### GET /api/admin/me

- JWT `sub` 클레임에서 `operator_id` 추출 → `admin_operators` 조회
- `admin_operator_roles` JOIN을 통해 role 이름 목록 포함
- 민감 필드(`password_hash`, `totp_secret_encrypted`) 절대 노출 금지

### GET /api/admin/operators

- `admin_operators` 전체 목록을 `created_at DESC` 기본 정렬로 페이지네이션 반환
- 각 항목에 해당 운영자의 role 이름 목록 포함
- 상태 필터(`status=ACTIVE|SUSPENDED`) 지원

### POST /api/admin/operators

- `email`은 `admin_operators.email` UNIQUE 제약 위반 시 `409 OPERATOR_EMAIL_CONFLICT`
- 초기 비밀번호는 요청 body에 포함. `libs/java-security.PasswordHasher` (Argon2id)로 해시 저장
- `roles` 배열로 초기 역할 부여. 비어 있으면 역할 없이 생성 허용
- 생성 성공 시 신규 운영자는 2FA 미등록(`totp_enrolled_at = NULL`) 상태
- **2FA 미등록 운영자의 첫 로그인**: 기존 `POST /api/admin/auth/login` 플로우에서 `bootstrapToken` 발급 → `/api/admin/auth/2fa/enroll` 유도 (기존 스펙 TASK-BE-029 동작 그대로)
- 생성 운영자 정보를 `admin_actions`에 `action_code=OPERATOR_CREATE`로 기록

### PATCH /api/admin/operators/{operatorId}/roles

- `roles` 배열로 **전체 교체** (delta 방식 아님)
- 존재하지 않는 role 이름 포함 시 `400 ROLE_NOT_FOUND`
- 본인 역할 변경은 허용 (SUPER_ADMIN이 자신의 역할을 바꾸는 것 포함)
- 변경 후 Redis 권한 캐시 즉시 invalidate (`admin:operator:perm:{operatorId}`)
- `admin_actions`에 `action_code=OPERATOR_ROLE_CHANGE` 기록

### PATCH /api/admin/operators/{operatorId}/status

- 허용 전환: `ACTIVE → SUSPENDED`, `SUSPENDED → ACTIVE`
- `SUSPENDED` 처리 시: 해당 운영자의 모든 유효 refresh token 즉시 무효화 (Redis blacklist 또는 version bump)
- 본인 계정 SUSPENDED 불가 → `400 SELF_SUSPEND_FORBIDDEN`
- `admin_actions`에 `action_code=OPERATOR_STATUS_CHANGE` 기록

---

## 감사 기록 (audit trail)

모든 mutation은 `admin_actions` 테이블에 1건씩 기록한다.

| action_code | 발생 시점 |
|---|---|
| `OPERATOR_CREATE` | 운영자 생성 성공 |
| `OPERATOR_ROLE_CHANGE` | 역할 변경 성공 |
| `OPERATOR_STATUS_CHANGE` | 상태 변경 성공 |

`target_type = OPERATOR`, `target_id = 대상 운영자 operator_id (UUID)`.

---

## Edge Cases

- 존재하지 않는 `operatorId` 경로 변수: `404 OPERATOR_NOT_FOUND`
- 이미 `ACTIVE` 상태 운영자를 `ACTIVE`로 변경 요청: `400 STATE_TRANSITION_INVALID`
- 이미 `SUSPENDED` 상태 운영자를 `SUSPENDED`로 변경 요청: `400 STATE_TRANSITION_INVALID`
- 역할 없는 운영자 생성: 허용. 역할 없는 운영자는 `operator.manage` 외 모든 엔드포인트에서 `403`

---

## Security Constraints

- `operator.manage` 권한은 `SUPER_ADMIN`에만 부여 (seed matrix canonical source: `specs/services/admin-service/rbac.md`)
- 응답에서 `password_hash`, `totp_secret_encrypted` 절대 노출 금지
- `operatorId`는 UUID v7 (`admin_operators.operator_id`) — 내부 BIGINT PK 노출 금지

---

## 운영자 그룹 관리 (Operator Group Management) — ADR-MONO-046

여러 운영자를 **named unit**(`operator_group`)으로 묶어, 역할/tenant-assignment 를 **한 번에** 부여하는 기능. 5인 지원 스쿼드를 3개 테넌트에 온보딩할 때 15개의 개별 assignment row 대신 **그룹 1개 + grant 몇 건**으로 처리한다(AWS IAM User Group / Google Group 의 workforce-grouping facet). **v1 은 fan-out**(ADR-MONO-046 § D2-A) — 그룹 grant 는 각 멤버의 **평범한 flat assignment row**(`operator_tenant_assignment`/`admin_operator_roles`)로 materialise 되고 `group_origin` 마커로 태깅되며, 그룹 멤버십은 **평가-시점 edge 가 아니다**(`PermissionEvaluator`·perm-cache·confinement 축 byte-unchanged — `specs/services/admin-service/rbac.md` Operator Group Fan-Out). inheritance(D2-B)는 후속 ADR.

### Actors

| Actor | 조건 |
|---|---|
| SUPER_ADMIN | `group.manage` 보유. 모든 테넌트의 그룹 관리 (`'*'` net-zero) |
| TENANT_ADMIN | `group.manage` 보유. **자기 테넌트** 그룹만 관리 (`TenantScopeGuard` 대상 = `operator_group.tenant_id`) |
| ORG_ADMIN | `group.manage` 보유. 자기 org-node subtree 테넌트 그룹 관리 |
| 기타 역할 | 그룹 관리 불가 (`403 PERMISSION_DENIED`) |

### Endpoints Summary

| Method | Path | 설명 | 필요 권한 |
|---|---|---|---|
| `POST` | `/api/admin/groups` | 그룹 생성 | `group.manage` |
| `GET` | `/api/admin/groups` | 그룹 목록(스코프 confine) | `group.manage` |
| `GET` | `/api/admin/groups/{groupId}` | 단건 조회 | `group.manage` |
| `PATCH` | `/api/admin/groups/{groupId}` | rename/describe | `group.manage` |
| `DELETE` | `/api/admin/groups/{groupId}` | 삭제 → cascade-revoke | `group.manage` |
| `GET\|POST` | `/api/admin/groups/{groupId}/members` | 멤버 조회 / 추가(→ fan-out) | `group.manage` |
| `DELETE` | `/api/admin/groups/{groupId}/members/{operatorId}` | 멤버 제거(→ group_origin revoke) | `group.manage` |
| `GET\|POST` | `/api/admin/groups/{groupId}/grants` | grant 조회 / 부여(→ fan-out) | `group.manage` |
| `DELETE` | `/api/admin/groups/{groupId}/grants/{grantId}` | grant 회수(→ cascade-revoke) | `group.manage` |

계약 상세(schema·error envelope·confinement)는 `specs/contracts/http/admin-api.md` § Operator Group Management (ADR-MONO-046) 가 canonical.

### 생명주기 (fan-out / cascade — ADR-MONO-046 § D5 bullet-for-bullet)

end-to-end 흐름 **그룹 생성 → 멤버 추가 → grant → fan-out**, 그리고 **멤버 제거 / 그룹 삭제 → cascade-revoke**:

- **그룹에 role/assignment grant** → 모든 **현재 멤버**로 fan-out(각 멤버의 flat `operator_tenant_assignment`/`admin_operator_roles` row 를 `group_origin=<groupId>` 로 materialise). grant-time no-escalation(D4): granter 보유 이내만.
- **멤버 추가** → 그룹의 **현행 grant** 를 새 멤버로 fan-out. add-member 시점에 no-escalation(D4) **재검사**. 멤버는 그룹 테넌트 소속 operator 만(home `tenant_id` == 그룹 `tenant_id`).
- **멤버 제거** → 그 멤버의 `group_origin=<groupId>` 태그 grant **만** revoke. **직접 grant(`group_origin IS NULL`)는 불변**.
- **그룹 삭제** → 모든 `group_origin=<groupId>` assignment cascade-revoke; 멤버는 독립 직접 grant 를 유지.
- **멱등성(Idempotence)** — fan-out 은 이미 존재하는 **동등 직접 grant** 를 중복 생성하지 않으며(`(operator, tenant)`/`(operator, role)` PK 상 최대 1 row), removal 은 non-`group_origin` row 를 절대 건드리지 않는다.

### 감사 기록 (audit trail)

모든 그룹 mutation 은 `admin_actions` 에 1건씩 기록(D6, deny-default·fail-closed). `target_type=GROUP`, `target_id=<groupId>`, `target_tenant_id=<group tenant_id>`.

| action_code | 발생 시점 |
|---|---|
| `GROUP_CREATE` | 그룹 생성 |
| `GROUP_UPDATE` | rename/describe |
| `GROUP_DELETE` | 그룹 삭제(cascade-revoke) |
| `GROUP_MEMBER_ADD` | 멤버 추가(fan-out) |
| `GROUP_MEMBER_REMOVE` | 멤버 제거(group_origin revoke) |
| `GROUP_GRANT_ADD` | grant 부여(fan-out) |
| `GROUP_GRANT_REVOKE` | grant 회수(cascade-revoke) |

GET read-path 는 성공 시 감사 row 미기록(`grantable-roles`/BE-486 규약); 403 은 best-effort DENIED row. 이벤트는 v1 audit-only(소비자 존재 시에만 `admin_outbox` v2 새 `topicFor` 로 발행, D6).

### Edge Cases

- 존재하지 않는 `groupId` / actor 스코프 밖: `404 GROUP_NOT_FOUND`(enumeration-safe)
- `(tenantId, name)` 중복: `409 GROUP_NAME_CONFLICT`
- 타 테넌트 operator 를 멤버로 추가: `422 GROUP_MEMBER_TENANT_MISMATCH`
- 미보유 role/tenant 를 그룹으로 우회 부여: `403 ROLE_GRANT_FORBIDDEN` / `422 GROUP_GRANT_NO_ESCALATION`
- `tenantId='*'` 플랫폼-전역 그룹 생성 시도: `400 VALIDATION_ERROR`(그룹은 한 실제 테넌트 안 unit)

### Security Constraints

- `group.manage` 는 `SUPER_ADMIN`·`TENANT_ADMIN`·`ORG_ADMIN` 에만 부여 (`operator.manage` 도달 미러 — seed matrix canonical: `specs/services/admin-service/rbac.md`)
- 모든 그룹 변이는 `TenantScopeGuard`(대상 = `operator_group.tenant_id`, D3) + reason-gated + audited
- fan-out 은 새 평가/confinement 축을 도입하지 않는다 — v1 은 flat per-operator row materialise 뿐(`PermissionEvaluator` byte-unchanged)
