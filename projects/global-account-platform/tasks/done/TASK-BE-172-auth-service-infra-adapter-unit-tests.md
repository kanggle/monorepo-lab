# TASK-BE-172: auth-service 잔여 인프라 어댑터 단위 테스트 추가

## Goal
auth-service 내 테스트 파일이 없는 인프라 어댑터 4개에 대해 `Unit (infrastructure)` 수준의 단위 테스트를 작성한다.

## Target Service
`apps/auth-service`

## Scope
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/client/AccountServiceClient.java`
  - WireMock 기반 HTTP 동작 테스트 (getAccountStatus, socialSignup)
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth/KakaoOAuthClient.java`
  - WireMock 기반 2단계 OAuth 플로우 테스트 (token exchange + user info)
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/jwt/JwksEndpointProvider.java`
  - 순수 단위 테스트 (RSA key → JWKS JSON 구조 검증)
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/email/Slf4jEmailSender.java`
  - 순수 단위 테스트 (로그 캡처 — 토큰 미로깅, 이메일 마스킹)

## Acceptance Criteria
- [ ] `AccountServiceClientUnitTest`: getAccountStatus(200/404/422/network), socialSignup(200/4xx/network) — 7개 테스트
- [ ] `KakaoOAuthClientUnitTest`: 정상 플로우, access_token 누락, token endpoint 5xx, user info endpoint 5xx, 네트워크 오류 — 5개 테스트
- [ ] `JwksEndpointProviderUnitTest`: JWKS 구조 검증, n/e Base64Url + leading zero 제거 — 2개 테스트
- [ ] `Slf4jEmailSenderUnitTest`: 정상, 토큰 미로깅, 이메일 마스킹, null 이메일 — 4개 테스트
- [ ] 전체 18개 테스트 통과
- [ ] 파일 이름이 `*UnitTest.java` 규칙 준수 (`platform/testing-strategy.md` Unit (infrastructure))

## Related Specs
- `platform/testing-strategy.md` (Naming Conventions, Unit Tests)
- `specs/services/auth-service/architecture.md`

## Related Contracts
- None

## Edge Cases
- `AccountServiceClient`는 `ResilienceClientFactory.buildRestClient`가 JDK HttpClient(HTTP/2)를 사용하므로 WireMock과 H2C 충돌 발생 → `ReflectionTestUtils.setField`로 HTTP/1.1 전용 RestClient 교체 필요
- `KakaoOAuthClient`는 `RestClient.builder().build()`를 내부에서 생성하므로 props에 WireMock URL을 직접 주입
- `JwksEndpointProvider.base64UrlEncode`의 leading zero 제거 로직은 BigInteger decode 후 비교로 간접 검증
- `Slf4jEmailSender`는 반환값 없이 로그만 출력 → logback `ListAppender`로 캡처

## Failure Scenarios
- WireMock H2C RST_STREAM 문제 발생 시 `KakaoOAuthClient`도 HTTP/1.1 RestClient 교체 적용
- `Slf4jEmailSender.maskedEmail`은 private이므로 직접 테스트 불가 — `sendPasswordResetEmail` 로그 출력으로 간접 검증
