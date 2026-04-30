# Task ID

TASK-BE-248

# Title

Security-service: propagate `tenant_id` through events, schema, and per-tenant anomaly detection

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- adr

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

`security-service`가 `multi-tenant` trait의 격리 요구사항을 충족하도록 한다. `specs/features/multi-tenancy.md` § "Isolation Strategy / 적용 범위 (서비스별)" 의 보안 이벤트 페이로드 필수 + 테넌트별 임계치 운용 요건이 코드·스키마·소비 양면에서 동작해야 한다.

완료 시점에 다음이 모두 참:

1. `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`, `auth.token.reuse.detected`, `account.status.changed`, `security.suspicious.detected`, `security.auto.lock.triggered`, `security.auto.lock.pending` 이벤트 페이로드에 **항상** `tenant_id` 필드가 포함된다 (소비자 측에서 누락 시 reject).
2. `security-service`의 `login_history`, `suspicious_events`, `account_lock_history` 테이블에 `tenant_id` NOT NULL 컬럼이 존재하며, 모든 query는 `(tenant_id, account_id)` 또는 `(tenant_id, ...)` index를 활용한다.
3. `VelocityRule`, `GeoAnomalyRule`, `ImpossibleTravelRule`, `DeviceFingerprintRule` 등 모든 detection rule이 `tenant_id` 단위로 임계치/카운터를 분리한다 (Redis key prefix · DB query predicate 양쪽).
4. cross-tenant 데이터 누출 회귀 테스트가 통과한다.

---

# Scope

## In Scope

- `security-service` flyway migration 추가:
  - `login_history.tenant_id VARCHAR(32) NOT NULL`
  - `suspicious_events.tenant_id VARCHAR(32) NOT NULL`
  - `account_lock_history.tenant_id VARCHAR(32) NOT NULL`
  - 기존 인덱스 `(account_id, ...)` → `(tenant_id, account_id, ...)` 전환
  - 기존 row의 backfill 전략(default `'fan-platform'` 후 NOT NULL 강제)을 ADR로 기록
- 도메인/포트 변경:
  - `LoginHistory`, `SuspiciousEvent`, `AccountLockHistory` 엔티티에 `tenantId` 필드 추가
  - `LoginHistoryRepository.findLatestSuccessByAccountId` 등 모든 finder가 `tenant_id`를 받도록 변경
  - `DetectionResult`, `EvaluationContext`에 `tenantId` 추가
  - `VelocityCounter` Redis key: `velocity:{tenant_id}:{account_id}` 형태로 재설계
  - `LastKnownGeoStore` Redis key: `geo:{tenant_id}:{account_id}`
- 이벤트 contracts 갱신:
  - `specs/contracts/events/auth-events.md` · `specs/contracts/events/account-events.md` · `specs/contracts/events/security-events.md`에 `tenant_id` 필드 (string, required) 추가
  - JSON Schema/Avro 자산이 있다면 동기화
- 소비자 측 검증:
  - Kafka consumer에서 `tenant_id` 누락 메시지를 DLQ로 라우팅 (`@KafkaListener` errorHandler)
- publisher 측 갱신:
  - `auth-service` `AuthEventPublisher`, `account-service` `AccountEventPublisher`, `security-service` `SecurityEventPublisher` 모두 `tenant_id`를 always-required 인자로 받도록 시그니처 변경
- 단위 + 통합 테스트:
  - cross-tenant 누출 회귀 테스트: tenantA 이벤트는 tenantB의 detection 카운터에 기여하지 않음
  - VelocityRule, GeoAnomalyRule 등 핵심 rule에 per-tenant threshold 적용 테스트

## Out of Scope

- `tenant_id` 와일드카드(`'*'`) 또는 platform-scope detection (cross-tenant 통합 모니터링) — 별도 ADR + 후속 태스크
- 테넌트별 detection threshold **설정 UI** — admin-service의 tenant management 후속 (TASK-BE-250 이후)
- 기존 production 데이터의 backfill 자동화 스크립트 — staging/local에서는 default value로 migration 처리, production migration 절차는 별도 runbook

---

# Acceptance Criteria

- [ ] `apps/security-service/src/main/resources/db/migration/`에 신규 V0019+ migration 추가, `login_history`/`suspicious_events`/`account_lock_history` 모두 `tenant_id` NOT NULL
- [ ] `SecurityEventPublisher`가 `tenant_id` 인자를 강제로 받음 (signature 변경; `null` 입력 시 `IllegalArgumentException`)
- [ ] `VelocityCounter` Redis key가 `velocity:{tenant_id}:{account_id}` 형식
- [ ] cross-tenant 회귀 테스트: tenantA 50회 실패가 tenantB의 동일 account에 대한 임계치를 트리거하지 않음
- [ ] `auth-service`/`account-service`의 publisher 호출부 모두 `tenant_id` 전달; 누락된 호출부는 컴파일 실패
- [ ] Kafka consumer가 `tenant_id` 없는 이벤트를 DLQ로 라우팅하고 `outbox.dlq.size` 메트릭이 증가
- [ ] `specs/contracts/events/{auth,account,security}-events.md`에 `tenant_id` (string, required) 필드 명시
- [ ] `./gradlew :projects:global-account-platform:apps:security-service:check` PASS (unit)
- [ ] `./gradlew :projects:global-account-platform:apps:security-service:integrationTest` PASS

---

# Related Specs

> Step 0: read `PROJECT.md`, `rules/common.md`, `rules/domains/saas.md`, `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md`.

- `specs/features/multi-tenancy.md` § "Isolation Strategy / 적용 범위 (서비스별)"
- `specs/features/multi-tenancy.md` § "Cross-Tenant Security Rules"
- `specs/features/abnormal-login-detection-v2.md` (현재 detection rule 사양)
- `specs/services/security-service/architecture.md`
- `specs/contracts/events/auth-events.md`
- `specs/contracts/events/account-events.md`
- `specs/contracts/events/security-events.md`

# Related Skills

- `.claude/skills/backend/event-driven-policy.md`
- `.claude/skills/backend/audit-trail-policy.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md` — payload schema 갱신 필요
- `specs/contracts/events/account-events.md` — payload schema 갱신 필요
- `specs/contracts/events/security-events.md` — 본 태스크의 신규 페이로드 추가

---

# Target Service

- `security-service` (primary)
- `auth-service`, `account-service` (publisher signature 갱신)

---

# Architecture

`specs/services/security-service/architecture.md` 준수. 변경 포인트:

- `infrastructure/persistence/`: JPA entity에 tenantId field 추가, Repository 메서드 시그니처 변경
- `domain/detection/`: `EvaluationContext`/`DetectionResult`에 tenantId 추가, 모든 rule 구현체가 tenantId 사용
- `infrastructure/cache/`: Redis key generator를 tenant-aware로 변경
- `consumer/`: `@KafkaListener` 진입에서 `tenant_id` 검증, 누락 시 DLQ 라우팅

---

# Implementation Notes

- **Backfill default**: 기존 row는 `'fan-platform'`로 backfill 후 NOT NULL 강제 (multi-tenant trait 도입 이전 데이터는 fan-platform 단일 테넌트 가정).
- **Redis key migration**: 기존 `velocity:{account_id}` 키는 TTL 만료(기본 1시간)로 자연 소멸시킨다 — 별도 migration 불필요. 변경 직후 일시적으로 false-negative 가능, 운영 메트릭으로 확인.
- **Signature breaking change**: `SecurityEventPublisher.publish*(...)` 시그니처에 `tenantId`를 추가. legacy 1-arg-less 오버로드를 deprecated로 두고 후속 cleanup task에서 제거.
- **Outbox events 페이로드**: 발행 시 `tenant_id`를 outbox `payload` JSON에 포함. publisher가 누락하지 않도록 `BaseEventPublisher.writeEvent` 시그니처에 `tenantId` 필수 인자를 추가하는 안도 검토 (별도 ADR).

---

# Edge Cases

- 기존 production 데이터에 `tenant_id`가 NULL인 row — migration의 backfill 단계에서 `'fan-platform'`으로 채운 뒤 NOT NULL 제약 추가 (2-step migration).
- `tenant_id`가 와일드카드 `'*'` (platform-scope event)인 경우 — 본 태스크에서는 reject. 후속 ADR에서 와일드카드 처리 정책 확정.
- 다른 테넌트의 동일 `account_id` 값(이론상 가능) — 모든 query가 `(tenant_id, account_id)` 복합 조건으로 동작하므로 충돌 없음.
- Redis fault: cache miss 시 detection이 false-negative 가능 — 기존 동작 유지 (in-memory fallback 없음).

---

# Failure Scenarios

- **publisher 변경 누락**: `AccountEventPublisher`/`AuthEventPublisher` 호출부 중 일부에서 `tenant_id`를 빠뜨리면 컴파일 단계에서 잡힘 (signature 변경으로). 빠진 호출부는 `SessionContext`로부터 `tenantId` 조회.
- **DLQ 폭주**: 신규 코드와 legacy publisher가 공존하는 배포 윈도우에 `tenant_id` 누락 메시지가 다수 DLQ로 쌓일 수 있음 — 배포 전 runbook에 alert threshold 조정 명시.
- **migration backfill 실패**: 거대한 `login_history` 테이블에서 single-statement UPDATE는 락 오래 잡음 — chunked migration 사용. 별도 RUNBOOK 참조.

---

# Test Requirements

- 단위 테스트:
  - `VelocityRule`, `GeoAnomalyRule`: tenantA 이벤트가 tenantB 임계치에 영향 없음
  - `SecurityEventPublisher`: `tenantId == null`이면 `IllegalArgumentException`
  - Repository finder: `(tenant_id, account_id)` predicate 적용 검증
- 통합 테스트 (`@Tag("integration")`):
  - 신규 migration 적용 후 schema 검증 (column NOT NULL, index 존재)
  - cross-tenant 누출 회귀: tenantA에서 50번 실패 → tenantB 동일 account의 detection은 fired되지 않음
  - DLQ 라우팅: `tenant_id` 없는 메시지 publish 시 DLQ에 도착 + `outbox.dlq.size` 메트릭 증가

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit tests added
- [ ] Integration tests added
- [ ] Tests passing (CI green)
- [ ] Event contracts updated (auth-events, account-events, security-events)
- [ ] specs/services/security-service/architecture.md 갱신 (tenant 인지 구간 명시)
- [ ] Migration 절차 RUNBOOK 작성 (chunked backfill 가이드)
- [ ] Ready for review
