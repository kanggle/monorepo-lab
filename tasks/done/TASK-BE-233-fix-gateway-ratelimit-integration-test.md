# TASK-BE-233: fix — GatewayRateLimitIntegrationTest tenant_id + key prefix 수정

## Goal

TASK-BE-230 2차 리뷰에서 발견된 이슈 수정.
`GatewayRateLimitIntegrationTest`가 TASK-BE-230 변경사항(tenant_id claim 필수, rate-limit key prefix `rate:`)과 정합되지 않아 통합 테스트가 잘못된 전제로 동작함.

Fix issue found in TASK-BE-230.

## Scope

**In:**
- `apps/gateway-service/src/test/java/com/example/gateway/integration/GatewayRateLimitIntegrationTest.java`
  - `createValidToken("account-123")` → tenant_id claim 포함하는 2-arg 버전으로 수정
  - `@BeforeEach` cleanup: `ratelimit:*` → `rate:*`
  - 수동 cleanup: `ratelimit:login:*` → `rate:login:*`
  - 잘못된 rate-limit 검증 로직 수정

**Out:**
- rate-limit 로직 변경 없음
- 다른 테스트 파일 변경 없음

## Acceptance Criteria

- [ ] `GatewayRateLimitIntegrationTest` 전체 통과
- [ ] cleanup이 `rate:*` 패턴 사용
- [ ] JWT 생성 시 `tenant_id` claim 포함
- [ ] `./gradlew :apps:gateway-service:test` BUILD SUCCESSFUL

## Related Specs

- `specs/contracts/http/gateway-api.md`
- `specs/services/gateway-service/architecture.md`

## Related Contracts

- `specs/contracts/http/gateway-api.md`

## Edge Cases

- 기존 `ratelimit:` prefix로 남아있는 키가 있어도 테스트 간 간섭 없음 (cleanup이 `rate:*`로 변경되므로)

## Failure Scenarios

- 테스트 실패 시 로그에서 assertion 내용 확인
