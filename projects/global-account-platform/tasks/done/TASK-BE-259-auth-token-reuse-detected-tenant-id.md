# Task ID

TASK-BE-259

# Title

`auth.token.reuse.detected` 이벤트 payload 에 `tenant_id` 추가 (P3 cleanup)

# Status

ready

# Owner

backend

# Task Tags

- event

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

`specs/contracts/events/auth-events.md` 의 다른 모든 이벤트 (`auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`, `auth.token.refreshed`, `auth.token.tenant.mismatch`, `auth.session.created`, `auth.session.revoked`) 는 payload 에 `tenant_id` 필드를 포함하지만, **`auth.token.reuse.detected` 만 누락** 된 일관성 갭을 닫는다.

multi-tenant 환경에서 보안 이벤트를 tenant 단위로 집계·알람하기 위해 필수.

완료 시점:

1. `auth.token.reuse.detected` payload 스키마에 `tenant_id` (string, required) 추가.
2. publisher (`AuthEventPublisher`) 호출부에서 `tenant_id` 를 항상 전달.
3. consumer (`security-service`) 가 `tenant_id` 를 활용해 per-tenant detection 수행 (TASK-BE-248 시리즈와 정합).
4. schema version bump (또는 backward-compatible 추가, decision in Implementation Notes).

---

# Scope

## In Scope

- `specs/contracts/events/auth-events.md` § `auth.token.reuse.detected` payload 갱신:
  - `tenant_id` (string, required) 필드 추가
  - schema_version 1 → 2 (또는 1.0 → 1.1, 호환성 정책에 따라)
- publisher 변경:
  - `auth-service` 의 `AuthEventPublisher.publishTokenReuseDetected(...)` 시그니처에 `tenantId` 추가
  - 호출부 전수 갱신 (refresh token 회전 / reuse 탐지 경로)
- consumer 변경:
  - `security-service` 의 해당 이벤트 처리 로직에서 `tenant_id` 활용
  - per-tenant counter (Redis `reuse:{tenant_id}:{account_id}`) 적용
- 통합 테스트: `tenant_id` 누락 시 reject 또는 fallback 처리 검증.

## Out of Scope

- 다른 이벤트 (`auth.token.refreshed` 등) 의 schema 변경 — 이미 `tenant_id` 포함됨.
- TASK-BE-248 의 광범위 변경 — 본 태스크는 해당 단일 이벤트만 대상.
- 외부 consumer 통지 — 본 이벤트의 consumer 는 GAP 내부 (security-service) 만.

---

# Acceptance Criteria

- [ ] `auth-events.md` § `auth.token.reuse.detected` payload 에 `tenant_id` 명시.
- [ ] schema_version 갱신 명시 (예: v1 → v2, 또는 명세 첫 줄 "schema_version: 1.1").
- [ ] `AuthEventPublisher.publishTokenReuseDetected(...)` 시그니처에 `tenantId` 추가, 호출부 전수 갱신.
- [ ] consumer (`security-service`) 의 reuse 탐지 카운터가 tenant scope.
- [ ] cross-tenant 회귀: tenantA 의 reuse 이벤트 50건이 tenantB account 의 detection 임계치에 영향 없음.
- [ ] `tenant_id` 누락된 메시지가 DLQ 로 라우팅 (TASK-BE-248 의 정책과 정합).
- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:check`, `:security-service:check`, `:integrationTest` PASS.

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification.

- `specs/contracts/events/auth-events.md` (확장 대상)
- `specs/services/auth-service/architecture.md`
- `specs/services/security-service/architecture.md`
- `specs/features/multi-tenancy.md` § "Cross-Tenant Security Rules"
- `tasks/ready/TASK-BE-248-security-service-tenant-events.md` (정합 대상 — 동일 패턴)

# Related Skills

- `.claude/skills/backend/` event-driven 관련

---

# Related Contracts

- `specs/contracts/events/auth-events.md`

---

# Target Service

- `auth-service` (publisher), `security-service` (consumer)

---

# Architecture

- `auth-service`: `infrastructure/event/AuthEventPublisher.publishTokenReuseDetected(...)` 시그니처 변경.
- `security-service`: 해당 consumer 의 `EvaluationContext` 에 `tenantId` 추가.

---

# Implementation Notes

- **Schema version 정책**:
  - Option 1: schema_version bump (1 → 2). 모든 consumer 가 동시에 업그레이드 필요.
  - Option 2: backward-compatible 추가 — `tenant_id` optional 필드로 추가, 점진적 enforce.
  - 권장: **Option 1**. 본 이벤트의 consumer 는 GAP 내부 단일 (security-service) 이므로 동시 업그레이드 가능.
- **TASK-BE-248 와의 관계**: TASK-BE-248 가 다른 이벤트들의 tenant 인지 정합을 처리한다. 본 태스크는 그 작업에서 누락되었던 단일 이벤트의 보완. 248 가 아직 머지 전이면 248 안에 흡수 가능 — INDEX 갱신 시 결정.
- **호출부 누락 컴파일 검증**: 시그니처에 required 인자로 추가하면 호출부 전수 컴파일 단계에서 적발.

---

# Edge Cases

- **현재 운영 중인 reuse 탐지 시나리오**: 본 변경은 새 필드 추가 + tenant scope. 기존 글로벌 카운터 동작은 마이그레이션 (Redis 키 prefix 재설계) 필요. TTL 만료로 자연 소멸 가능 (1시간).
- **`tenant_id` claim 누락된 발급 토큰**: 발생 가능성 0 — 현재 모든 발급 토큰에 `tenant_id` claim 포함. 그러나 누락 시 발견 검증 (publisher 단계에서 IllegalArgumentException).
- **reuse 탐지 동시성**: 동일 tenant 내 동시 reuse 이벤트는 정상 (한 사용자가 두 device 에서 동시 토큰 사용). cross-tenant 가 아니라면 차단 사유 없음.

---

# Failure Scenarios

- **Publisher 호출부 누락**: 시그니처 변경으로 컴파일 실패 → 자동 적발.
- **Consumer 가 신규 schema 미인식**: 본 이벤트 consumer 는 단일 (security-service). 동시 deploy 필요.
- **Redis 키 마이그레이션 누락 (`reuse:{account_id}` → `reuse:{tenant_id}:{account_id}`)**: 변경 직후 짧은 기간 false-negative 가능 (TTL 만료까지). 운영 알람 임계치 일시 완화 권장.

---

# Test Requirements

- 단위 테스트:
  - `AuthEventPublisher.publishTokenReuseDetected`: `tenantId == null` → IllegalArgumentException.
  - `security-service` reuse counter: tenant scope 검증.
- 통합 테스트 (`@Tag("integration")`):
  - End-to-end: refresh token reuse 시뮬레이션 → tenant 별 카운터 분리 검증.
  - cross-tenant 회귀: tenantA reuse 50회 → tenantB 동일 account 의 detection 임계치 영향 없음.
  - DLQ: `tenant_id` 누락 메시지 publish → DLQ.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration tests added and passing
- [ ] `auth-events.md` 갱신
- [ ] CI green
- [ ] Ready for review
