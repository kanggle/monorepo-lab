---
id: TASK-BE-103
title: auth.login.succeeded 이벤트 소비 — lastLoginSucceededAt 갱신
status: ready
priority: critical
target_service: account-service
tags: [event, kafka, consumer]
created_at: 2026-04-26
---

# TASK-BE-103: auth.login.succeeded 이벤트 소비 — lastLoginSucceededAt 갱신

## Goal

account-service가 auth-service의 `auth.login.succeeded` Kafka 이벤트를 소비하여
`accounts.last_login_succeeded_at` 컬럼을 갱신한다.

현재 상태: `last_login_succeeded_at` 컬럼(V0007 마이그레이션), 도메인 필드(`Account.lastLoginSucceededAt`),
JPA 매핑(`AccountJpaEntity`) 모두 구현되어 있지만 이 컬럼을 업데이트하는 코드가 없다.
`AccountDormantScheduler`는 `COALESCE(a.lastLoginSucceededAt, a.createdAt)`으로 fallback하므로
이 컬럼이 NULL인 상태에서는 로그인 이력이 있어도 휴면 판정이 `created_at` 기준으로 내려진다.

## Scope

### In
- `infrastructure/messaging/` 패키지에 `LoginSucceededConsumer` 추가 (groupId: `account-service`)
- `application/` 패키지에 `UpdateLastLoginUseCase` 추가
- `Account` 도메인에 `recordLoginSuccess(Instant)` 메서드 추가
- `ProcessedEventJpaRepository` (libs/java-messaging) 이용한 DB-level 멱등성 처리
- `infrastructure/kafka/` → `infrastructure/messaging/` 이름 혼용 주의: architecture.md 정의 그대로 유지 (`infrastructure/kafka/` 하위에 배치)
- Kafka consumer group: `account-service` (security-service group과 독립)
- 단위 테스트: `UpdateLastLoginUseCaseTest`, `LoginSucceededConsumerTest`
- 통합 테스트: `LoginSucceededConsumerIntegrationTest` (AbstractIntegrationTest 상속)

### Out
- Kafka topic 생성/설정 변경 — 이미 존재하는 토픽 재사용
- auth-service 코드 변경
- `auth.login.failed`, `auth.login.attempted` 이벤트 처리
- Redis-layer dedup (security-service 방식) — DB `processed_events` 단일 레이어로 충분

## Acceptance Criteria

1. `LoginSucceededConsumer`가 `auth.login.succeeded` 토픽을 `groupId = "account-service"`로 구독한다.
2. 이벤트 수신 시 payload의 `accountId`와 `timestamp`를 추출하여 `accounts.last_login_succeeded_at`을 갱신한다.
3. 동일 `eventId`가 재전달되면 `processed_events` 테이블 조회로 중복 처리를 방지한다 (DB PRIMARY KEY 충돌 → 무시).
4. `accountId`에 해당하는 계정이 존재하지 않으면 WARN 로그 후 정상 종료 (poison pill 방지).
5. 역직렬화 실패 시 RuntimeException을 던져 Kafka가 재시도하도록 한다.
6. `Account.recordLoginSuccess(Instant)` 메서드가 `lastLoginSucceededAt` 필드를 갱신한다.
7. 단위 테스트: 정상 처리, 중복 이벤트, 계정 미존재 3가지 시나리오 커버.
8. 통합 테스트: Testcontainers MySQL + Kafka로 end-to-end 갱신 검증.

## Related Specs

- `specs/services/account-service/architecture.md` — 레이어 구조 및 내부 패키지 규칙
- `specs/services/account-service/data-model.md` — accounts.last_login_succeeded_at 컬럼
- `platform/entrypoint.md` — 스펙 읽기 순서
- `platform/testing-strategy.md` — 테스트 전략
- `rules/traits/transactional.md` — T3 Outbox/Inbox 패턴

## Related Contracts

- `specs/contracts/events/auth-events.md` — `auth.login.succeeded` 이벤트 페이로드 정의

## Related Skills

- (없음 — 기존 패턴에서 직접 파악)

## Edge Cases

- `timestamp` 필드가 null인 경우: `Instant.now()`로 fallback하고 WARN 로그.
- `accountId`가 null 또는 빈 문자열인 경우: WARN 로그 후 정상 종료.
- 이벤트 수신 시점보다 이전 timestamp (재전달 시나리오): 현재 저장된 값이 더 최신이면 갱신하지 않음 (max 시맨틱).
- 이미 DELETED/DORMANT 상태인 계정: 갱신 진행 (상태 무관 — 이력 보존 목적).

## Failure Scenarios

- Kafka 브로커 연결 실패: Spring Kafka 재시도 설정에 위임, consumer 자체는 예외 전파.
- DB INSERT 실패 (processed_events): 트랜잭션 롤백 → Kafka offset 커밋 안 됨 → 재전달 → 멱등성 처리로 안전.
- AccountRepository.save 실패: 동일한 트랜잭션 롤백.

## Test Requirements

### 단위 테스트 (`*Test.java`)
- `UpdateLastLoginUseCaseTest`:
  - 정상: `AccountRepository.findById` → `account.recordLoginSuccess()` → `save()` 호출 검증
  - 중복: `ProcessedEventJpaRepository.existsByEventId` true → 즉시 return
  - 계정 미존재: `findById` empty → warn log, no save
- `LoginSucceededConsumerTest`:
  - 올바른 envelope 파싱 → UseCase 호출 검증
  - eventId 누락 envelope → UseCase 미호출 검증

### 통합 테스트 (`*IntegrationTest.java`)
- `LoginSucceededConsumerIntegrationTest` extends `AbstractIntegrationTest`:
  - Kafka로 `auth.login.succeeded` 이벤트 발행 → DB에서 `last_login_succeeded_at` 갱신 확인
  - 동일 이벤트 재발행 → `processed_events`에 중복 처리 방지 확인

## Implementation Notes

### 멱등성 처리 패턴
`ProcessedEventJpaRepository` (`libs/java-messaging`)의 `existsByEventId(String)` 사용.
`save(ProcessedEventJpaEntity.create(eventId, eventType))`와 `accountRepository.save()` 동일 트랜잭션.

### Account 도메인 메서드
```java
// Account.java 에 추가
public void recordLoginSuccess(Instant occurredAt) {
    if (this.lastLoginSucceededAt == null || occurredAt.isAfter(this.lastLoginSucceededAt)) {
        this.lastLoginSucceededAt = occurredAt;
    }
}
```

### Consumer 배치 위치
`apps/account-service/src/main/java/com/example/account/infrastructure/kafka/` 하위
(architecture.md 정의 준수 — `infrastructure/messaging/`이 아닌 `infrastructure/kafka/`)

### Kafka consumer group
`groupId = "account-service"` (security-service의 `"security-service"` 와 독립된 consumer group)
