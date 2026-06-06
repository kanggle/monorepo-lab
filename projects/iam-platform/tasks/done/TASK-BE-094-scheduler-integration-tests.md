---
id: TASK-BE-094
title: "test: AccountDormantScheduler + AccountAnonymizationScheduler 통합 테스트"
status: ready
area: backend
service: account-service
---

## Goal

TASK-BE-092/093에서 구현된 두 스케줄러의 배치 흐름을 실제 DB(H2 in-memory 또는 Testcontainers MySQL)와 함께 End-to-End로 검증한다. 단위 테스트가 커버하지 못하는 쿼리 정확성·트랜잭션 경계·이벤트 발행 순서를 검증한다.

## Scope

### In

- `apps/account-service/src/test/java/com/example/account/infrastructure/scheduler/AccountDormantSchedulerIntegrationTest.java` 신규
  - `@SpringBootTest` + `@Transactional` (또는 Testcontainers)
  - 시나리오:
    1. 365일 초과 미접속 ACTIVE 계정 → 배치 실행 → DORMANT 전환, `account.status.changed` 이벤트 발행
    2. 364일 미접속 ACTIVE 계정 → 배치 실행 → 전환 없음
    3. 365일 초과 LOCKED 계정 → 배치 실행 → 전환 없음 (금지 전이)
    4. 이미 DORMANT인 계정 → 배치 실행 → 중복 처리 없음

- `apps/account-service/src/test/java/com/example/account/infrastructure/scheduler/AccountAnonymizationSchedulerIntegrationTest.java` 신규
  - 시나리오:
    1. 30일 초과 DELETED + masked_at IS NULL → 배치 실행 → `masked_at` 설정, PII 마스킹 완료
    2. 29일 DELETED + masked_at IS NULL → 배치 실행 → 익명화 없음
    3. DELETED + masked_at 이미 설정 → 배치 실행 → 중복 처리 없음
    4. DELETED + 30일 초과지만 grace period 복구됨 (ACTIVE) → 배치 실행 → 처리 없음

### Out

- 스케줄러 트리거 타이밍 테스트 (`@Scheduled` cron 자체 테스트)
- 분산 락(distributed lock) 관련 테스트
- admin-service, auth-service 통합 테스트

## Acceptance Criteria

- [ ] `AccountDormantSchedulerIntegrationTest` — 4개 시나리오 모두 통과
- [ ] `AccountAnonymizationSchedulerIntegrationTest` — 4개 시나리오 모두 통과
- [ ] 이벤트 발행 검증: `account.status.changed` 이벤트가 `dormant` 시나리오에서 발행됨
- [ ] `./gradlew :apps:account-service:test` 전체 통과

## Related Specs

- specs/services/account-service/retention.md — 배치 정책 기준값 (365일, 30일)
- specs/features/account-lifecycle.md — 상태 전이 규칙
- platform/testing-strategy.md — 통합 테스트 작성 규칙

## Related Contracts

- specs/contracts/events/account-events.md — `account.status.changed` 이벤트 검증

## Edge Cases

- H2 날짜 함수 차이 (`now() - INTERVAL 365 DAY`): `LocalDateTime.now().minusDays(365)`로 Java 계산 후 바인딩 권장
- Testcontainers 미사용 환경: `AbstractIntegrationTest` 공통 픽스처 활용 (기존 패턴 따름)

## Failure Scenarios

- TASK-BE-092 또는 TASK-BE-093 미구현 시 Hard Stop — 의존 태스크 완료 후 실행
- 이벤트 발행 검증 어려움: Outbox 테이블 직접 조회 또는 `ApplicationEventPublisher` 캡처로 대체
