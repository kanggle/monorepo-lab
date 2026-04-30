---
id: TASK-BE-093
title: "feat: AccountAnonymizationScheduler — 30일 유예 만료 계정 PII 자동 익명화 배치"
status: review
area: backend
service: account-service
---

## Goal

`specs/services/account-service/retention.md` PII Anonymization 정책에 따라, DELETED 상태이고 `deleted_at` 이후 30일이 경과했으며 `masked_at IS NULL`인 계정을 일 1회 배치로 PII 익명화하는 스케줄러를 구현한다. 기존 `PiiAnonymizer` 인프라를 재사용한다.

## Scope

### In

- `apps/account-service/src/main/java/com/example/account/infrastructure/scheduler/AccountAnonymizationScheduler.java` 신규
  - `@Scheduled(cron = "0 0 3 * * *", zone = "UTC")` 일 1회 UTC 03:00
  - `status = DELETED AND masked_at IS NULL AND deleted_at < now() - INTERVAL 30 DAY` 쿼리
  - `PiiAnonymizer.anonymize(account)` 호출
  - `masked_at = now()` 업데이트
  - 처리 지표: `scheduler.anonymize.processed`, `scheduler.anonymize.failed`
  - 개별 오류 시 skip + warn 로그, 전체 배치 계속 진행
- `apps/account-service/src/main/java/com/example/account/infrastructure/persistence/AccountJpaRepository.java` 수정
  - `findAnonymizationCandidates(LocalDateTime threshold): List<AccountJpaEntity>` 쿼리 메서드 추가
- `apps/account-service/src/test/java/com/example/account/infrastructure/scheduler/AccountAnonymizationSchedulerTest.java` 신규
  - 단위 테스트: 대상 계정에 `PiiAnonymizer.anonymize()` 호출 및 `masked_at` 설정 검증
  - grace period 내 복구 계정 제외 검증 (masked_at IS NULL이지만 status = ACTIVE인 경우)
  - 오류 skip 검증: 특정 계정 실패 시 나머지 계속 처리

### Out

- DB 마이그레이션 (`masked_at`, `deleted_at` 컬럼은 기존 존재)
- `PiiAnonymizer` 구현 변경 (기존 로직 재사용)
- GDPR 삭제 API(`GdprDeleteUseCase`) 변경
- 통합 테스트 (TASK-BE-094)

## Acceptance Criteria

- [ ] `AccountAnonymizationScheduler` 클래스 존재, `@Scheduled(cron = "0 0 3 * * *", zone = "UTC")` 적용
- [ ] `findAnonymizationCandidates(threshold)` 쿼리: `status=DELETED, masked_at IS NULL, deleted_at < threshold`
- [ ] 30일 기준 계산: `now().minusDays(30)` 를 threshold로 전달
- [ ] 처리 후 `masked_at` = 현재 시각으로 업데이트
- [ ] 개별 오류 시 skip, 전체 배치 계속 진행
- [ ] 단위 테스트 통과
- [ ] `./gradlew :apps:account-service:test` 전체 통과

## Related Specs

- specs/services/account-service/retention.md — PII 익명화 정책 (TASK-BE-091에서 작성)
- specs/features/data-rights.md — GDPR 삭제 + PII 마스킹 정의
- specs/services/account-service/architecture.md — infrastructure/anonymizer, infrastructure/scheduler 레이어 위치

## Related Contracts

- 없음 (외부 이벤트 발행 없음 — `account.deleted` 이벤트는 DELETED 전환 시 이미 발행됨)

## Edge Cases

- `masked_at`이 이미 설정된 계정: 쿼리 조건 `masked_at IS NULL`로 자동 제외
- grace period 내 복구된 계정 (DELETED → ACTIVE): status = ACTIVE이므로 쿼리 조건 `status=DELETED`에서 제외
- `PiiAnonymizer.anonymize()` 실패 (예: 이미 익명화된 필드): 개별 skip, warn 로그
- 같은 계정이 배치 실행 중 동시에 grace period 복구 API 호출되는 경우: 낙관적 락 또는 `status=DELETED` 재확인 후 처리

## Failure Scenarios

- `PiiAnonymizer` 클래스 없음: `architecture.md`의 `infrastructure/anonymizer` 위치에서 기존 구현 확인
- `masked_at` 컬럼 없음: TASK-BE-088 마이그레이션에서 추가됨 — 없으면 Hard Stop
