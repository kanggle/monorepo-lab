---
id: TASK-BE-116
title: "fix(admin): TASK-BE-113 리뷰 지적 사항 수정 — 에러 코드 등록 및 통합 테스트 추가"
status: ready
priority: high
target_service: admin-service
tags: [code, api]
created_at: 2026-04-26
---

# TASK-BE-116: TASK-BE-113 리뷰 후속 수정

## Goal

TASK-BE-113(운영자 2FA 복구 코드 재발급)에서 코드 리뷰를 통해 발견된 두 가지 문제를 수정한다.

1. `TOTP_NOT_ENROLLED` 에러 코드를 `platform/error-handling.md`에 등록 — 현재 미등록 상태로 "Error codes must be registered in this document before use" 규칙 위반.
2. 복구 코드 재발급 플로우의 통합 테스트(`*IntegrationTest.java`) 추가 — `platform/testing-strategy.md`는 `code` 태그 태스크에 Unit / Slice / Integration 세 레이어를 모두 요구하나, TASK-BE-113 구현에서 통합 테스트가 누락됨.

## Scope

### In

**`platform/error-handling.md` 수정**
- `## Admin Operations [domain: saas]` 섹션에 `TOTP_NOT_ENROLLED` 에러 코드 추가:

```
| TOTP_NOT_ENROLLED | 404 | Operator has not yet enrolled TOTP; regeneration requires an existing enrollment (`POST /api/admin/auth/2fa/enroll` 선행 필요) |
```

**통합 테스트 추가**
- `apps/admin-service/src/test/java/com/example/admin/integration/` 아래에 `RecoveryCodeRegenerateIntegrationTest.java` 추가 (또는 기존 `AdminIntegrationTest` 확장)
- 기존 `AbstractIntegrationTest` 상속 패턴 유지 (Testcontainers MySQL + Kafka 공유)
- 커버해야 할 시나리오:
  1. 유효한 operator JWT + TOTP 등록 상태 → 200, `recoveryCodes` 10개 반환, DB에 새 hashes 저장됨.
  2. 이전 복구 코드로 로그인 시 401 `INVALID_RECOVERY_CODE` 반환(재발급 후 기존 코드 무효화 확인, 수락 기준 AC2).
  3. 새 복구 코드로 로그인 성공(수락 기준 AC3).
  4. TOTP 미등록 운영자가 재발급 요청 → 404 `TOTP_NOT_ENROLLED`.
  5. JWT 미제출 → 401.

### Out
- `TotpEnrollmentService.regenerateRecoveryCodes` 시그니처 변경 — `String operatorUuid` 방식은 기존 패턴(`enroll`, `verify` 메서드)과 일치하며 기능 상 올바름. 변경 불필요.
- 기타 TASK-BE-113 구현 로직 수정

## Acceptance Criteria

1. `platform/error-handling.md`의 `Admin Operations [domain: saas]` 섹션에 `TOTP_NOT_ENROLLED | 404` 항목이 추가된다.
2. `RecoveryCodeRegenerateIntegrationTest` (또는 동등한 통합 테스트 클래스)가 위의 5개 시나리오를 모두 커버한다.
3. `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL.

## Related Specs

- `platform/error-handling.md` — 에러 코드 등록 규칙
- `platform/testing-strategy.md` — code 태그 태스크의 테스트 레이어 요구사항
- `specs/services/admin-service/architecture.md` — 테스트 기대 사항
- `specs/contracts/http/admin-api.md` — `POST /api/admin/auth/2fa/recovery-codes/regenerate` 계약

## Related Contracts

- `specs/contracts/http/admin-api.md`

## Target Service

admin-service

## Architecture

`specs/services/admin-service/architecture.md` 참조. 통합 테스트는 기존 `AbstractIntegrationTest` 상속 패턴을 따른다.

## Implementation Notes

- `AbstractIntegrationTest`를 상속하여 MySQL + Kafka 컨테이너를 공유한다 (테스트 전략 가이드라인 준수).
- WireMock 또는 직접 DB 조작으로 TOTP 등록 상태를 준비한다.
- plain-text 복구 코드는 응답에서만 확인하고 로그에 출력하지 않는다(R4).

## Edge Cases

- 통합 테스트에서 TOTP 미등록 운영자 생성 후 재발급 시도: `admin_operator_totp` row 없음 → 404.
- 재발급 후 이전 코드 사용: Argon2id 검증 실패 → 401.

## Failure Scenarios

- 통합 테스트 DB 연결 실패: `AbstractIntegrationTest` 공유 컨테이너 사용으로 방지.

## Test Requirements

### 통합 테스트
- `RecoveryCodeRegenerateIntegrationTest`: 5개 시나리오 (200 성공, 이전 코드 무효화, 새 코드 성공, 404 미등록, 401 비인증)

## Definition of Done

- [ ] `platform/error-handling.md`에 `TOTP_NOT_ENROLLED` 등록 완료
- [ ] 통합 테스트 `RecoveryCodeRegenerateIntegrationTest` 작성 및 5개 시나리오 통과
- [ ] `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL
- [ ] 코드 리뷰 통과
- [ ] `tasks/review/`로 이동 완료
