# TASK-BE-168: security-service Redis 어댑터 단위 테스트

## Goal
security-service 의 Redis 인프라 어댑터 4개에 대한 단위 테스트를 작성한다.
각 어댑터는 Spring Data Redis 를 사용하며, Redis 장애 시 fail-open/graceful-degradation 동작을 갖는다.

## Scope
- `RedisEventDedupStore` — 이벤트 중복 처리 감지 (fail-open)
- `RedisVelocityCounter` — 로그인 시도 빈도 카운터 (fail-open, returns 0)
- `RedisKnownDeviceStore` — 기기 지문 저장소 (fail-open, returns true)
- `RedisLastKnownGeoStore` — 마지막 위치 스냅샷 저장소 (fail-open, returns empty)

## Acceptance Criteria
- [ ] `RedisEventDedupStoreTest` — isDuplicate 정상/miss/Redis오류, markProcessed 정상/Redis오류
- [ ] `RedisVelocityCounterTest` — incrementAndGet 첫번째/누적/Redis오류, peek 정상/null/Redis오류
- [ ] `RedisKnownDeviceStoreTest` — isKnown 알려진/미지/Redis오류(true반환), remember 정상/Redis오류
- [ ] `RedisLastKnownGeoStoreTest` — get 정상/empty/필드누락/Redis오류, put 정상/Redis오류
- [ ] 컴파일 및 테스트 통과

## Related Specs
- `specs/services/security-service/architecture.md`

## Related Contracts
- 없음 (내부 어댑터)

## Edge Cases
- Redis hasKey/isMember 가 null 반환 시 false 로 처리
- incrementAndGet: value==1 일 때만 expire 호출
- peek: null 값은 0 반환
- get: 하나라도 필드 누락 시 Optional.empty()

## Failure Scenarios
- 모든 Redis 오류는 catch(Exception) 로 흡수하고 각 어댑터별 fail-safe 값 반환
