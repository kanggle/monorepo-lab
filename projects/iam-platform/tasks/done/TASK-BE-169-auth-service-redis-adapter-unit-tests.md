# TASK-BE-169: auth-service Redis 어댑터 단위 테스트

## Goal
auth-service 의 Redis 인프라 어댑터 4개에 대한 단위 테스트를 작성한다.

## Scope
- `RedisPasswordResetTokenStore` — 비밀번호 재설정 토큰 저장소 (fail-closed: 예외 전파)
- `RedisBulkInvalidationStore` — 전체 토큰 무효화 마커 (isInvalidated/getInvalidatedAt: fail-closed)
- `RedisLoginAttemptCounter` — 로그인 실패 카운터 (fail-open: 0 반환)
- `RedisTokenBlacklist` — 토큰 블랙리스트 (isBlacklisted: fail-closed, blacklist: fail-open)

## Acceptance Criteria
- [ ] `RedisPasswordResetTokenStoreTest` — save/findAccountId/delete 정상, save Redis오류 전파
- [ ] `RedisBulkInvalidationStoreTest` — invalidateAll(정상/오류흡수), isInvalidated(존재/없음/오류→true), getInvalidatedAt(정상/빈/malformed/오류→closed)
- [ ] `RedisLoginAttemptCounterTest` — getFailureCount(존재/없음/오류→0), increment(정상/오류흡수), reset(정상/오류흡수)
- [ ] `RedisTokenBlacklistTest` — blacklist(정상/오류흡수), isBlacklisted(있음/없음/오류→true)
- [ ] 컴파일 및 테스트 통과

## Related Specs
- `specs/services/auth-service/architecture.md`

## Related Contracts
- 없음 (내부 어댑터)

## Edge Cases
- `RedisPasswordResetTokenStore`: fail-closed — Redis 예외가 그대로 전파됨
- `RedisBulkInvalidationStore.getInvalidatedAt`: NumberFormatException → Optional.of(Instant.now()) (fail-closed)
- 모든 catch 블록은 `DataAccessException` (not generic Exception)

## Failure Scenarios
- `DataAccessException` 포착 시 어댑터별 fail-safe 값 반환 또는 예외 전파
