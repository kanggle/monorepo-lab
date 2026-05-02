# Task ID

TASK-BE-258

# Title

GDPR 삭제 downstream 전파 계약 — `account.deleted(anonymized=true)` 소비 의무 정의

# Status

ready

# Owner

backend

# Task Tags

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

`specs/features/data-rights.md` 가 account-service 내 PII 마스킹 처리만 정의하고, `account.deleted(anonymized=true)` 가 발행될 때 외부 소비 서비스(WMS / ERP / SCM / MES / community / membership) 가 자체 시스템에서 무엇을 해야 하는지 정의한 contract 가 없다. GDPR/PIPA 컴플라이언스 전파 책임이 분산 설계 상 명시되지 않은 위험을 닫는다.

본 태스크는 contract 작성 + 1개 reference consumer (security-service) 구현 + 다른 소비자에 대한 implementation guideline 으로 구성된다.

완료 시점:

1. `specs/contracts/events/account-events.md` 의 `account.deleted` 이벤트 섹션에 **소비자 의무 (Consumer Obligations)** 표 추가.
2. 모든 GAP 내부 소비자 (security-service, community-service, membership-service, admin-service) 가 `account.deleted(anonymized=true)` 이벤트를 받았을 때 수행할 작업 명시.
3. security-service 에 reference 구현 추가: `account.deleted(anonymized=true)` 수신 시 `login_history` / `suspicious_events` / `account_lock_history` 의 PII 컬럼 (e.g. IP, device fingerprint) 을 마스킹.
4. external 소비자 (WMS / 향후 ERP/SCM/MES) 의 처리 의무는 TASK-BE-254 (consumer integration guide) 의 Phase 5 와 cross-link.

---

# Scope

## In Scope

- `specs/contracts/events/account-events.md` § `account.deleted` 갱신:
  - 신규 섹션 "Consumer Obligations" 추가
  - 표 형식: consumer service / 의무 행동 / SLA (e.g. "수신 후 24시간 이내 PII 마스킹 완료") / failure handling
- 4개 GAP 내부 소비자별 의무 명시:
  - **security-service**: `login_history`, `suspicious_events`, `account_lock_history` 의 IP / device fingerprint / user_agent 마스킹. `tenant_id`, `account_id` 는 보존 (감사 무결성).
  - **community-service**: 사용자 프로필 캐시 (있다면) 무효화. 작성한 콘텐츠는 author_name 만 마스킹 ("(deleted user)") 처리, content 본문은 GAP 책임이 아니므로 community 의 자체 정책.
  - **membership-service**: 멤버십 record 의 author 식별자 마스킹.
  - **admin-service**: `admin_actions.target_id` 는 그대로 유지 (audit 무결성), 단 운영자 UI 표시 시 "(deleted user)" 라벨링.
- security-service 에 reference 구현:
  - 신규 consumer: `AccountDeletedAnonymizedConsumer`
  - PII 마스킹 utility 활용 (libs/java-security/.../PiiMaskingUtils 이미 존재)
  - 마스킹 후 audit 레코드 발행: `security.pii.masked` 신규 이벤트
- 신규 outbox 이벤트:
  - `security.pii.masked` (account_id, tenant_id, masked_at, table_names[])
- TASK-BE-254 의 consumer integration guide 에 Phase 5 (이벤트 구독 — 사용자 라이프사이클) 의 GDPR 섹션 cross-link.

## Out of Scope

- community/membership-service 의 reference 구현 — FROZEN 정책. contract 에 의무만 명시하고 구현은 별도 (TASK-BE-253 처럼 FROZEN 예외 필요 시 후속).
- WMS / ERP 등 external 소비자 구현 — 각 프로젝트 책임. 본 contract 가 그들의 onboarding 계약.
- "right to access" / "right to rectification" 등 GDPR 의 다른 권리 — `data-rights.md` 의 다른 섹션에 별도 정의됨.
- PII 보존 기간 정책 — `retention.md` 책임.

---

# Acceptance Criteria

- [ ] `account-events.md` § `account.deleted` 에 "Consumer Obligations" 표 추가, 4개 GAP 내부 소비자 + external 소비자 일반 가이드 명시.
- [ ] security-service 의 `AccountDeletedAnonymizedConsumer` 구현, IP / device fingerprint / user_agent 컬럼 마스킹 확인.
- [ ] 마스킹 동작 검증 통합 테스트: `account.deleted(anonymized=true)` 발행 → 24시간 SLA 내 (테스트는 즉시) 마스킹 완료.
- [ ] `tenant_id`, `account_id` 는 감사 무결성을 위해 보존 (마스킹 안 함).
- [ ] `security.pii.masked` 신규 outbox 이벤트가 마스킹 완료 후 발행.
- [ ] `security-events.md` 에 `security.pii.masked` payload 정의 추가.
- [ ] TASK-BE-254 의 가이드 Phase 5 에 본 의무 cross-link.
- [ ] `./gradlew :projects:global-account-platform:apps:security-service:check` + `:integrationTest` PASS.

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification (특히 `regulated`, `audit-heavy`).

- `specs/features/data-rights.md`
- `specs/contracts/events/account-events.md` (확장 대상)
- `specs/contracts/events/security-events.md` (확장 대상)
- `specs/services/security-service/architecture.md`
- `specs/services/security-service/data-model.md`
- `specs/use-cases/abnormal-login-detection.md`
- TASK-BE-254 (consumer integration guide — cross-link 대상)
- `libs/java-security/.../pii/PiiMaskingUtils` (마스킹 utility)

# Related Skills

- `.claude/skills/backend/` event-driven / audit-trail / pii 관련

---

# Related Contracts

- `specs/contracts/events/account-events.md` (의무 표 추가)
- `specs/contracts/events/security-events.md` (`security.pii.masked` 신규)

---

# Target Service

- `security-service` (reference 구현)
- `account-service` (이벤트 발행 측, 변경 없음)

---

# Architecture

`specs/services/security-service/architecture.md` 준수. 변경 포인트:

- `infrastructure/messaging/`: `AccountDeletedAnonymizedConsumer` 신규 (`@KafkaListener` on `account-events` topic, filter `event_type=account.deleted` AND `anonymized=true`)
- `application/pii/`: `PiiMaskingService` (entity 별 마스킹 로직 캡슐화)
- `infrastructure/persistence/`: `LoginHistoryRepository` 등에 `maskPiiByAccountId(tenantId, accountId)` 메서드 추가 (UPDATE WHERE tenant_id, account_id)

---

# Implementation Notes

- **마스킹 vs 삭제**: 컬럼 자체를 NULL 또는 hashed value 로 update. row 자체는 삭제하지 않음 (감사 무결성). e.g. `ip_address = '0.0.0.0'`, `user_agent = 'REDACTED'`, `device_fingerprint = SHA256(account_id + 'salt')`.
- **idempotency**: 동일 `account.deleted(anonymized=true)` 이벤트가 중복 수신되어도 재마스킹 안전 (이미 마스킹된 row 는 변경 없음). consumer 단에서 idempotency key (`event_id`) 체크.
- **SLA "24시간 이내"**: contract 명시값. 실제 구현은 즉시 처리 (Kafka 소비 직후). SLA 는 운영 안정성 buffer.
- **`security.pii.masked` 이벤트의 의미**: GDPR/PIPA 컴플라이언스 audit trail. 규제 기관 또는 사용자가 "내 PII 가 모두 삭제되었는가" 질문 시 이 이벤트 (and TASK-BE-254 의 모든 consumer 에서 발행되는 동일 종류 이벤트) 를 집계하여 증명.
- **External 소비자 (WMS) 가 의무 미준수 시**: GAP 는 강제할 수 없음. contract + audit + 정기 컴플라이언스 리뷰 (외부 절차) 로 검증.

---

# Edge Cases

- **`account.deleted(anonymized=false)` (유예 기간 내)**: 이 이벤트는 마스킹 대상 아님. 유예 종료 후 발행되는 `anonymized=true` 만 처리.
- **마스킹 대상 row 가 없음**: 해당 account 가 security-service 에 한 번도 로그인 시도 안 한 경우 `login_history` 가 비어있음. UPDATE 0 rows → 정상.
- **이벤트 수신 시점 전 row 추가**: 마스킹 후에도 추가 이벤트가 들어와 row 생성 가능 (delete 후 누군가 동일 account_id 로 이벤트 재발행). idempotency key 로 차단되지만 race window 존재. → 별도 후속 정책 ("deleted account 의 신규 이벤트는 reject") 필요할 수 있음.
- **동일 tenant 내 다른 account 영향**: WHERE 절에 `(tenant_id, account_id)` 쌍을 사용하므로 cross-account 영향 없음.

---

# Failure Scenarios

- **마스킹 SQL 실패 (DB 가용성)**: Kafka consumer 실패 → DLQ. retry policy: 1시간 후 재시도, 5회 실패 시 alert. SLA "24시간 이내" 는 retry 시간 포함.
- **idempotency key 누락 race**: 동일 이벤트 동시 처리 시 두 consumer 가 마스킹 시도 → 두 번째는 이미 마스킹된 row 에 NO-OP. 안전.
- **`security.pii.masked` outbox 발행 실패**: 마스킹은 성공했으나 audit 이벤트 누락. → outbox pattern 으로 atomic. 별도 폴링 발행.

---

# Test Requirements

- 단위 테스트:
  - `PiiMaskingService`: 각 엔티티의 마스킹 결과 검증.
  - `AccountDeletedAnonymizedConsumer`: 이벤트 필터링 (anonymized=true 만 처리).
  - idempotency: 중복 이벤트 → 한 번만 마스킹.
- 통합 테스트 (`@Tag("integration")`):
  - end-to-end: account-service 에서 `account.deleted(anonymized=true)` 발행 → security-service 수신 → 마스킹 검증.
  - cross-tenant: tenantA 의 `account.deleted` 가 tenantB 의 row 에 영향 없음.
  - DLQ: 의도적 마스킹 실패 (DB 일시 차단) → DLQ 라우팅 + 재시도 후 성공.
  - `security.pii.masked` outbox 이벤트 발행 검증.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration tests added and passing
- [ ] `account-events.md`, `security-events.md` 갱신
- [ ] TASK-BE-254 가이드 Phase 5 갱신 (cross-link)
- [ ] `data-rights.md` 에 본 contract backlink
- [ ] CI green
- [ ] Ready for review
