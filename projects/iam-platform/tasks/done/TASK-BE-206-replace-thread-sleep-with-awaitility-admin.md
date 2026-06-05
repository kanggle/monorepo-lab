# TASK-BE-206: Thread.sleep → Awaitility 교체 — admin-service

## Goal

admin-service 테스트 2개 파일의 `Thread.sleep` 호출을 `Awaitility` poll로 교체하여
CI 환경 타이밍 의존 flakiness를 제거한다.

## Scope

### admin-service (2 files)

**1. PermissionEvaluatorCacheTest.java**
- 파일: `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/rbac/PermissionEvaluatorCacheTest.java`
- 현재: `Thread.sleep(Duration.ofMillis(2_200).toMillis())` — Redis TTL(2s) 만료 대기
- 교체: `await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200)).untilAsserted(...)` 로 TTL 만료 감지

**2. SecurityServiceClientCircuitBreakerTest.java**
- 파일: `apps/admin-service/src/test/java/com/example/admin/infrastructure/client/SecurityServiceClientCircuitBreakerTest.java`
- 현재: `Thread.sleep(300)` — CB OPEN → HALF_OPEN 자동 전환 대기 (waitDurationInOpenState=200ms)
- 교체: `await().atMost(Duration.ofMillis(500)).until(() -> circuitBreaker.getState() != OPEN)` 으로 상태 감지

### 비대상 (community-service 2ms 마이크로슬립)

`PostRepositoryIntegrationTest`, `PostStatusHistoryJpaRepositoryIntegrationTest` 의 2ms sleep은
`PostStatusHistoryJpaEntity.record()` 내부 `Instant.now()` 하드코딩으로 인해 Awaitility 적용 불가.
타임스탬프 순서 보장 용도이며 flakiness 위험 없음 — 별도 태스크에서 팩토리 메서드 수정 시 처리.

## Acceptance Criteria

- [ ] `PermissionEvaluatorCacheTest.reloadsAfterTtlExpiry` — `Thread.sleep` 제거, `throws InterruptedException` 제거, Awaitility poll로 교체
- [ ] `SecurityServiceClientCircuitBreakerTest.recovers_to_closed_after_half_open_success` — `Thread.sleep(300)` 제거, `throws InterruptedException` 제거, Awaitility poll로 교체
- [ ] 두 파일 모두 `import static org.awaitility.Awaitility.await;` 추가
- [ ] `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL

## Related Specs

- `platform/testing-strategy.md`

## Related Contracts

없음

## Edge Cases

- Awaitility는 admin-service `build.gradle`에 이미 `testImplementation 'org.awaitility:awaitility'` 로 선언되어 있음 — 의존성 추가 불필요
- `verify(origin, times(2))` 는 Awaitility `untilAsserted` 내부에서도 정확히 동작: TTL 만료 전 폴링은 cache hit이므로 origin 미호출, TTL 만료 후 첫 폴에서 origin 호출 → total 2회

## Failure Scenarios

- Awaitility `atMost` 이 너무 짧으면 TTL 만료 전 timeout — `atMost(Duration.ofSeconds(5))` 로 2s TTL + 충분한 여유 보장
- CB 전환 타이밍: `waitDurationInOpenState=200ms`, `atMost(500ms)` 으로 2.5배 여유 확보
