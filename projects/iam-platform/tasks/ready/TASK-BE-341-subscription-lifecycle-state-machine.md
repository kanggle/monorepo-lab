# Task ID

TASK-BE-341

# Title

ADR-MONO-023 § 3.3 step 1 (D1) — subscription lifecycle state machine (net-zero). Introduce a dedicated `SubscriptionStatus` enum (`PENDING`/`ACTIVE`/`SUSPENDED`/`CANCELLED`) + a transition guard for `tenant_domain_subscription`, distinct from the tenant aggregate's `TenantStatus`, and pin the state set with a DB CHECK constraint. Behaviorally net-zero: the catalog (ADR-019 D4) and `entitled_domains` (ADR-019 D5 / ADR-020 D3) read paths filter `ACTIVE` and are byte-identical.

# Status

ready

# Owner

backend

# Task Tags

- backend
- account-service
- adr
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-023 ACCEPTED (#1238 squash `662ff859`) — this is its § 3.3 step 1.
- **prerequisite for**: TASK-BE-342 (step 2 — subscription admin API + `subscription.manage` + `tenant.subscription.changed` event) and the step-3 plane-separation proof IT.
- **must preserve**: ADR-019 D4 catalog read (`ConsoleRegistryUseCase`) + ADR-019 D5 / ADR-020 D3 `entitled_domains` read (`TenantClaimTokenCustomizer` → account-service `GET /internal/tenant-domain-subscriptions`) — both filter ACTIVE; behavior byte-identical.

# Goal

Formalize the subscription `status` column (ADR-019 D2 left it a bare `VARCHAR(10) DEFAULT 'ACTIVE'` with no state set) as the ADR-023 D1 lifecycle state machine, so step 2 can implement subscribe/suspend/resume/cancel against a guarded, DB-constrained state model — without changing any current behavior (every existing row is ACTIVE).

# Scope

- **NEW** `account-service/.../domain/tenant/SubscriptionStatus.java` — enum `PENDING`/`ACTIVE`/`SUSPENDED`/`CANCELLED` + `isActive()` / `isTerminal()` / `canTransitionTo(target)` guard (PENDING→ACTIVE|CANCELLED; ACTIVE→SUSPENDED|CANCELLED; SUSPENDED→ACTIVE|CANCELLED; CANCELLED terminal; self/null rejected) + `creatable()`.
- `account-service/.../domain/tenant/TenantDomainSubscription.java` — `status` field + `reconstitute` param + `isActive()`: `TenantStatus` → `SubscriptionStatus`.
- `account-service/.../infrastructure/persistence/TenantDomainSubscriptionJpaEntity.java` — `@Enumerated(STRING)` field type → `SubscriptionStatus`.
- `account-service/.../infrastructure/persistence/TenantDomainSubscriptionJpaRepository.java` — `findByStatus` / `findByStatusAndTenantId` param type → `SubscriptionStatus`.
- `account-service/.../infrastructure/persistence/TenantDomainSubscriptionRepositoryImpl.java` — `TenantStatus.ACTIVE` → `SubscriptionStatus.ACTIVE`.
- **NEW** `account-service/.../resources/db/migration/V0021__tenant_domain_subscription_status_check.sql` — `CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','CANCELLED'))` (net-zero; existing rows all ACTIVE).
- Tests: **NEW** `SubscriptionStatusTest` (transition guard); `TenantDomainSubscriptionQueryUseCaseTest` + `TenantDomainSubscriptionJpaRepositoryTest` — `TenantStatus` → `SubscriptionStatus` (subscription rows only; tenant-lifecycle tests keep `TenantStatus`).
- `TenantStatus` enum **unchanged** (still `ACTIVE`/`SUSPENDED` for the tenant aggregate).

# Acceptance Criteria

- **AC-1** `SubscriptionStatus` enum exists with the 4 states + a transition guard matching ADR-023 D1; unit-tested (legal/illegal/self/null/terminal).
- **AC-2** The subscription domain object + JPA entity + repository use `SubscriptionStatus`; `TenantStatus` is no longer referenced by any subscription path.
- **AC-3** V0021 adds the CHECK constraint; Flyway applies V0019→V0020→V0021 cleanly on real MySQL (Testcontainers IT GREEN).
- **AC-4** **Net-zero**: catalog + `entitled_domains` read paths are unchanged in behavior; the reverse-lookup IT (`acme-corp → {finance,wms}`, `wms → {wms}`) still passes.
- **AC-5** Compilation clean; `:account-service:test` GREEN (unit + the subscription Testcontainers IT where Docker is available).

# Related Specs

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` § D1 / § D6 step 1
- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` § D2 (the `status` column this formalizes)
- `projects/iam-platform/specs/features/multi-tenancy.md` (tenant model)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal-api.md` (the `GET /internal/tenant-domain-subscriptions` read surface — unchanged shape; ACTIVE filter preserved)

# Edge Cases

- `TenantStatus` must NOT gain `CANCELLED`/`PENDING` — the subscription lifecycle is a distinct enum; conflating the two would imply tenants can be CANCELLED (wrong).
- The DB stores status as STRING; switching the JPA enum type is net-zero at the data layer (existing values 'ACTIVE' unchanged).
- MySQL 8.0.16+ enforces CHECK; the Testcontainers image is `mysql:8.0` (supported).

# Failure Scenarios

- If a read path starts returning non-ACTIVE subscriptions (catalog/entitled_domains regression) → net-zero violated; the ACTIVE filter must be preserved.
- If V0021 fails to apply because an existing row holds a value outside the allowed set → investigate the seed; (none expected — all seeds are ACTIVE).
