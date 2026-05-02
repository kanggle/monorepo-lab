# Task ID

TASK-MONO-023d

# Title

TenantAdmin / OutboxRelay 의 outbox 관련 회귀 fix

# Status

ready

# Owner

backend / qa

# Task Tags

- code
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-MONO-023](../in-progress/TASK-MONO-023-main-baseline-integration-cleanup.md) 의 분류 매트릭스에서 식별된 **outbox 관련 2 통합 테스트** 의 회귀를 fix:

| 테스트 클래스 | 증상 | 추정 원인 |
|---|---|---|
| `admin.TenantAdminIntegrationTest` | `BadSqlGrammarException: SELECT COUNT(*) FROM outbox_events ...` | `outbox_events` 테이블이 admin-service 통합 테스트 환경에 없음 — Flyway 마이그레이션 누락 |
| `auth.OutboxRelayIntegrationTest` | "Expecting actual not to be empty" — outbox 행이 없음 | publisher 호출이 outbox 에 INSERT 하지 않음 또는 transaction commit 시점 회귀 |

이 태스크 완료 후 위 2 테스트가 모두 PASS.

---

# Scope

## In Scope

### 1. TenantAdmin — outbox_events 테이블 누락

- admin-service 의 Flyway 마이그레이션 디렉토리 확인 (`db/migration/V*__create_outbox_events.sql`)
- 마이그레이션이 누락되었으면 추가 (account-service / auth-service 의 동일 테이블 생성 마이그레이션 참고)
- 테이블 스키마: `id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload`, `created_at`, `published_at` (서비스별 spec 따름)
- `admin_actions` 의 outbox publishing 이 이 테이블에 INSERT 하는지 확인

### 2. OutboxRelay — outbox 행 미발행

- `OutboxRelay` 의 publisher 호출 경로 분석
- 호출 시점에 transaction commit 이 안 된 상태이면 `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용
- outbox INSERT 시점이 use-case 내 transaction 안에서 일어나는지 확인 (W4/W5 패턴 — eventId dedupe + Propagation.MANDATORY)
- 실제 publisher 실행 시점 vs 테스트 폴링 시점의 timing 검토

## Out of Scope

- Provisioning 회귀 — TASK-MONO-023a
- OAuth2 / OIDC 회귀 — TASK-MONO-023b
- Audit / Anonymization 회귀 — TASK-MONO-023c
- Community JPA 격리 — TASK-MONO-023e
- 새 outbox event 발행 추가 — 본 태스크 범위 밖

---

# Acceptance Criteria

- [ ] `TenantAdminIntegrationTest` PASS — `outbox_events` 테이블 존재 + `tenant.created` 이벤트 행 INSERT 확인
- [ ] `OutboxRelayIntegrationTest` PASS — outbox 행이 production publisher 에 의해 비어있지 않음
- [ ] 5회 연속 PASS
- [ ] outbox 마이그레이션이 추가되었으면 prod 환경 호환성 검증 (기존 `outbox_events` 테이블이 있는 환경에서 idempotent 적용)

---

# Related Specs

- `projects/global-account-platform/specs/services/admin-service/architecture.md` § Outbox Pattern
- `projects/global-account-platform/specs/services/auth-service/architecture.md` § Event Publishing
- `platform/event-driven-architecture.md` (있다면) — outbox / publisher / relay 패턴

---

# Related Contracts

- `specs/contracts/events/admin-events.md` § admin.action.performed
- `specs/contracts/events/tenant-events.md` § tenant.created / suspended / reactivated
- `specs/contracts/events/auth-events.md` § auth.login.* / auth.token.*

---

# Target Service / Component

### admin-service
- `projects/global-account-platform/apps/admin-service/src/main/resources/db/migration/V*__create_outbox_events.sql` (신규 또는 확인)
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/event/AdminEventPublisher.java`
- `projects/global-account-platform/apps/admin-service/src/test/java/com/example/admin/integration/TenantAdminIntegrationTest.java`

### auth-service
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/event/AuthOutboxPublisher.java` (또는 OutboxRelay 위치)
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OutboxRelayIntegrationTest.java`

---

# Implementation Notes

- admin-service 의 Flyway 마이그레이션 listing 으로 outbox_events 누락 여부 즉시 확인:
  - `ls projects/global-account-platform/apps/admin-service/src/main/resources/db/migration/`
  - `grep -l outbox_events ...`
- account-service 의 outbox_events 마이그레이션을 참조 모델로 사용
- OutboxRelay 의 timing 이슈는 `Awaitility` 의 polling timeout 증가로 해결 가능 (단, 이건 flaky 처리 — 진짜 회귀일 가능성 더 높음)
- OutboxPublisher 가 호출되었으나 INSERT 가 commit 안 된 케이스: `@Transactional(propagation = MANDATORY)` 또는 `REQUIRES_NEW` 검토

---

# Edge Cases

- 마이그레이션 추가 시 prod DB 에 기존 `outbox_events` 가 있으면 `CREATE TABLE IF NOT EXISTS` 또는 baseline 처리
- OutboxRelay 의 publisher 가 의도적으로 빈 outbox 를 만드는 케이스 (예: idempotent skip) → 테스트 setup 보강

---

# Failure Scenarios

- admin-service 의 outbox_events 마이그레이션이 prod 와 충돌 → 마이그레이션 versioning 검토 (V0XXX 다음 슬롯)
- OutboxRelay 의 transaction 변경이 다른 publisher 경로에 영향 → 회귀 영향 분석

---

# Test Requirements

- 2 통합 테스트 PASS
- 5회 연속 PASS
- 마이그레이션 추가 시 단위 테스트 또는 schema validation 테스트

---

# Definition of Done

- [ ] 2 통합 테스트 PASS
- [ ] 마이그레이션 추가 (필요 시) + prod 호환성 확인
- [ ] OutboxRelay timing fix (필요 시) + 단위 테스트
- [ ] Ready for review
