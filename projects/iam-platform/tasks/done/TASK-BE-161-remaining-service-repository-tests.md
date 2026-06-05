# TASK-BE-161 — 나머지 서비스 JPA Repository 슬라이스 테스트

## Goal
account-service, security-service, membership-service의 마지막 JPA repository 커버리지 공백을 해소한다.

## Scope
- `ProfileJpaRepository` (account-service) — `findByAccountId`
- `SuspiciousEventJpaRepository` (security-service) — `findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc`
- `SubscriptionStatusHistoryJpaRepository` (membership-service) — save/persist (no custom methods, append-only)

## Acceptance Criteria
- [ ] `ProfileJpaRepositoryTest` — profiles FK 처리, findByAccountId 시나리오 3개
- [ ] `SuspiciousEventJpaRepositoryTest` — 범위 쿼리 + 내림차순 정렬 시나리오 4개
- [ ] `SubscriptionStatusHistoryJpaRepositoryTest` — append-only 저장 시나리오 3개
- [ ] 각 서비스별 DB명 (`account_db`, `security_db`, `membership_db`) 사용
- [ ] `@EnabledIf("isDockerAvailable")` 적용
- [ ] `./gradlew :apps:account-service:compileTestJava` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:security-service:compileTestJava` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:membership-service:compileTestJava` BUILD SUCCESSFUL

## Related Specs
- `specs/services/account-service/architecture.md`
- `specs/services/security-service/architecture.md`
- `specs/services/membership-service/architecture.md`

## Related Contracts
없음

## Edge Cases
- `profiles.account_id` → `accounts(id)` FK: profile 저장 전 account 먼저 생성 필요
- `subscription_status_history` BEFORE DELETE/UPDATE 트리거 → `repo.deleteAll()` 불가, 테스트별 고유 subscriptionId 사용
- `findByAccountIdAndDetectedAtBetween`: from/to 경계 값 (inclusive) 및 범위 밖 이벤트 제외 검증

## Failure Scenarios
- Docker 미실행 → `@EnabledIf` 로 자동 skip
