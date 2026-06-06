# TASK-BE-232: fix — gateway rate-limit Redis 키 prefix 수정

## Goal

TASK-BE-230 리뷰에서 발견된 이슈 수정.
`TokenBucketRateLimiter`가 Redis 키를 `ratelimit:{scope}:{identifier}:{windowSeconds}` 패턴으로 생성하고 있으나, `gateway-api.md` spec은 `rate:{scope}:{tenant_id}:{ip}` 패턴을 요구한다. prefix를 `rate:`로 변경하고 관련 테스트를 정합시킨다.

## Scope

**In:**
- `apps/gateway-service/src/main/java/com/example/gateway/ratelimit/TokenBucketRateLimiter.java` — 키 prefix `ratelimit:` → `rate:`, window suffix 제거
- `apps/gateway-service/src/test/java/com/example/gateway/filter/RateLimitFilterUnitTest.java` — key assertion 수정
- `apps/gateway-service/src/test/java/com/example/gateway/integration/GatewayTenantPropagationIntegrationTest.java` — key pattern assertion 수정
- `specs/contracts/http/gateway-api.md` — key pattern 명세 확인 (이미 올바른 경우 변경 불필요)

**Out:**
- rate-limit 로직 변경 없음 (prefix만 변경)
- 다른 서비스 변경 없음

## Acceptance Criteria

- [ ] Redis key 형식이 `rate:{scope}:{tenant_id}:{identifier}` 패턴으로 생성됨
- [ ] `RateLimitFilterUnitTest` 전체 통과
- [ ] `GatewayTenantPropagationIntegrationTest` key assertion 정합
- [ ] `gateway-api.md`에 기술된 key pattern과 구현이 일치

## Related Specs

- `specs/contracts/http/gateway-api.md`
- `specs/services/gateway-service/architecture.md`

## Related Contracts

- `specs/contracts/http/gateway-api.md`

## Edge Cases

- 기존 Redis에 `ratelimit:` prefix로 저장된 키: 만료 대기 (rate limit은 단기 TTL이므로 자연 소멸)

## Failure Scenarios

- 키 변경 후 카운터가 초기화되는 효과 발생 → 의도된 동작 (이전 잘못된 키는 버림)
