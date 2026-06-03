# Task ID

TASK-MONO-175

# Title

Scope **운영자 관리(operator list)** to the active (switched) tenant — cross-project. GAP `admin-service` `GET /api/admin/operators` gains a `tenantId` query param (mirror the audit endpoint) and returns operators **belonging to** that tenant (HOME `admin_operators.tenant_id == tenantId` **OR** an `operator_tenant_assignment` to it), gated by the caller's dual-read effective scope (home ∪ assignments). `console-web` sends the active tenant as `tenantId`. Platform-scope (`'*'`) callers listing `'*'` keep the unscoped cross-tenant view.

# Status

ready

# Owner

backend-engineer (admin-service producer + contract) + frontend-engineer (console consumer) — one atomic cross-project PR (CLAUDE.md § Cross-Project Changes)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api

---

# Dependency Markers

- **follows**: ADR-MONO-020 (operator↔multi-customer N:M + active-tenant scoping; TASK-BE-326 `TenantScopeResolver` dual-read), TASK-BE-249 (the audit `tenantId` + `TENANT_SCOPE_DENIED` precedent this mirrors), TASK-PC-FE-043 (the audit slice 1 of the same user request).
- **root cause**: `GET /api/admin/operators` (`OperatorQueryService.listOperators`) had **no tenant dimension** — it returned the global operator list regardless of the active tenant, so 운영자 관리 did not follow the tenant switcher (acme and globex showed identical lists). The console already sent `X-Tenant-Id` but the endpoint ignored it.
- **decision (user, 2026-06-03)**: scope operators to the active tenant (option A), NOT leave it global. The effective-scope gate makes it strictly more restrictive (no privilege widening on this privilege-sensitive surface).
- **no dependency on**: any new migration (the `operator_tenant_assignment` table + `admin_operators.tenant_id` already exist) or ADR change (additive `tenantId` param + tenant-scope semantics, mirroring audit).

# Goal

Selecting a tenant in the switcher re-scopes the 운영자 관리 list to operators of that tenant (home ∪ assignment). An operator assigned to acme + globex sees acme's operators under acme and globex's under globex; a platform operator viewing a specific customer sees that customer's operators (`'*'` → cross-tenant). An out-of-scope tenant is rejected `403 TENANT_SCOPE_DENIED`.

# Scope

## In Scope

**admin-service (producer):**
- `AdminOperatorJpaRepository.findByTenantScope(tenantId, status, pageable)` — `@Query` (+ explicit `countQuery`): `o.tenantId = :tenantId OR EXISTS (operator_tenant_assignment a WHERE a.operatorId = o.id AND a.tenantId = :tenantId)`, optional `status`, sort/paging from `Pageable`.
- `AdminOperatorPort.findOperatorsPageByTenant(...)` + `JpaAdminOperatorAdapter` impl.
- `OperatorQueryService.listOperators(status, page, size, callerOperatorId, requestedTenantId)` — resolve caller home tenant + `TenantScopeResolver` effective scope; gate non-platform callers (`TenantScopeDeniedException` for out-of-scope); route platform `'*'`→`'*'` to the unscoped `findOperatorsPage`, else `findOperatorsPageByTenant`.
- `OperatorAdminController.listOperators` — `@RequestParam(required=false) String tenantId` + `OperatorContextHolder.require().operatorId()`.
- Unit tests: `OperatorQueryServiceTest` (scoped path + effective-scope gate + existing platform-caller routing), `OperatorAdminControllerTest` (stub signature).

**contract:** `specs/contracts/http/admin-api.md` `GET /api/admin/operators` — `tenantId` param + tenant-scope semantics + `403 TENANT_SCOPE_DENIED` row (mirror the audit section).

**console-web (consumer):**
- `features/operators/api/operators-api.ts` `listOperators` — send `tenantId = getActiveTenant()` (mirror the audit `tenantId` default). No other console change (operators-state / OperatorsScreen already drive the call; the switch already re-runs the server component via PC-FE-040 `router.refresh()`).

## Out of Scope

- A dedicated Testcontainers IT for the `findByTenantScope` SQL — covered by (a) Spring Data validating the `@Query` at admin-service context load (a parse error fails startup/CI), (b) the live demo (acme=2 / globex=1 operators against real MySQL), (c) unit-level scoping/gate tests. A focused `@DataJpaTest` is a reasonable follow-up but not required here.
- Scoping the operator **mutations** (create/role/status) differently — they already carry tenant context (`body.tenantId` / per-row); unchanged.
- New demo seed — the existing `multi-operator` (home=acme, assigned acme+globex) + `acme-corp-operator` (home=acme) already make acme=2 / globex=1 demonstrable.
- A cross-tenant `'*'` UI affordance.

# Acceptance Criteria

- [ ] **AC-1** `GET /api/admin/operators?tenantId=X` returns operators whose home tenant == X OR who are assigned to X; `status`/paging/sort preserved (unit + live).
- [ ] **AC-2** A non-platform caller requesting a tenant outside their effective scope (home ∪ assignments) → `403 TENANT_SCOPE_DENIED` (unit test).
- [ ] **AC-3** A platform (`'*'`) caller listing `'*'` → unscoped cross-tenant list (existing behavior preserved; unit test routes to `findOperatorsPage`).
- [ ] **AC-4** console-web sends the active tenant as `tenantId`; switching acme↔globex re-scopes 운영자 관리 (acme=2 / globex=1 in the demo).
- [ ] **AC-5** admin-service unit tests green (`OperatorQueryServiceTest` + `OperatorAdminControllerTest`); console `pnpm test` green; `tsc`/`lint`/`build` green; admin-service boots healthy (validates the `@Query`). Contract updated.

# Related Specs

- GAP `admin-api.md` § `GET /api/admin/operators` (this task) + § `GET /api/admin/audit` (the `tenantId` precedent). ADR-MONO-020 (active-tenant scoping; `TenantScopeResolver` dual-read). `console-integration-contract.md` § 2.4.3 (operators consumer).

# Edge Cases

- **Platform caller, specific tenant**: `'*'` operator viewing acme → `findOperatorsPageByTenant("acme", …)` (sees acme's operators), gate short-circuits (platform allowed).
- **No active tenant**: console blocks with `NO_ACTIVE_TENANT` before any fetch (unchanged).
- **`'*'` operators in a specific-tenant list**: a platform-home (`'*'`) operator does NOT match a specific tenant's home/assignment, so it appears only under the `'*'` view — intended.
- **Assignment-only membership**: `multi-operator` (home=acme) appears under globex via the `operator_tenant_assignment` EXISTS branch.

# Failure Scenarios

- A broken `@Query` JPQL would fail admin-service context load (caught at startup/CI), not silently mis-scope.
- The effective-scope gate prevents a caller from listing a tenant outside their scope — no cross-tenant operator enumeration (privilege-surface safety; more restrictive than the prior global list).

# Test Requirements

- admin-service: `OperatorQueryServiceTest` (scoped + gate + platform routing), `OperatorAdminControllerTest` (signature). `./gradlew :projects:global-account-platform:apps:admin-service:test`.
- console: `operators-api` list still green; `pnpm test` + `tsc` + `lint` + `build`.
- Local: rebuild admin-service + console-web; admin-service healthy; switch acme↔globex → operator list re-scopes (acme=2 / globex=1).

# Definition of Done

- [ ] Producer (repository/port/adapter/use-case/controller) + contract + console consumer + unit tests.
- [ ] admin-service unit tests + console `pnpm test`/`tsc`/`lint`/`build` green; admin-service boots healthy.
- [ ] Local admin-service + console-web rebuilt + recreated (live :3000); acme=2 / globex=1 operators.
- [ ] One atomic cross-project PR (admin-service + GAP contract + console).
- [ ] Task md + root `tasks/INDEX.md` updated.
- [ ] Reviewed + merged (3-dim verified; all CI GREEN).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 요청 "감사·운영자를 테넌트별로 스코핑"의 **슬라이스 2(운영자)**. 사용자 선택(2026-06-03)=활성 테넌트로 스코핑(전역 유지 아님). audit(슬라이스 1, PC-FE-043)과 동일 패턴: `tenantId` 파라미터 + effective-scope 게이트 + 플랫폼 '*' 전역. 운영자 목록은 producer 에 테넌트 차원이 아예 없던 게 RC → repository `findByTenantScope`(home OR assignment EXISTS) 신설. **메타: privilege-escalation surface 라 effective-scope 게이트로 더 제한적이게(권한 비확장) + contract-first 갱신. cross-project atomic PR(admin-service+contract+console).**
