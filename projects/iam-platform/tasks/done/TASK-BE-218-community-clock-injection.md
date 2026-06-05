---
id: TASK-BE-218
title: "community-service FeedSubscription Clock 주입 — followedAt 테스트 결정론화"
type: refactoring
status: ready
service: community-service
---

## Goal

`FeedSubscription.create()`가 `Instant.now()`를 직접 호출하므로 `FollowArtistUseCaseTest`에서 `followedAt` 값을 정확히 검증할 수 없다 (`isNotNull()` 수준에 그침). `FollowArtistUseCase`에 `Clock`을 주입하고 use-case 레벨에서 `clock.instant()`를 호출해 domain 팩토리에 명시적 타임스탬프를 전달하도록 변경한다.

## Scope

### 추가
- `infrastructure/config/ClockConfig.java` — `Clock.systemUTC()` 빈 등록

### 수정
- `domain/feed/FeedSubscription.java`
  - `create(String fanAccountId, String artistAccountId)` → `create(String fanAccountId, String artistAccountId, Instant followedAt)`
  - 내부 `Instant.now()` 호출 제거

- `application/FollowArtistUseCase.java`
  - 생성자에 `Clock clock` 주입 추가
  - `follow()` 내에서 `clock.instant()`로 타임스탬프 획득 후 `FeedSubscription.create(..., now)` 호출

### 수정 (테스트)
- `application/FollowArtistUseCaseTest.java`
  - `Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneOffset.UTC)` 사용
  - `useCase = new FollowArtistUseCase(subscriptionRepository, artistAccountChecker, fixedClock)` 로 변경
  - `follow_validPair_savesSubscription` — `result.followedAt()` 를 `isEqualTo(Instant.parse("2026-04-30T00:00:00Z"))` 로 정확히 검증

## Acceptance Criteria

- [ ] `ClockConfig.java`에서 `Clock.systemUTC()` 빈 등록
- [ ] `FeedSubscription.create()` 시그니처가 `Instant followedAt` 파라미터를 받음
- [ ] `FollowArtistUseCase`에 `Clock` 주입됨
- [ ] `FollowArtistUseCaseTest`에서 `followedAt`을 고정 시각으로 정확히 검증
- [ ] `./gradlew :apps:community-service:test` 통과

## Related Specs

- `specs/services/community-service/architecture.md`

## Related Contracts

- 없음

## Edge Cases

- `FeedSubscription.create()` 시그니처 변경으로 기존 호출 코드(`FollowArtistUseCase`) 외 다른 호출처가 없는지 확인
- 통합 테스트에서 `FeedSubscription.create()`를 직접 호출하는 경우 컴파일 에러 발생 — 모두 수정

## Failure Scenarios

- Clock 빈 미등록 시 `NoSuchBeanDefinitionException` — `ClockConfig`가 정상 등록되어야 함
