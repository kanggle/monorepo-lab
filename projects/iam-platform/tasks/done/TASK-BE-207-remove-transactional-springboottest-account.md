# TASK-BE-207: @Transactional 제거 — AccountEventPublisherIntegrationTest

## Goal

`@SpringBootTest` 통합 테스트에서 데이터 격리 목적으로 사용된 `@Transactional`을
`@BeforeEach` 명시적 cleanup으로 교체한다.

`@SpringBootTest` + `@Transactional` 조합은 테스트 스레드 트랜잭션과 애플리케이션 트랜잭션 경계가
분리되어 `REQUIRES_NEW` 전파 시 롤백이 누락될 수 있는 anti-pattern이다.

## Scope

**1 file:**

- `apps/account-service/src/test/java/com/example/account/integration/AccountEventPublisherIntegrationTest.java`
  - 테스트 메서드 2개에 `@Transactional` 어노테이션 제거
  - `import org.springframework.transaction.annotation.Transactional` 제거
  - `@BeforeEach void cleanOutbox()` 추가: `jdbcTemplate.update("DELETE FROM outbox")` 로 이전 상태 정리

비대상 확인:
- `SignupRollbackIntegrationTest`: 주석에만 @Transactional 언급, 실제 어노테이션 없음

## Acceptance Criteria

- [ ] `AccountEventPublisherIntegrationTest` 두 테스트 메서드에서 `@Transactional` 어노테이션 제거
- [ ] `import org.springframework.transaction.annotation.Transactional` 제거
- [ ] `@BeforeEach void cleanOutbox()` 추가 — `jdbcTemplate.update("DELETE FROM outbox")`
- [ ] `./gradlew :apps:account-service:test --tests "*.AccountEventPublisherIntegrationTest"` BUILD SUCCESSFUL

## Related Specs

- `platform/testing-strategy.md`

## Related Contracts

없음

## Edge Cases

- 각 테스트가 이미 UUID 기반 `accountId`를 사용하므로 cleanup 없이도 간섭 없음.
  그러나 outbox 행이 누적되지 않도록 `@BeforeEach` cleanup 추가
- `@MockitoBean OutboxPollingScheduler`로 인해 outbox 행은 PENDING 상태로 잔류 — DELETE로 안전하게 제거 가능
- `@BeforeEach`가 이미 있는지 확인: 현재 없음 — 신규 추가

## Failure Scenarios

- `@BeforeEach`에서 `DELETE FROM outbox` 실패 시: 테이블명 오타 → `outbox` 테이블이 Flyway migration으로 생성되는지 확인
