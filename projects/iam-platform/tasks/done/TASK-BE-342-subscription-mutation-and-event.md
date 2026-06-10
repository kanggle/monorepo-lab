# Task ID

TASK-BE-342

# Title

ADR-MONO-023 § 3.3 step 2a (D1/D3/D4) — account-service subscription mutation surface + `tenant.subscription.changed` event. The entitlement plane gains its own writes (subscribe/suspend/resume/cancel) behind the `SubscriptionStatus` state-machine guard, each mutation emitting a `tenant.subscription.changed` outbox event in the same transaction. Internal HTTP surface only; the operator-facing RBAC gate (`subscription.manage`) + audit is step 2b (admin-service), which delegates here.

# Status

done

> **완료 (2026-06-10)**: impl PR #1242 (squash `7cf29a2e685373e0bb903513dea7975cad5e6a02`). ADR-MONO-023 § 3.3 step 2a (D1/D3/D4) — account-service 구독 mutation 표면 + `tenant.subscription.changed` outbox 이벤트. 계약 선행(account-events.md v1 + account-tenant-domain-subscriptions.md POST/PATCH). 코드: `create`/`changeStatus`(상태머신 가드) + 예외 3종(404/409/409) + JPA `fromDomain`/`findByTenantIdAndDomainKey` + repository save/find + event publisher + mutation use-case(가드→저장→발행 1tx) + 내부 POST/PATCH 컨트롤러. **D2: account-service는 IAM 미접근**(operator 인가는 2b 위임). 검증: 컴파일 + mutation 단위 11케이스 + 슬라이스 6케이스(POST/PATCH/409 추가) + 실 MySQL8 IT(write round-trip + V0021 CHECK가 SUSPENDED/CANCELLED/PENDING 수용 + SUSPENDED는 ACTIVE 역조회 제외) — account-service 400 tests/0 fail. **CI 회귀 1건 자체적발·수정**(기존 컨트롤러 @WebMvcTest가 새 의존성으로 깨짐→@MockitoBean 추가, c0a6f9b0). 3차원 ✓(20 pass/0 fail, MERGED `7cf29a2e`/origin tip 일치). **후속**: TASK-BE-343(step 2b admin-service `subscription.manage` + `/api/admin/subscriptions` 위임). 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend

# Task Tags

- backend
- account-service
- adr
- multi-tenant
- event

---

# Dependency Markers

- **depends on**: TASK-BE-341 (SubscriptionStatus + V0021, merged #1240 `be790137`) — this consumes the state machine + the CHECK-constrained column.
- **prerequisite for**: TASK-BE-343 (step 2b — admin-service `subscription.manage` + `/api/admin/subscriptions` controller delegating to the internal endpoints here) and the step-3 plane-separation proof IT.
- **D2 (ADR-023)**: account-service performs the entitlement write + event; it does NOT read the IAM plane (admin_db RBAC/assignments). The operator authorization decision is made upstream in admin-service (2b) and delegated here.

# Goal

Give the entitlement authority (account-service) a guarded, event-emitting mutation surface so a subscription can be created, suspended, resumed, and cancelled — the writes ADR-019 D2 deferred. The state-machine guard (ADR-023 D1) + the `tenant.subscription.changed` event (ADR-023 D4) make non-ACTIVE transitions reflect in the catalog + `entitled_domains` at the next read/issuance with no extra wiring (ADR-023 D2).

# Scope

**Contracts (first):**
- `specs/contracts/events/account-events.md` — NEW `tenant.subscription.changed` event (schema v1; aggregate `TenantDomainSubscription`, id `<tenantId>:<domainKey>`; payload eventId/tenantId/domainKey/previousStatus(nullable)/currentStatus/reason?/actorType/actorId?/occurredAt; D2 plane-separation consumer note).
- `specs/contracts/http/internal/account-tenant-domain-subscriptions.md` — NEW `POST` (subscribe) + `PATCH /{tenantId}/{domainKey}` (transition) sections + error table (400/401/404/409) + D2 note.

**Code (account-service):**
- `domain/tenant/TenantDomainSubscription` — `create()` factory + `changeStatus()` (guard via `SubscriptionStatus.canTransitionTo`).
- NEW `domain/tenant/IllegalSubscriptionTransitionException` (→409 `SUBSCRIPTION_TRANSITION_INVALID`).
- NEW `application/exception/SubscriptionNotFoundException` (→404) + `SubscriptionAlreadyExistsException` (→409).
- `infrastructure/persistence/TenantDomainSubscriptionJpaEntity` — `fromDomain()` write mapper.
- `…JpaRepository` — `findByTenantIdAndDomainKey()`; `domain/repository/TenantDomainSubscriptionRepository` + impl — `save()` + `findByTenantIdAndDomainKey()`.
- NEW `application/event/TenantDomainSubscriptionEventPublisher` (`tenant.subscription.changed`, outbox, `@Transactional`).
- NEW `application/result/SubscriptionMutationResult` + `application/service/TenantDomainSubscriptionMutationUseCase` (subscribe/changeStatus; guard → save → publish in one tx).
- `presentation/internal/TenantDomainSubscriptionController` — POST + PATCH + DTOs.
- `presentation/advice/GlobalExceptionHandler` — 3 handlers (404/409/409); non-creatable create-status → 400 via existing `IllegalArgumentException` mapping.

**Tests:**
- NEW `TenantDomainSubscriptionMutationUseCaseTest` (subscribe success/PENDING/unknown-tenant/already-exists/non-creatable; changeStatus suspend+event/resume/cancel/not-found/illegal-transition).
- `TenantDomainSubscriptionJpaRepositoryTest` — NEW MutationPersistence nested (save+find round-trip; V0021 CHECK accepts SUSPENDED/CANCELLED/PENDING; SUSPENDED excluded from ACTIVE reverse-lookup).

# Acceptance Criteria

- **AC-1** subscribe/suspend/resume/cancel work via the internal endpoints; every mutation emits `tenant.subscription.changed` in the same `@Transactional` boundary.
- **AC-2** The `SubscriptionStatus` guard rejects illegal transitions (409) and a missing subscription (404); subscribe on an existing pair (409) and on an unknown tenant (404); non-creatable create-status (400).
- **AC-3** Read paths unchanged: GET still returns ACTIVE only; a SUSPENDED/CANCELLED subscription is absent from the catalog + `entitled_domains` reverse-lookup (verified by IT).
- **AC-4** account-service reads NO IAM data (D2 one-way dependency).
- **AC-5** Compile clean; mutation unit test + the subscription Testcontainers IT (write path + V0021 CHECK accepts all 4 states) GREEN.

# Related Specs

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` § D1 / D3 / D4 / D6 step 2
- `specs/contracts/events/account-events.md` (the event added)
- `specs/contracts/http/internal/account-tenant-domain-subscriptions.md` (the mutation surface added)

# Related Contracts

- `specs/contracts/events/account-events.md`
- `specs/contracts/http/internal/account-tenant-domain-subscriptions.md`

# Edge Cases

- A re-subscribe of a CANCELLED (terminal) pair is blocked by the PK + `SubscriptionAlreadyExistsException` (re-activating a cancelled subscription is a separate future decision — out of scope).
- `previousStatus = null` is intentional on create (event payload).
- The mutation event aggregate is `TenantDomainSubscription` (id `<tenantId>:<domainKey>`), NOT `Account` — the account_id partition-key convention does not apply.

# Failure Scenarios

- If a mutation does not publish the event (or publishes outside the tx) → a consumer could miss the change; the event MUST ride the same `@Transactional` as the save (outbox pattern).
- If account-service starts reading admin_db RBAC to authorize → D2 violation; authorization stays in admin-service (2b).
