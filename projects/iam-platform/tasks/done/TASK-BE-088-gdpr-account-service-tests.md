---
id: TASK-BE-088
title: "GDPR account-service 테스트 — GdprDeleteUseCase, DataExportUseCase, GdprController slice"
status: ready
area: backend
service: account-service
---

## Goal

TASK-BE-054에서 구현된 account-service GDPR 기능(GdprDeleteUseCase, DataExportUseCase, GdprController)에 대한 단위 테스트 및 controller slice 테스트를 추가한다. 현재 해당 클래스들에 대한 테스트가 전혀 없어 platform/testing-strategy.md 요구사항을 충족하지 못하고 있다.

## Scope

### In

- `GdprDeleteUseCaseTest` (단위 테스트)
  - 정상 케이스: ACTIVE → DELETED 전이, 이메일 SHA-256 해시 교체(`gdpr_<hash>@deleted.local`), email_hash 저장, 프로필 PII NULL 처리, account.deleted(anonymized=true) 이벤트 발행
  - 예외 케이스: DELETED 상태 계정 → StateTransitionException, 계정 미존재 → AccountNotFoundException
  - 프로필 미존재 계정: 계정만 처리(프로필 마스킹 스킵)
- `DataExportUseCaseTest` (단위 테스트)
  - 정상 케이스: 계정+프로필 데이터 반환
  - 프로필 미존재: profile 필드가 null인 결과 반환
  - 계정 미존재: AccountNotFoundException
- `GdprControllerTest` (controller slice 테스트, @WebMvcTest)
  - `POST /internal/accounts/{accountId}/gdpr-delete` 200 정상, 400 STATE_TRANSITION_INVALID, 404 ACCOUNT_NOT_FOUND
  - `GET /internal/accounts/{accountId}/export` 200 정상, 404 ACCOUNT_NOT_FOUND

### Out

- 통합 테스트(Testcontainers DB 연동) — 별도 태스크로 추후 대응
- admin-service 테스트 (TASK-BE-089)
- 신규 기능 구현 변경

## Acceptance Criteria

- [ ] `GdprDeleteUseCaseTest` 작성 — mock 기반, Spring 컨텍스트 없음
  - [ ] 정상 처리: accountRepository.save 호출, emailHash가 sha256(originalEmail), maskedEmail이 `gdpr_<hash>@deleted.local` 형식, profileRepository.save 호출, 이벤트 발행 2회(status_changed + account_deleted_anonymized)
  - [ ] DELETED 계정 재요청 시 StateTransitionException throw
  - [ ] 존재하지 않는 accountId 시 AccountNotFoundException throw
  - [ ] 프로필 없는 계정: profileRepository.findByAccountId가 empty → profileRepository.save 미호출, 계정 처리는 정상 완료
- [ ] `DataExportUseCaseTest` 작성 — mock 기반, Spring 컨텍스트 없음
  - [ ] 계정+프로필 존재: ProfileData 포함 DataExportResult 반환
  - [ ] 계정 존재/프로필 미존재: profile=null인 DataExportResult 반환
  - [ ] 계정 미존재: AccountNotFoundException throw
- [ ] `GdprControllerTest` 작성 — @WebMvcTest, SecurityConfig 포함
  - [ ] POST .../gdpr-delete 200: GdprDeleteResponse JSON 확인(accountId, status="DELETED", maskedAt)
  - [ ] POST .../gdpr-delete 409: StateTransitionException → STATE_TRANSITION_INVALID
  - [ ] POST .../gdpr-delete 404: AccountNotFoundException → ACCOUNT_NOT_FOUND
  - [ ] GET .../export 200: DataExportResponse JSON 확인(accountId, email, status, profile)
  - [ ] GET .../export 404: AccountNotFoundException → ACCOUNT_NOT_FOUND
- [ ] `./gradlew :apps:account-service:test` 성공 (기존 테스트 회귀 없음)

## Related Specs

- specs/features/data-rights.md
- specs/services/account-service/architecture.md
- platform/testing-strategy.md

## Related Contracts

- specs/contracts/http/internal/admin-to-account.md

## Edge Cases

- SHA-256 해시 검증: `GdprDeleteResult.emailHash()`가 정확히 64자 16진수 문자열인지 assert
- 이미 DELETED 상태 계정에 GdprDeleteUseCase.execute 호출 → StatusMachine이 StateTransitionException throw
- 프로필 없는 계정의 DataExportUseCase: exportedAt 타임스탬프가 non-null인지 확인

## Failure Scenarios

- GdprDeleteUseCase 내부에서 SHA-256 알고리즘 unavailable → RuntimeException (테스트 대상 아님, 플랫폼 보증)
- accountRepository.save 실패 시 트랜잭션 롤백 (단위 테스트 범위 외)

## Test Requirements

- 테스트 클래스 위치: `apps/account-service/src/test/java/com/example/account/`
  - 단위 테스트: `application/service/GdprDeleteUseCaseTest.java`, `application/service/DataExportUseCaseTest.java`
  - Controller slice: `presentation/GdprControllerTest.java`
- 단위 테스트: Spring 컨텍스트 없이 Mockito mock만 사용
- Controller slice: `@WebMvcTest({GdprController.class})` + `@Import({SecurityConfig.class, GlobalExceptionHandler.class})` + `@MockitoBean`(GdprDeleteUseCase, DataExportUseCase)
- `@DisplayName`에 한국어 설명 사용
- 테스트 메서드 명명: `{scenario}_{condition}_{expectedResult}` 패턴
