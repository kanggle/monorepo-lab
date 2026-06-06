---
id: TASK-BE-083
title: "운영자 관리 API — GET /me, GET/POST /operators, PATCH roles/status"
status: ready
area: backend
service: admin-service
---

## Goal

SUPER_ADMIN이 운영자 계정을 생성·조회하고 역할·상태를 관리할 수 있는 API를 admin-service에 구현한다.

## Background

- `admin_operators`, `admin_roles`, `admin_operator_roles` 테이블과 JPA 레포지토리는 이미 존재한다.
- `RequiresPermissionAspect`, `CachingPermissionEvaluator` (Redis 캐시 포함) 인프라는 TASK-BE-028에서 구현 완료.
- `operator.manage` 권한 키가 `specs/services/admin-service/rbac.md`에 추가되었으나, 아직 `admin_role_permissions` seed에 반영되지 않았다 — 이 태스크에서 Flyway 마이그레이션 추가 필요.
- `GET /api/admin/me` 는 프론트엔드가 이미 호출하고 있으나 백엔드 구현이 없다.

## Scope

1. **Flyway 마이그레이션** — `operator.manage` 권한을 `SUPER_ADMIN` role에 seed
2. **`Permission.java` 상수** — `OPERATOR_MANAGE = "operator.manage"` 추가
3. **`OperatorAdminController`** — 아래 5개 엔드포인트 구현
4. **`OperatorAdminUseCase`** (application layer) — 비즈니스 로직
5. **감사 기록** — 모든 mutation에 `AdminActionAuditor` 통해 `admin_actions` 기록
6. **Redis 캐시 invalidate** — `PATCH /roles` 성공 시 `CachingPermissionEvaluator.invalidate(operatorId)` 호출
7. **단위 테스트 + 통합 테스트**

### Endpoints

| Method | Path | Permission | Action Code |
|---|---|---|---|
| `GET` | `/api/admin/me` | 없음 (JWT만 필요) | — |
| `GET` | `/api/admin/operators` | `operator.manage` | — |
| `POST` | `/api/admin/operators` | `operator.manage` | `OPERATOR_CREATE` |
| `PATCH` | `/api/admin/operators/{operatorId}/roles` | `operator.manage` | `OPERATOR_ROLE_CHANGE` |
| `PATCH` | `/api/admin/operators/{operatorId}/status` | `operator.manage` | `OPERATOR_STATUS_CHANGE` |

## Acceptance Criteria

- [ ] `GET /api/admin/me` 호출 시 현재 운영자 정보(operatorId, email, displayName, status, roles, totpEnrolled, lastLoginAt, createdAt) 반환
- [ ] `GET /api/admin/operators` — `operator.manage` 보유 시 전체 목록 페이지네이션 반환, 미보유 시 403
- [ ] `POST /api/admin/operators` — 운영자 생성, password Argon2id 해시, roles 초기 부여, 201 반환
- [ ] `POST /api/admin/operators` — email 중복 시 409 `OPERATOR_EMAIL_CONFLICT`
- [ ] `PATCH .../roles` — 역할 전체 교체, Redis 캐시 invalidate, audit 기록
- [ ] `PATCH .../status` — 상태 변경, SUSPENDED 시 refresh token 무효화, audit 기록
- [ ] `PATCH .../status` — 본인 계정 SUSPENDED 시 400 `SELF_SUSPEND_FORBIDDEN`
- [ ] 존재하지 않는 operatorId 요청 시 404 `OPERATOR_NOT_FOUND`
- [ ] 모든 mutation에 `admin_actions` row 기록 (A10 fail-closed)
- [ ] 응답에 `password_hash`, `totp_secret_encrypted` 절대 포함 안 됨

## Related Specs

- `specs/features/operator-management.md`
- `specs/services/admin-service/rbac.md` — `operator.manage` 권한 키, Seed Matrix
- `specs/services/admin-service/data-model.md` — `admin_operators`, `admin_operator_roles` 스키마

## Related Contracts

- `specs/contracts/http/admin-api.md` — GET /api/admin/me, GET/POST /api/admin/operators, PATCH roles/status

## Edge Cases

- `roles` 빈 배열: 역할 전부 제거 허용
- 존재하지 않는 role 이름: 400 `ROLE_NOT_FOUND`
- 이미 동일 status로 변경 요청: 400 `STATE_TRANSITION_INVALID`
- `operator.manage` 없는 운영자가 목록 조회: 403 (빈 목록 아닌 403)

## Failure Scenarios

- `admin_actions` INSERT 실패 → 500 반환, mutation 롤백 (A10 fail-closed)
- Redis invalidate 실패 → 로그 기록 후 진행 (캐시 TTL 10초로 자연 만료)
- 비밀번호 해시 중 예외 → 500, 운영자 생성 롤백
