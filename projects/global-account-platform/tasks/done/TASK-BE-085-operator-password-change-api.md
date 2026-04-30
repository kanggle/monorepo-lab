---
id: TASK-BE-085
title: "운영자 본인 비밀번호 변경 API"
status: ready
area: backend
service: admin-service
---

## Goal

로그인한 운영자가 자신의 비밀번호를 변경할 수 있는 `PATCH /api/admin/operators/me/password` 엔드포인트를 구현한다.

## Scope

### In

1. **`specs/contracts/http/admin-api.md`** — 계약 추가 (구현 전 선행)
2. **`AdminOperatorJpaEntity`** — `changePasswordHash(String, Instant)` 메서드 추가
3. **`exception/CurrentPasswordMismatchException`** — 신규
4. **`exception/PasswordPolicyViolationException`** — 신규
5. **`OperatorAdminUseCase`** — `changeMyPassword(String operatorId, String currentPassword, String newPassword)` 추가
   - 현재 비밀번호 `passwordHasher.verify()` 검증 → 불일치 시 `CurrentPasswordMismatchException`
   - 새 비밀번호 정책 검증 (8자 이상, 대·소·숫·특 4종 중 3종 이상) → 위반 시 `PasswordPolicyViolationException`
   - `passwordHasher.hash(newPassword)` → `entity.changePasswordHash(...)` → save
6. **`OperatorAdminController`** — `PATCH /api/admin/operators/me/password` 엔드포인트 추가
   - 권한 어노테이션 없음 (유효한 operator JWT만 필요)
   - 성공: `204 No Content`
7. **`AdminExceptionHandler`** — 두 신규 예외 핸들러 추가
   - `CurrentPasswordMismatchException` → `400 CURRENT_PASSWORD_MISMATCH`
   - `PasswordPolicyViolationException` → `400 PASSWORD_POLICY_VIOLATION`

### Out

- 다른 세션 revoke (스펙 "선택적 강화 모드"로 초기 범위 제외)
- 이메일 알림
- 비밀번호 변경 이력 테이블

## Acceptance Criteria

- [ ] `PATCH /api/admin/operators/me/password` 존재
- [ ] 유효한 요청 → `204 No Content`
- [ ] 현재 비밀번호 불일치 → `400 CURRENT_PASSWORD_MISMATCH`
- [ ] 새 비밀번호 정책 위반 → `400 PASSWORD_POLICY_VIOLATION`
- [ ] 비인증 요청 → `401 TOKEN_INVALID`
- [ ] 슬라이스 또는 통합 테스트 1개 이상

## Related Specs

- `specs/features/operator-management.md`
- `specs/features/password-management.md`

## Related Contracts

- `specs/contracts/http/admin-api.md` — PATCH /api/admin/operators/me/password (구현 전 추가)

## Edge Cases

- 새 비밀번호 = 현재 비밀번호: 정책 위반 아님 (서버 허용, 클라이언트 UX에서 처리)
- 비밀번호 128자 초과: `PASSWORD_POLICY_VIOLATION`
- JWT 없음 / 만료: `401` (기존 SecurityFilter가 처리)

## Failure Scenarios

- DB 장애: 500 (기존 글로벌 핸들러 처리)
