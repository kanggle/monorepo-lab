# TASK-BE-152 — membership-service 테스트 커버리지 보강

## Goal

`specs/services/membership-service/architecture.md`의 Testing Expectations 6개 레이어 중
현재 누락된 4개 영역을 채운다.

- Unit: `GetMySubscriptionsUseCase`, `CheckContentAccessUseCase` 단위 테스트 없음
- Repository slice: `MembershipPlanJpaRepository.findByPlanLevel()`,
  `ContentAccessPolicyJpaRepository.findByVisibilityKey()` 쿼리 테스트 없음
- Integration scenario: 스펙 필수 시나리오 "EXPIRED 구독 재활성화 시 신규 구독 생성" 누락

## Scope

**추가 파일 (5개)**

| # | 경로 | 레이어 |
|---|------|--------|
| 1 | `apps/membership-service/src/test/…/application/GetMySubscriptionsUseCaseTest.java` | Unit |
| 2 | `apps/membership-service/src/test/…/application/CheckContentAccessUseCaseTest.java` | Unit |
| 3 | `apps/membership-service/src/test/…/infrastructure/persistence/MembershipPlanJpaRepositoryTest.java` | Repository slice |
| 4 | `apps/membership-service/src/test/…/infrastructure/persistence/ContentAccessPolicyJpaRepositoryTest.java` | Repository slice |
| 5 | `apps/membership-service/src/test/…/integration/SubscriptionReactivationIntegrationTest.java` | Integration |

**변경 없음**: 기존 테스트 파일, 프로덕션 코드, 계약서, 스펙.

## Acceptance Criteria

- [ ] `GetMySubscriptionsUseCaseTest`: 구독 없음 → effectivePlanLevel=FREE / ACTIVE 구독 1개 → FAN_CLUB / ACTIVE+EXPIRED 혼재 → ACTIVE만 반영
- [ ] `CheckContentAccessUseCaseTest`: ACTIVE FAN_CLUB 구독 + FAN_CLUB 요청 → allowed=true / 구독 없음 + FAN_CLUB 요청 → allowed=false / ACTIVE FAN_CLUB + FREE 요청 → allowed=true / ACTIVE FAN_CLUB + FAN_CLUB 요청(경계) → allowed=true
- [ ] `MembershipPlanJpaRepositoryTest`: `findByPlanLevel(FAN_CLUB)` 기존 데이터 반환 / 존재하지 않는 레벨 → empty
- [ ] `ContentAccessPolicyJpaRepositoryTest`: `findByVisibilityKey(existing-key)` 반환 / 없는 키 → empty
- [ ] `SubscriptionReactivationIntegrationTest`: EXPIRED 구독 보유 계정 → 재활성화 요청 → 201 신규 구독 생성 / EXPIRED 행 유지 + 신규 ACTIVE 행 생성 검증
- [ ] `./gradlew :apps:membership-service:test` BUILD SUCCESSFUL (Docker 없으면 integration 테스트 SKIP)

## Related Specs

- `specs/services/membership-service/architecture.md` — Testing Expectations, 필수 시나리오

## Related Contracts

없음 (테스트 전용 — 계약 변경 없음)

## Edge Cases

- `GetMySubscriptionsUseCase.getMine()`: ACTIVE 구독이 여러 플랜 레벨일 때 가장 높은 rank 반환
- `CheckContentAccessUseCase.check()`: EXPIRED 구독은 ACTIVE 목록에서 제외되므로 FREE로 fallback
- `MembershipPlanJpaRepository`: `findByPlanLevel` 은 DataInitializer 또는 SQL insert로 사전 데이터 필요
- `ContentAccessPolicyJpaRepository`: `findByVisibilityKey` 는 visibility_key unique 제약 존재 가정
- `SubscriptionReactivationIntegrationTest`: EXPIRED 구독 행이 DB에 남아 있을 때 `findByAccountIdAndPlanLevelAndStatus(ACTIVE)` 가 empty 반환 → 신규 생성 경로 진입 확인

## Failure Scenarios

- Repository slice 테스트가 Docker 미설치 환경에서 실행 → `@EnabledIf("isDockerAvailable")` 로 SKIP
- Integration 테스트 MySQL 컨테이너 기동 실패 → Testcontainers 예외로 테스트 SKIP (ABORTED)
- EXPIRED 구독 재활성화 시 `SubscriptionAlreadyActiveException` 이 잘못 던져지는 버그 발견 → 태스크 내 버그 수정 포함 허용
