---
id: TASK-BE-092
title: "feat: AccountDormantScheduler — 365일 미접속 계정 자동 휴면 전환 배치"
status: ready
area: backend
service: account-service
---

## Goal

`specs/services/account-service/retention.md` Dormant Activation 정책에 따라, 마지막 로그인 성공 후 365일이 경과한 ACTIVE 계정을 일 1회 배치로 DORMANT 상태로 전환하는 스케줄러를 구현한다.

## Scope

### In

- `apps/account-service/src/main/java/com/example/account/infrastructure/scheduler/AccountDormantScheduler.java` 신규
  - `@Scheduled(cron = "0 0 2 * * *", zone = "UTC")` 일 1회 UTC 02:00
  - `last_login_succeeded_at < now() - INTERVAL 365 DAY AND status = ACTIVE` 쿼리
  - `AccountStatusService.transitionToDormant(accountId, "DORMANT_365D")` 호출
  - 처리 결과 지표 기록: `scheduler.dormant.processed`, `scheduler.dormant.failed`
  - 개별 오류 시 skip + warn 로그, 전체 배치 계속 진행
- `apps/account-service/src/main/java/com/example/account/infrastructure/persistence/AccountJpaRepository.java` 수정
  - `findActiveDormantCandidates(LocalDateTime threshold): List<AccountJpaEntity>` 쿼리 메서드 추가
- `apps/account-service/src/main/java/com/example/account/application/service/AccountStatusService.java` 수정 (필요 시)
  - `transitionToDormant(String accountId, String reason)` 메서드 — 기존 상태 기계로 ACTIVE → DORMANT 전환
  - `account.status.changed` 이벤트 발행 (source: "system", reason: "DORMANT_365D")
- `apps/account-service/src/test/java/com/example/account/infrastructure/scheduler/AccountDormantSchedulerTest.java` 신규
  - 단위 테스트: `findActiveDormantCandidates` 결과로 `transitionToDormant` 호출 검증
  - 오류 skip 검증: 특정 계정 전환 실패 시 나머지 계정은 계속 처리됨
  - 대상 없음 케이스: 배치 실행하지만 아무것도 처리하지 않음

### Out

- DB 마이그레이션 (`last_login_succeeded_at` 컬럼은 기존 존재)
- `@EnableScheduling` 설정 변경 (이미 활성화 여부 확인 후 없으면 추가)
- 통합 테스트 (TASK-BE-094)
- admin-service, auth-service 변경

## Acceptance Criteria

- [ ] `AccountDormantScheduler` 클래스 존재, `@Scheduled(cron = "0 0 2 * * *", zone = "UTC")` 적용
- [ ] `findActiveDormantCandidates(threshold)` 쿼리 메서드 존재, 올바른 조건
- [ ] 365일 기준 계산: `now().minusDays(365)` 를 threshold로 전달
- [ ] 이벤트 발행: `account.status.changed` with `source=system, reason=DORMANT_365D`
- [ ] 개별 오류 시 해당 계정 skip, 나머지 처리 계속
- [ ] 단위 테스트 통과
- [ ] `./gradlew :apps:account-service:test` 전체 통과

## Related Specs

- specs/services/account-service/retention.md — 휴면 전환 정책 (TASK-BE-091에서 작성)
- specs/features/account-lifecycle.md — ACTIVE → DORMANT 전이 규칙
- specs/services/account-service/architecture.md — scheduler 레이어 위치 (infrastructure/scheduler)

## Related Contracts

- specs/contracts/events/account-events.md — `account.status.changed` 이벤트 스키마

## Edge Cases

- `last_login_succeeded_at IS NULL` 계정: `created_at`을 대체 기준으로 사용 (retention.md 정책 따름)
- DORMANT 전환 중 이미 LOCKED인 계정: retention.md에서 LOCKED → DORMANT 금지 전이 — skip
- 배치 실행 도중 서버 재시작: `@Scheduled`는 멱등성 보장 — 재실행 시 이미 DORMANT인 계정은 쿼리에서 제외됨

## Failure Scenarios

- `AccountStatusService.transitionToDormant` 미존재 시: 기존 `statusTransition()` 메서드 패턴을 따라 신규 메서드 추가
- `@EnableScheduling` 누락: `AccountServiceApplication` 또는 설정 클래스에 추가
