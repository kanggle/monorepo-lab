---
id: TASK-BE-096
title: "fix(scheduler): TASK-BE-092 review 지적 사항 수정 — Micrometer 메트릭 추가 + lastLoginSucceededAt 덮어쓰기 버그 수정"
status: ready
area: backend
service: account-service
---

## Goal

TASK-BE-092 (`AccountDormantScheduler`) 리뷰에서 발견된 Critical/Warning 항목을 수정한다.

## Scope

### In

1. **[Critical] `AccountJpaEntity.fromDomain` — `lastLoginSucceededAt` 덮어쓰기 버그 수정**
   - `AccountJpaEntity.fromDomain(Account)` 이 `lastLoginSucceededAt` 필드를 매핑하지 않아 `AccountRepositoryAdapter.save()` 호출 시 `last_login_succeeded_at` 컬럼이 `NULL`로 덮어써짐
   - 해결 방안: `Account` 도메인 객체에 `lastLoginSucceededAt: Instant` 필드 추가 + `reconstitute()` 매개변수에 포함 + `AccountJpaEntity.fromDomain()` 및 `toDomain()` 에 매핑 추가
   - 영향: `AccountRepositoryAdapter`, `AccountJpaEntity`, `Account`, 관련 테스트

2. **[Critical] `AccountDormantScheduler` — Micrometer 메트릭 미구현 수정**
   - `retention.md §1.8` 및 태스크 Scope에서 요구하는 `scheduler.dormant.processed`, `scheduler.dormant.failed`, `scheduler.dormant.duration_ms` 메트릭 미구현
   - `AccountAnonymizationScheduler`의 `MeterRegistry` 패턴을 따라 구현
   - `scheduler.dormant.duration_ms`: `Timer` 또는 `long` 타임스탬프로 배치 전체 소요 시간 기록

3. **[Warning] `AccountDormantScheduler` — standalone 가드 누락**
   - `@Profile("!standalone")` 및 `@ConditionalOnProperty(name = "scheduler.dormant.enabled", havingValue = "true", matchIfMissing = true)` 추가 (`AccountAnonymizationScheduler` 패턴 일치)

4. **[Warning] 메서드명 혼동 수정**
   - `activateDormantAccounts()` → `runDormantBatch()` 로 이름 변경 (DORMANT 전환 배치임을 명확히)
   - 테스트 메서드명도 동일하게 갱신

### Out

- `AccountAnonymizationScheduler` 변경
- 통합 테스트 (TASK-BE-094)
- migration 변경

## Acceptance Criteria

- [ ] `Account.lastLoginSucceededAt` 필드 존재, `reconstitute()` 매개변수에 포함
- [ ] `AccountJpaEntity.fromDomain()` 에서 `lastLoginSucceededAt` 매핑
- [ ] `AccountJpaEntity.toDomain()` 에서 `lastLoginSucceededAt` 매핑
- [ ] `AccountDormantScheduler` 에 `MeterRegistry` 주입, `scheduler.dormant.processed` / `scheduler.dormant.failed` counter 호출
- [ ] `scheduler.dormant.duration_ms` 히스토그램/타이머 기록 (배치 1회 소요 시간)
- [ ] `@Profile("!standalone")` + `@ConditionalOnProperty` 추가
- [ ] 메서드명 `runDormantBatch()` 으로 변경
- [ ] `AccountDormantSchedulerTest` 통과 (메서드명 반영)
- [ ] `./gradlew :apps:account-service:test` 전체 통과

## Related Specs

- `specs/services/account-service/retention.md` §1.8 (지표 정의)
- `specs/services/account-service/architecture.md` (infrastructure/scheduler 레이어)

## Related Contracts

- 없음 (내부 구현 수정)

## Edge Cases

- `Account.reconstitute()` 를 호출하는 기존 코드(테스트 포함)가 새 매개변수를 전달하도록 수정 필요
- `lastLoginSucceededAt` 은 `null` 허용 (가입 후 미로그인 계정)

## Failure Scenarios

- `Account.reconstitute()` 시그니처 변경 시 컴파일 에러: 영향받는 호출 부위를 모두 수정 후 `./gradlew :apps:account-service:compileJava` 확인
