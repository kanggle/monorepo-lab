# Task ID

TASK-PC-FE-043

# Title

`console-web` — scope the **감사·보안(audit)** read to the **active (switched) tenant** instead of the operator's home tenant. `queryAudit` defaults the producer `tenantId` query param to the active-tenant cookie (the GAP admin-service already enforces the dual-read effective-scope gate — home ∪ assignments — so a multi-tenant operator querying an assigned tenant is allowed). An explicit `tenantId` (SUPER_ADMIN cross-tenant) still overrides.

# Status

done

# Owner

frontend-engineer (console-web consumer wiring only — one default in `queryAudit`; NO producer / contract / admin-service change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **follows**: ADR-MONO-020 (operator↔multi-customer N:M + active-tenant token scoping); TASK-MONO-174 (audit leg made reliable). The producer `AuditQueryUseCase` (TASK-BE-249 + TASK-BE-326 dual-read) already accepts a `tenantId` and gates it against the operator's effective tenant scope.
- **root cause**: the console audit consumer sent `tenantId` **only** when explicitly passed (never the active tenant), so `AuditQueryUseCase` defaulted the query to the operator's **home** tenant — the 감사·보안 page did not follow the tenant switcher (acme vs globex showed identical, home-scoped results). admin-service does not read `X-Tenant-Id` for audit; the scope is the `tenantId` query param.
- **no dependency on**: any admin-service / contract / ADR change. The producer already supports + gates `tenantId`. Single consumer-side default.

# Goal

Selecting a tenant in the switcher re-scopes the 감사·보안 view to that tenant: an operator assigned to acme + globex sees acme's audit rows under acme and globex's under globex (each gated producer-side by the effective scope); a SUPER_ADMIN platform operator viewing a specific tenant sees that tenant (cross-tenant `*` only when no customer is selected).

# Scope

## In Scope

- **`features/audit/api/audit-api.ts`** `queryAudit`: default the query `tenantId` to the resolved active tenant — `buildQuery({ ...params, tenantId: params.tenantId ?? tenant })` (the function already resolves `tenant = getActiveTenant()` for the `X-Tenant-Id` header and blocks when none). Covers BOTH entry points that call `queryAudit`: the server initial load (`getAuditListState`) and the client refetch (`/api/audit` proxy → `queryAudit`). JSDoc updated (tenantId now defaults to the active tenant; explicit overrides for SUPER_ADMIN cross-tenant).
- **`tests/unit/audit-api.test.ts`**: update the "omits tenantId unless explicitly supplied" case → "defaults `tenantId` to the active-tenant cookie; an explicit `tenantId` overrides".

## Out of Scope

- 운영자 관리(operators) tenant-scoping — needs an admin-service producer change (the list endpoint has no tenant filter) + a contract update; tracked as a separate (cross-project) task.
- Any globex demo-data seeding — globex audit populates from real activity (each globex audit view + tenant action writes a globex-tagged `admin_actions` row). A richer demo seed is optional and folded into the operators task.
- The producer effective-scope gate / `AuditQueryUseCase` — unchanged (already correct).
- A cross-tenant `*` UI affordance — not added here (platform operators still get `*` only when no customer tenant is active).

# Acceptance Criteria

- [x] **AC-1** With a multi-tenant operator, switching the tenant switcher re-scopes the 감사·보안 view: the producer receives `tenantId=<active tenant>` and returns that tenant's rows (verified by `audit-api.test.ts` — `tenantId` defaults to the active-tenant cookie).
- [x] **AC-2** An explicit `tenantId` (SUPER_ADMIN cross-tenant) still overrides the default (verified by the same test).
- [x] **AC-3** No producer/contract/admin-service change; the producer's effective-scope gate (403 `TENANT_SCOPE_DENIED` for an out-of-scope tenant) is unchanged and still surfaced inline.
- [x] **AC-4** `pnpm test` 790/790 + `tsc --noEmit` exit 0 + `next lint` clean + `next build` success; local console-web rebuilt + recreated.

# Related Specs

- `console-integration-contract.md` § 2.4.2 (audit read; the consumer attaches the operator token + tenant) + GAP `admin-api.md` § `GET /api/admin/audit` (`tenantId` query param + tenant-scope enforcement, TASK-BE-249) + ADR-MONO-020 (active-tenant scoping; dual-read effective scope, TASK-BE-326). No spec change — this aligns the consumer with the already-specified producer behavior.

# Edge Cases

- **No active tenant**: `queryAudit` already blocks with `NO_ACTIVE_TENANT` before the fetch (the page renders the "select a tenant" gate) — unchanged.
- **SUPER_ADMIN platform operator (home `*`)**: active tenant `*` → `tenantId=*` → cross-tenant view; active = a selected customer → that tenant (producer `isPlatformScope` allows it via `searchCrossTenant`).
- **Out-of-scope tenant**: cannot occur from the switcher (it only offers the operator's registry-scoped tenants); if forced, the producer returns 403 `TENANT_SCOPE_DENIED` → inline actionable (unchanged).
- **globex sparsity**: globex audit may be sparse until activity accrues — correct scoping, not a failure.

# Failure Scenarios

- If the default leaked an out-of-scope tenant, the producer's effective-scope gate (dual-read) rejects it (403) — no cross-tenant data leak. The switcher never offers an out-of-scope tenant anyway.

# Test Requirements

- `tests/unit/audit-api.test.ts`: `tenantId` defaults to the active-tenant cookie; explicit `tenantId` overrides.
- `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- Local rebuild + container restart; switch acme↔globex and confirm 감사·보안 re-scopes.

# Definition of Done

- [x] `queryAudit` active-tenant default + JSDoc + test update.
- [x] `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- [x] Local federation-e2e `console-web` rebuilt + recreated (live :3000).
- [x] No producer/contract/admin-service change; diff confined to `audit-api.ts` + its test.
- [x] Task md + `INDEX.md` updated.
- [x] Reviewed + merged (impl PR #1067 squash `9d9ad0d6`, 3-dim verified; all CI GREEN, no transient).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 요청 "감사·운영자를 테넌트별로 스코핑" 의 **슬라이스 1(감사, 콘솔 전용)**. 운영자 관리는 producer+contract 변경이 필요해 별도 슬라이스 2. **메타: 감사 스코프는 `tenantId` 쿼리 파라미터(admin이 X-Tenant-Id 무시) — 콘솔이 활성 테넌트를 안 넘겨 home 고정이던 갭. producer(BE-249/BE-326 dual-read)는 이미 tenantId 수용+effective-scope 게이트 보유라 콘솔 1줄 기본값으로 해소. 서버 초기로드+/api/audit 프록시가 모두 queryAudit 경유라 단일 주입점.**
