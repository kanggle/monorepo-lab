# TASK-BE-173: admin-service 인프라 어댑터 단위 테스트 추가

## Goal
admin-service 내 테스트 파일이 없는 인프라 어댑터 4개에 대해 `Unit (infrastructure)` 수준의 단위 테스트를 작성한다.

## Target Service
`apps/admin-service`

## Scope
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/client/AuthServiceClient.java`
  - WireMock 기반 HTTP 동작 테스트 (forceLogout)
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/security/AdminJwtKeyStore.java`
  - 순수 단위 테스트 (PEM 파싱, 키 사이즈 검증, activeKid 누락 등)
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/security/OperatorContextHolder.java`
  - Spring Security 컨텍스트 모킹 단위 테스트
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/security/RedisTokenBlacklistAdapter.java`
  - Mockito 기반 Redis 단위 테스트 (fail-closed 포함)

## Acceptance Criteria
- [ ] `AuthServiceClientUnitTest`: forceLogout(200/4xx/5xx/network) — 4개 테스트
- [ ] `AdminJwtKeyStoreUnitTest`: valid key, blank PEM, invalid base64, key too small, missing activeKid, empty map — 6개 테스트
- [ ] `OperatorContextHolderUnitTest`: 유효 컨텍스트, auth 없음, 잘못된 principal 타입 — 3개 테스트
- [ ] `RedisTokenBlacklistAdapterUnitTest`: blacklist 정상, blacklist null TTL, isBlacklisted true/false, fail-closed — 5개 테스트
- [ ] 전체 18개 테스트 통과
- [ ] 파일 이름이 `*UnitTest.java` 규칙 준수 (`platform/testing-strategy.md` Unit (infrastructure))

## Related Specs
- `platform/testing-strategy.md` (Naming Conventions, Unit Tests)
- `specs/services/admin-service/architecture.md`

## Related Contracts
- None

## Edge Cases
- `AuthServiceClient`는 생성자에서 `HttpClient.Version.HTTP_1_1`을 명시적으로 세팅하므로 WireMock H2C 이슈 없음 → `ReflectionTestUtils` 불필요
- `AdminJwtKeyStore`는 Spring 컨텍스트 불필요 (순수 Java 파싱 로직); 1024-bit 키로 MIN_RSA_KEY_BITS(2048) 검증 테스트
- `OperatorContextHolder`는 `SecurityContextHolder.clearContext()`로 테스트 간 격리
- `RedisTokenBlacklistAdapter.blacklist`의 null/negative TTL → 1초 폴백 검증
- `RedisTokenBlacklistAdapter.isBlacklisted` fail-closed: Redis 예외 → true 반환

## Failure Scenarios
- `AuthServiceClient` 4xx → `NonRetryableDownstreamException`, 5xx/network → `DownstreamFailureException`
- `AdminJwtKeyStore` 잘못된 PEM → `IllegalStateException` (fail-fast)
- `OperatorContextHolder.require()` auth 없음 또는 principal 타입 불일치 → `OperatorUnauthorizedException`
