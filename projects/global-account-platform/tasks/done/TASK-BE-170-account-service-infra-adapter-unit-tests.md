# TASK-BE-170: account-service 인프라 어댑터 단위 테스트

## Goal
account-service 의 미테스트 인프라 어댑터 2개에 대한 단위 테스트를 작성한다.

## Scope
- `AuthServiceClient` (WireMock 기반) — 회원가입 크리티컬 패스, 409/4xx/네트워크 오류 → 예외 매핑
- `RedisEmailVerificationTokenStore` (Mockito 기반) — dual fail-mode (token ops fail-closed, resend rate-limit fail-open)

## Acceptance Criteria
- [ ] `AuthServiceClientTest` — createCredential 200(정상), 409→CredentialAlreadyExistsConflict, 기타4xx→AuthServiceUnavailable, 네트워크오류→AuthServiceUnavailable
- [ ] `RedisEmailVerificationTokenStoreTest` — save/findAccountId/delete 정상, save Redis오류 전파(fail-closed), tryAcquireResendSlot true/false/null/Redis오류→true(fail-open)
- [ ] 컴파일 및 테스트 통과

## Related Specs
- `specs/services/account-service/architecture.md`

## Related Contracts
- `specs/contracts/http/internal/auth-internal.md`

## Edge Cases
- `AuthServiceClient`: 409 → `CredentialAlreadyExistsConflict` (계약 위반이 아닌 정상 비즈니스 예외)
- `AuthServiceClient`: 기타 4xx → `AuthServiceUnavailable` (계약 위반으로 간주, 회원가입 롤백)
- `AuthServiceClient`: 네트워크 오류는 resilience4j retry 2회 후 `AuthServiceUnavailable` — 테스트 약 1.5s
- `RedisEmailVerificationTokenStore.tryAcquireResendSlot`: null 반환 → false (Boolean.TRUE.equals(null))
- `RedisEmailVerificationTokenStore.tryAcquireResendSlot`: DataAccessException → true (fail-open, 가용성 우선)

## Failure Scenarios
- `AuthServiceClient`: 모든 비-4xx 오류는 `AuthServiceUnavailable` 로 변환되어 회원가입 트랜잭션 롤백
- `RedisEmailVerificationTokenStore`: token ops(save/findAccountId/delete) 는 예외 전파 (fail-closed)
