---
id: TASK-BE-112
title: "test(account): 휴면 스케줄러 — lastLoginSucceededAt NULL 및 최근 로그인 케이스 통합 테스트 보강"
status: ready
priority: high
target_service: account-service
tags: [test]
created_at: 2026-04-26
---

# TASK-BE-112: 휴면 스케줄러 lastLoginSucceededAt NULL / 최근 로그인 케이스 통합 테스트

## Goal

BE-103에서 `auth.login.succeeded` Kafka 컨슈머가 추가되어 `last_login_succeeded_at`이 실제로 채워지기 시작했다.
기존 `AccountDormantSchedulerIntegrationTest`는 `lastLoginSucceededAt`을 항상 non-null 값으로 설정하는 헬퍼(`setAccountTimestamps`)만 사용하므로 아래 두 시나리오를 커버하지 못한다.

1. **`lastLoginSucceededAt = NULL`** (BE-103 이전 계정) → `COALESCE(last_login_succeeded_at, created_at)` 에서 `created_at`만으로 판단
2. **`lastLoginSucceededAt`이 최근**(365일 미만) + `created_at`은 366일 초과 → 로그인 이력이 있어 ACTIVE 유지되어야 함

이 두 케이스를 통합 테스트로 추가하여 COALESCE 로직이 올바르게 동작함을 검증한다.

## Scope

### In

**`AccountDormantSchedulerIntegrationTest.java` 수정**
- `setAccountTimestamps` 헬퍼를 `lastLoginSucceededAt`에 `null`을 허용하도록 오버로드(또는 별도 헬퍼 추가)
  - SQL: `UPDATE accounts SET created_at = ?, last_login_succeeded_at = ? WHERE id = ?`
  - `null` 전달 시 `Timestamp` 대신 `null`을 바인딩 (`jdbcTemplate.update(..., null, ...)`)
- 신규 테스트 케이스 2개 추가:

**케이스 A: `lastLoginSucceededAt = NULL`, `createdAt` 366일 초과 → DORMANT 전환**
- 시나리오: BE-103 컨슈머가 아직 메시지를 소비하지 않은 오래된 계정
- 검증: 상태 DORMANT, `account_status_history` 1행, outbox `account.status.changed`

**케이스 B: `lastLoginSucceededAt` 최근(364일), `createdAt` 366일 초과 → ACTIVE 유지**
- 시나리오: 계정이 오래됐지만 최근 로그인 이력이 있는 경우
- 검증: 상태 ACTIVE 유지, history 미추가, outbox `account.status.changed` 미생성

### Out
- `AccountDormantScheduler` 로직 변경
- `AccountJpaRepository.findActiveDormantCandidates` 쿼리 변경
- 다른 스케줄러(`AccountAnonymizationScheduler`) 테스트 변경

## Acceptance Criteria

1. `runDormantBatch_nullLastLogin_oldCreatedAt_transitionsToDormant` 테스트가 통과한다.
2. `runDormantBatch_recentLastLogin_oldCreatedAt_remainsActive` 테스트가 통과한다.
3. 기존 4개 테스트(`eligibleActive`, `belowThreshold`, `lockedAccount`, `alreadyDormant`)가 그대로 통과한다.
4. `./gradlew :apps:account-service:test --tests "com.example.account.infrastructure.scheduler.*"` BUILD SUCCESSFUL.

## Related Specs

- `specs/features/account-lifecycle.md` — §Automatic Transitions: 365일 미접속 → DORMANT, COALESCE 기준
- `specs/services/account-service/architecture.md` — Scheduler 절, `retention.md §1.3/§1.4` 참조

## Related Skills

- `.claude/skills/INDEX.md` 참조 후 `testing` 관련 스킬 적용

## Related Contracts

- 없음 (API 계약 변경 없음)

## Target Service

account-service

## Architecture

`specs/services/account-service/architecture.md` 참조.

## Implementation Notes

- `setAccountTimestamps(accountId, createdAt, null)` 호출 시 MySQL `NULL`이 저장되어야 한다.
  - JdbcTemplate에서 `null` 직접 바인딩: `Timestamp.from(...)` 대신 `null` 전달 가능.
  - 또는 별도 헬퍼 `setNullLastLogin(accountId, createdAt)` 추가.
- `jdbcTemplate.update(sql, param1, param2, param3)` 에서 `param2`가 `null`이면 JdbcTemplate이 자동으로 NULL SQL 값으로 처리한다.
- 기존 헬퍼 시그니처 변경은 기존 4개 테스트 컴파일 호환성을 깨지 않도록 오버로드 방식 권장.

## Edge Cases

- `lastLoginSucceededAt = NULL` + `createdAt = NULL`: 실제로 불가능 (createdAt은 NOT NULL 스키마), 처리 불필요
- 경계값(`365일 정확히`): 기존 `belowThreshold` 테스트가 364일로 커버; 이 태스크에서 별도 추가 불필요

## Failure Scenarios

- JdbcTemplate null 바인딩 오류: NULL이 아닌 0-epoch Timestamp로 저장되면 쿼리 결과 오염. SQL로 직접 검증 후 어서션.

## Test Requirements

### 통합 테스트
- 기존 `AccountDormantSchedulerIntegrationTest`에 2개 케이스 추가
- Testcontainers MySQL 사용 (기존 `AbstractIntegrationTest` 상속 구조 유지)
- 케이스 A, B 각각 독립 `@Test` 메서드
- `@DisplayName` 한국어로 시나리오 설명 포함

## Definition of Done

- [ ] 케이스 A 테스트 추가 및 통과
- [ ] 케이스 B 테스트 추가 및 통과
- [ ] 기존 4개 테스트 통과 (회귀 없음)
- [ ] `./gradlew :apps:account-service:test --tests "com.example.account.infrastructure.scheduler.*"` BUILD SUCCESSFUL
- [ ] 코드 리뷰 통과
- [ ] `tasks/review/`로 이동 완료
