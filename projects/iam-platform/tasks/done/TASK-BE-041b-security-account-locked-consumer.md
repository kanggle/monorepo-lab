# Task ID

TASK-BE-041b-security-account-locked-consumer

# Title

security-service — AccountLockedConsumer + account_lock_history 테이블 + 단위/슬라이스 테스트

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- db
- test

# depends_on

- (없음)

---

# Goal

admin-service가 account-service에 계정 lock을 수행한 뒤 발행하는 `account.locked` Kafka 이벤트를 security-service가 consume하여 보안 분석 경로(`account_lock_history` 테이블)에 기록한다. 현재 security-service는 `auth.*` 토픽 5종만 consume하며 account 도메인 이벤트 소비 경로가 없어 관리자 잠금이 보안 분석에 반영되지 않는다.

---

# Scope

## In Scope

### 1) Kafka 이벤트 계약
- `specs/contracts/events/account-events.md`(있으면) 또는 신설: `account.locked` 이벤트 스키마 등록
  - 필드: `eventId`, `occurredAt`, `accountId`, `reason`(VARCHAR), `lockedBy`(operator UUID), `source`("admin" 또는 "system")
- admin-service의 기존 `AccountAdminUseCase.lock` 경로가 이벤트 발행하는지 전수 점검. 발행 누락이면 본 태스크 범위에 포함: `AdminEventPublisher`에 `publishAccountLocked(...)` 추가
- bulk-lock은 이미 row별로 `LockAccountUseCase` 호출 → 이벤트 발행 경로가 한 곳에서 처리됨

### 2) security-service AccountLockedConsumer
- 경로: `apps/security-service/src/main/java/com/example/security/consumer/AccountLockedConsumer.java`
- 기존 `AbstractAuthEventConsumer` 패턴 참조 (ErrorHandlingDeserializer → StrictJsonStringDeserializer → DefaultErrorHandler → DLQ 자동 라우팅)
- `@KafkaListener(topics = "account.locked", groupId = "security-service")`
- 이벤트 수신 → `AccountLockHistoryRepository.save(new AccountLockHistoryEntity(...))`

### 3) Flyway 마이그레이션 (security-service)
- 최신 security-service 마이그레이션 버전 확인 후 다음 번호: `V00XX__account_lock_history.sql`
- 테이블:
  - `account_lock_history(id BIGINT PK AUTO_INCREMENT, account_id UUID NOT NULL, reason VARCHAR(255), locked_by UUID NOT NULL, source VARCHAR(32) NOT NULL, occurred_at TIMESTAMP(6) NOT NULL, received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))`
- Index `(account_id, occurred_at DESC)` — 특정 계정의 lock 이력 조회

### 4) JPA entity/repository
- `AccountLockHistoryEntity`, `AccountLockHistoryRepository extends JpaRepository<...>`

### 5) 테스트
- `AccountLockedConsumerTest` (mock 기반 단위): 유효 이벤트 → save 호출 검증, invalid 이벤트 → DLQ 라우팅(기존 KafkaConsumerConfig 재사용) 검증
- Slice: `@DataJpaTest`로 repository 쿼리 검증
- 기존 `DlqRoutingIntegrationTest`가 새 consumer도 자동 커버하는지 확인 (파라미터화 가능)

### 6) admin-service 이벤트 발행 확인
- `AdminEventPublisher.publishAccountLocked(accountId, reason, operatorId)` 메서드 존재 확인. 없으면 추가 (canonical envelope 기반, 028b2 패턴)
- `AccountAdminUseCase.lock` 성공 후 발행 (transactional outbox 또는 동기 publish — 기존 패턴 따름)

## Out of Scope

- security-service의 lock history 조회 HTTP 엔드포인트 (별도 태스크)
- admin-service의 lock history 프록시 조회 경로 (별도)
- `account.unlocked` 이벤트 (현 태스크에서는 lock만)
- E2E 실행 (041c)

---

# Acceptance Criteria

- [ ] `account.locked` 이벤트 계약 스펙 문서화
- [ ] `AccountLockedConsumer` 수신 → `account_lock_history` row 저장
- [ ] 기존 DLQ 라우팅이 `account.locked` 토픽에도 적용 (invalid 메시지 → `.dlq`)
- [ ] admin-service의 lock 경로에서 이벤트 발행 검증 (기존 없으면 신규 추가)
- [ ] `./gradlew :apps:security-service:test :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/security-service/architecture.md`
- `specs/services/admin-service/architecture.md`
- `specs/contracts/events/account-events.md` (신설 또는 업데이트)
- `platform/service-types/event-consumer.md`

# Related Contracts

- `specs/contracts/events/account-events.md`

---

# Target Service

- `apps/security-service`
- `apps/admin-service` (발행 경로 검증/추가)

---

# Edge Cases

- 동일 accountId에 중복 lock 이벤트 수신 (admin이 이미 잠긴 계정 재잠금) → row 다수 허용(이력 성격)
- `lockedBy` operator가 UUID 해석 불가 시 로그 + save 지속 (hard fail하지 않음)
- clock skew로 `occurred_at > received_at`인 경우 수용

---

# Failure Scenarios

- JSON deserialize 실패 → DLQ (기존 Strict deserializer)
- DB write 실패 → 기본 `DefaultErrorHandler` retry 3회 후 DLQ

---

# Test Requirements

- Consumer 단위 + repository 슬라이스
- 기존 DlqRoutingIntegrationTest 확장(파라미터화 가능)

---

# Definition of Done

- [ ] 구현 + 테스트 완료
- [ ] 계약 스펙 반영
- [ ] Ready for review
