---
id: TASK-BE-113
title: "feat(admin): 운영자 2FA 복구 코드 재발급 엔드포인트"
status: ready
priority: high
target_service: admin-service
tags: [code, api]
created_at: 2026-04-26
---

# TASK-BE-113: 운영자 2FA 복구 코드 재발급 (POST /api/admin/auth/2fa/recovery-codes/regenerate)

## Goal

운영자가 TOTP 백업 복구 코드를 모두 소진하거나 분실했을 때 새로운 코드 세트를 발급받을 수 있도록 한다.

현재:
- TOTP 등록 시 복구 코드 10개가 발급되고(`TotpEnrollmentService`)
- 로그인 시 복구 코드 소비는 구현되어 있다(`AdminLoginService.consumeRecoveryCode`)
- **누락**: 코드 소진/분실 후 재발급 엔드포인트 없음 → 운영자가 인증앱 없이 잠길 수 있음

## Scope

### In

**`specs/contracts/http/admin-api.md` 계약 추가 (구현 전 먼저)**
- `POST /api/admin/auth/2fa/recovery-codes/regenerate`
  - Auth: operator access JWT (`token_type=admin`)
  - Request: body 없음
  - Response 200: `{ "recoveryCodes": ["XXXX-XXXX-XXXX", ...] }` (10개 plain-text, 1회만 노출)
  - 사이드 이펙트: 기존 `recovery_codes_hashed` 완전 교체 (이전 코드 즉시 무효화)
  - Errors:
    - 401 `TOKEN_INVALID` / `TOKEN_REVOKED`: 토큰 인증 실패
    - 404 `TOTP_NOT_ENROLLED`: TOTP 등록 전 상태 (재발급 전 등록 필요)

**`TotpEnrollmentService` 수정 (또는 신규 메서드 추가)**
- `regenerateRecoveryCodes(long operatorPk): List<String>` 메서드
  - 10개 새 복구 코드 생성 (기존 `generateRecoveryCode` 헬퍼 재사용)
  - 각 코드를 Argon2id 해시 (`PasswordHasher.hash(plainCode)`)
  - `AdminOperatorTotpJpaEntity.replaceRecoveryHashes(newHashedJson, Instant.now())` 호출
  - TOTP 미등록 시 `TotpNotEnrolledException` 발생
  - plain-text 코드 목록 반환 (호출자가 응답에 담아 반환 후 폐기)

**`TotpNotEnrolledException` (신규 또는 기존 확인)**
- `application/exception/TotpNotEnrolledException.java`
- `extends RuntimeException`

**`AdminAuthController` (또는 신규 컨트롤러) 엔드포인트 추가**
- `POST /api/admin/auth/2fa/recovery-codes/regenerate`
- Auth: `OperatorAuthenticationFilter` (operator JWT 검증)
- `operatorId` — SecurityContext에서 추출 (기존 패턴 유지)
- Response: 200 + `{ "recoveryCodes": [...] }`

**`AdminExceptionHandler` 핸들러 추가**
- `TotpNotEnrolledException` → 404 `TOTP_NOT_ENROLLED`

**테스트**
- `RegenerateRecoveryCodesUseCaseTest` (단위): 10코드 생성, 기존 코드 교체, 미등록 예외
- `TotpRecoveryCodeRegenerateControllerTest` (`@WebMvcTest`): 200 응답, 401 인증 실패, 404 미등록

### Out
- TOTP 등록/검증 로직 변경
- 복구 코드 남은 개수 조회 엔드포인트 (별도 태스크)
- 슈퍼어드민의 타 운영자 복구 코드 강제 재발급 (별도 태스크)

## Acceptance Criteria

1. `POST /api/admin/auth/2fa/recovery-codes/regenerate` 가 유효한 operator JWT로 200을 반환하고 응답에 `recoveryCodes` 배열(10개)이 포함된다.
2. 재발급 후 이전 복구 코드로 로그인 시도하면 401 `INVALID_RECOVERY_CODE`가 반환된다.
3. 재발급 후 새 복구 코드로 로그인 시 성공한다.
4. TOTP 미등록 운영자가 재발급 요청 시 404 `TOTP_NOT_ENROLLED`가 반환된다.
5. `specs/contracts/http/admin-api.md`에 엔드포인트 계약이 추가된다.
6. `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL.

## Related Specs

- `specs/services/admin-service/architecture.md` — Self-issued IdP, TOTP 구현 세부, 복구 코드 10개 Argon2id 해시
- `specs/features/operator-management.md` — 운영자 2FA 규칙

## Related Skills

- `.claude/skills/INDEX.md` 참조

## Related Contracts

- `specs/contracts/http/admin-api.md` — 이 태스크에서 먼저 수정 후 구현

## Target Service

admin-service

## Architecture

`specs/services/admin-service/architecture.md` 참조.  
레이어: `presentation → application → domain/infrastructure` (단방향).  
`TotpEnrollmentService`는 `application` 레이어.

## Implementation Notes

- 복구 코드 형식: `XXXX-XXXX-XXXX` (기존 `generateRecoveryCode` 헬퍼에서 생성)
- Argon2id 해시는 `libs/java-security`의 `PasswordHasher`를 사용 (기존 `TotpEnrollmentService` 패턴 동일)
- `AdminOperatorTotpJpaEntity.replaceRecoveryHashes`는 이미 구현됨 — JSON 직렬화 포함
- `operatorId` (long PK) — SecurityContext에서 `AdminAuthentication` 또는 JWT claim에서 추출 (기존 컨트롤러 패턴 확인 후 동일하게 적용)
- plain-text 코드는 응답 반환 즉시 로직 내에서 보유하지 않는다 (메모리 잔류 최소화)
- 로그에 plain-text 복구 코드를 출력하지 않는다 (R4 준수)

## Edge Cases

- TOTP 미등록 상태에서 재발급 요청: `AdminOperatorTotpJpaRepository.findByOperatorPk` empty → 404
- 재발급 중 DB 오류: `@Transactional` 내에서 처리 → rollback, 이전 코드 유지
- 모든 코드가 이미 소진된 상태: 정상 재발급 가능 (소진 여부 무관하게 동작)

## Failure Scenarios

- Argon2id 해시 실패: 극히 낮은 확률; 예외 전파 → 500
- `replaceRecoveryHashes` 직렬화 오류: `IllegalStateException` → 500

## Test Requirements

### 단위 테스트
- `TotpEnrollmentServiceTest` (또는 신규 `RegenerateRecoveryCodesTest`): 10코드 생성, replaceRecoveryHashes 호출, 미등록 예외

### 컨트롤러 테스트 (`@WebMvcTest`)
- 200: 유효 JWT, TOTP 등록 상태 → 10코드 응답
- 401: JWT 미제출 또는 invalid
- 404: TOTP 미등록

## Definition of Done

- [ ] `specs/contracts/http/admin-api.md` 계약 추가
- [ ] `TotpEnrollmentService.regenerateRecoveryCodes` 구현
- [ ] `POST /api/admin/auth/2fa/recovery-codes/regenerate` 엔드포인트 동작
- [ ] 단위 테스트 통과
- [ ] 컨트롤러 테스트 통과
- [ ] `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL
- [ ] 코드 리뷰 통과
- [ ] `tasks/review/`로 이동 완료
