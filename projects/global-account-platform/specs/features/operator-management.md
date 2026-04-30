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
