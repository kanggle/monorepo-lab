---
id: TASK-BE-089
title: "GDPR admin-service 테스트 — GdprAdminUseCase, AdminGdprController slice"
status: ready
area: backend
service: admin-service
---

## Goal

TASK-BE-054에서 구현된 admin-service GDPR 기능(GdprAdminUseCase, AdminGdprController)에 대한 단위 테스트 및 controller slice 테스트를 추가한다. 현재 해당 클래스들에 대한 테스트가 전혀 없어 platform/testing-strategy.md 요구사항을 충족하지 못하고 있다.

## Goal

GDPR admin-service의 두 클래스에 대한 테스트 커버리지를 확보한다:
1. `GdprAdminUseCaseTest` — mock 기반 단위 테스트
2. `AdminGdprControllerTest` — @WebMvcTest controller slice 테스트

## Scope

### In

- `GdprAdminUseCaseTest` (단위 테스트)
  - `gdprDelete`: 정상 처리(감사 start→completion 기록, downstream 호출, GdprDeleteResult 반환), reason 누락 → ReasonRequiredException, downstream DownstreamFailureException → FAILURE 감사 기록 후 재throw, circuit breaker OPEN → FAILURE 감사 기록 후 재throw
  - `dataExport`: 정상 처리(감사 기록, downstream 호출, DataExportResult 반환), reason 누락 → ReasonRequiredException, downstream 오류 → FAILURE 감사 기록
- `AdminGdprControllerTest` (controller slice, @WebMvcTest)
  - `POST /api/admin/accounts/{accountId}/gdpr-delete`: 200 정상(Idempotency-Key 필수, X-Operator-Reason 또는 body.reason), 400 REASON_REQUIRED, 403 PERMISSION_DENIED(권한 없는 역할), 500 AUDIT_FAILURE
  - `GET /api/admin/accounts/{accountId}/export`: 200 정상, 400 REASON_REQUIRED(헤더 없음), 403 PERMISSION_DENIED

### Out

- account-service 테스트 (TASK-BE-088)
- 통합 테스트(Testcontainers DB 연동) — 별도 태스크로 추후 대응
- 신규 기능 구현 변경

## Acceptance Criteria

- [ ] `GdprAdminUseCaseTest` 작성 — mock 기반, Spring 컨텍스트 없음
  - [ ] gdprDelete 정상: auditor.recordStart, accountServiceClient.gdprDelete, auditor.recordCompletion(SUCCESS) 순으로 호출됨
  - [ ] gdprDelete reason 누락: ReasonRequiredException throw, accountServiceClient 미호출
  - [ ] gdprDelete DownstreamFailureException: auditor.recordCompletion(FAILURE) 호출 후 예외 재throw
  - [ ] gdprDelete CallNotPermittedException: auditor.recordCompletion(FAILURE, "CIRCUIT_OPEN:...") 호출 후 예외 재throw
  - [ ] dataExport 정상: auditor.record(SUCCESS), DataExportResult 반환 확인
  - [ ] dataExport reason 누락: ReasonRequiredException throw
  - [ ] dataExport DownstreamFailureException: auditor.record(FAILURE) 호출 후 예외 재throw
- [ ] `AdminGdprControllerTest` 작성 — @WebMvcTest 패턴 (BulkLockControllerTest 패턴 참조)
  - [ ] POST gdpr-delete 200: `Idempotency-Key` 헤더 포함, `X-Operator-Reason` 헤더로 reason 전달, GdprDeleteResponse JSON 확인(accountId, status, maskedAt, auditId)
  - [ ] POST gdpr-delete 200: reason을 body.reason으로 전달해도 동일하게 처리
  - [ ] POST gdpr-delete 400: Idempotency-Key 헤더 누락 → 400
  - [ ] POST gdpr-delete 400: reason 없음(헤더+body 모두) → REASON_REQUIRED
  - [ ] POST gdpr-delete 403: AUDIT_READ 권한만 있는 역할 → PERMISSION_DENIED
  - [ ] GET export 200: `X-Operator-Reason` 헤더 포함, DataExportResponse JSON 확인(profile 포함/미포함 두 케이스)
  - [ ] GET export 400: `X-Operator-Reason` 헤더 없음 → REASON_REQUIRED
  - [ ] GET export 403: ACCOUNT_LOCK 권한만 있는 역할 → PERMISSION_DENIED
- [ ] `./gradlew :apps:admin-service:test` 성공 (기존 테스트 회귀 없음)

## Related Specs

- specs/features/data-rights.md
- specs/services/admin-service/architecture.md
- platform/testing-strategy.md

## Related Contracts

- specs/contracts/http/admin-api.md
- specs/contracts/http/internal/admin-to-account.md

## Edge Cases

- X-Operator-Reason 헤더와 body.reason 모두 제공 시: 헤더 우선(resolveReason 로직)
- profile이 null인 DataExportResponse: export 응답에서 profile 필드 null 허용
- CallNotPermittedException 감사 메시지: "CIRCUIT_OPEN: " prefix 포함 여부 assert

## Failure Scenarios

- 감사 기록(auditor) 실패 시 → 컨트롤러가 500 AUDIT_FAILURE 반환 (AdminExceptionHandler 경유)
- AccountServiceClient circuit breaker OPEN: 503 반환 (AdminExceptionHandler 경유)

## Test Requirements

- 테스트 클래스 위치: `apps/admin-service/src/test/java/com/example/admin/`
  - 단위 테스트: `application/GdprAdminUseCaseTest.java`
  - Controller slice: `presentation/AdminGdprControllerTest.java`
- 단위 테스트: Spring 컨텍스트 없이 Mockito mock만 사용
- Controller slice: `@WebMvcTest(controllers = AdminGdprController.class)` + `@ImportAutoConfiguration(AopAutoConfiguration.class)` + `@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class, RequiresPermissionAspect.class, ...JwtBeans...})` — BulkLockControllerTest 구조 참조
- `OperatorJwtTestFixture` 활용하여 SUPER_ADMIN / SUPPORT_LOCK / SECURITY_ANALYST 역할별 JWT 생성
- `@DisplayName`에 한국어 설명 사용
- 테스트 메서드 명명: `{scenario}_{condition}_{expectedResult}` 패턴
