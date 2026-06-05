---
id: TASK-BE-176
title: "community-service AccountProfileClient·AccountAuthenticationFilter 단위 테스트"
status: ready
type: BE
service: community-service
priority: medium
---

## Goal

`AccountProfileClient`와 `AccountAuthenticationFilter`의 단위 테스트를 작성한다.
두 클래스 모두 현재 테스트가 전혀 없다.

- `AccountProfileClient`: fail-silent 동작(예외 → null 반환)이 핵심 계약이며 이를 단위 테스트로 문서화
- `AccountAuthenticationFilter`: JWT 검증 → SecurityContext 설정 흐름, 헤더 없음/토큰 무효/sub 없음 → 401 응답을 검증

## Scope

- `apps/community-service/src/test/java/com/example/community/infrastructure/client/AccountProfileClientUnitTest.java` (신규)
- `apps/community-service/src/test/java/com/example/community/infrastructure/security/AccountAuthenticationFilterUnitTest.java` (신규)

## Acceptance Criteria

- `AccountProfileClientUnitTest`:
  - 200 응답 → displayName 반환
  - 4xx/5xx/네트워크 오류 → null 반환 (fail-silent)
  - null accountId 입력 → null 반환 (HTTP 호출 없음)

- `AccountAuthenticationFilterUnitTest`:
  - 유효한 토큰 + sub 포함 → SecurityContext에 ActorContext 설정 후 체인 통과
  - Authorization 헤더 없음 → 401
  - `Bearer ` 로 시작하지 않는 헤더 → 401
  - 서명 검증 실패(JwtVerificationException) → 401
  - sub claim 없음 → 401
  - `/api/community/` 로 시작하지 않는 경로 → 필터 바이패스(체인 통과)
  - roles 클레임 배열 → ROLE_ 권한 설정

- 모든 테스트 통과
- `./gradlew :apps:community-service:test` 성공

## Related Specs

- `specs/services/community-service/architecture.md`

## Related Contracts

- (없음 — 내부 HTTP client 및 보안 필터)

## Edge Cases

- `AccountProfileClient`는 `@Cacheable`을 사용하지만 단위 테스트는 Spring 컨텍스트 없음 → 캐시 없이 직접 HTTP 호출
- `AccountProfileClient`는 HTTP_1_1 명시 → WireMock H2C 처리 불필요
- `AccountAuthenticationFilter.shouldNotFilter()`: path가 `/api/community/`로 시작하지 않으면 true → 필터 스킵
- SecurityContext는 `doFilterInternal` finally 블록에서 반드시 clear → 테스트에서 @AfterEach로 clear
- 필터 실행 중 SecurityContext 캡처: 람다 FilterChain으로 AtomicReference에 저장

## Failure Scenarios

- AccountProfileClient: 예외 발생 시 warn 로그 후 null 반환
- AccountAuthenticationFilter: 401 응답, body `{"code":"TOKEN_INVALID",...}`
