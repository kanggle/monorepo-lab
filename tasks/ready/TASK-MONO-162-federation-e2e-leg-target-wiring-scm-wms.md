# Task ID

TASK-MONO-162

# Title

ADR-MONO-020 D4 **federation-e2e leg-target wiring completion** — the console-bff operator-overview **WMS** and **SCM** legs call endpoints that **no running e2e service serves** (the compose boots the wrong producers), masking both the A-side (wms) and blocking the B-side (scm). Wire the **correct** producers into `federation-hardening-e2e` (wms **admin-service** for `/api/v1/admin/dashboard/inventory`; scm **inventory-visibility-service** for the inventory snapshot), fix the stale BFF scm adapter path, complete the entitlement-trust dual-accept at each producer's **decode/role authz layer** (mirroring MONO-161), and seed their read-models — so the `tenant-switch-rescope.spec.ts` A↔B proof goes GREEN.

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- multi-tenant
- e2e
- devops

---

# Dependency Markers

- **completes**: TASK-MONO-158 (ADR-020 D4) federation-e2e `tenant-switch-rescope.spec.ts` **A↔B**. MONO-158/159/160 delivered the console assume-tenant mechanism (proven: the globex assumed token carries `tenant_id=globex-corp` + `entitled_domains=[scm,erp]`; the acme assumed token carries `[finance,wms]`). MONO-161 fixed the **erp** role/scope authz dual-accept. This task fixes the remaining two legs (**scm**, **wms**) whose blocker is NOT (only) authz but **the e2e booting the wrong producer service entirely**.
- **blocked-by**: none (all upstream mechanism merged: BE-326/BE-327/MONO-158/159/160/161).
- **unblocks the close of**: TASK-MONO-158 / 159 / 160 / 161 (all merged-but-unclosed in `tasks/ready/` pending the B-side GREEN proof). After this lands + the workflow run is GREEN, close all five per lifecycle.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (cross-domain auth + multi-service e2e infra; entitlement-trust must stay fail-closed + WRITE-unaffected; net-zero for existing tokens).

---

# Root Cause (definitive, file:line-verified)

The operator-overview composition (`OperatorOverviewCompositionUseCase.compose`) fires 5 legs. Two leg **targets** are mis-wired in `federation-hardening-e2e`:

### WMS leg
- BFF `WmsInventoryReadAdapter` calls `GET /api/v1/admin/dashboard/inventory` (`projects/platform-console/apps/console-bff/.../adapter/outbound/http/WmsInventoryReadAdapter.java:43`).
- That path is served by wms **admin-service** `InventoryDashboardController` (`projects/wms-platform/apps/admin-service/.../api/dashboard/InventoryDashboardController.java:19`), port **8086**, DB **`admin_db`** (PostgreSQL), `@PreAuthorize("hasRole('WMS_VIEWER')")` (line 20).
- But e2e runs **`wms-master-service`** (serves `/api/v1/master/warehouses`, DB `master_db`) and points `CONSOLE_BFF_OUTBOUND_WMS_BASE_URL=http://wms-master-service:8081` (`docker-compose.federation-e2e.yml:486`). master-service has no such handler → `NoResourceFoundException` → its catch-all `handleUnexpected` → **HTTP 500** (`projects/wms-platform/apps/master-service/.../advice/GlobalExceptionHandler.java:209`). The BFF classifies 500 → `degraded/DOWNSTREAM_ERROR` (not `forbidden`), so the A-side acme assertion (wms NOT forbidden) **passed coincidentally** while wms was actually broken.
- **Authz gap (same as erp/MONO-161)**: even pointed at the right service, admin-service's `jwtAuthenticationConverter` reads only `roles`/`role` claims, never `entitled_domains` (`projects/wms-platform/apps/admin-service/.../config/SecurityConfig.java:91-103`). The tenant gate `TenantClaimValidator` **already** dual-accepts (`isEntitled` at `.../infra/security/TenantClaimValidator.java:32`, used by `validate()` line 66), but `@PreAuthorize("hasRole('WMS_VIEWER')")` denies an acme token that is wms-**entitled** but carries no WMS role → 403.

### SCM leg
- BFF `ScmInventoryReadAdapter` calls `GET /api/scm/inventory/visibility` (`projects/platform-console/apps/console-bff/.../adapter/outbound/http/ScmInventoryReadAdapter.java:44`).
- **No producer serves that path.** scm `inventory-visibility-service` serves `GET /api/inventory-visibility/snapshot` (`projects/scm-platform/apps/inventory-visibility-service/.../adapter/inbound/web/InventoryVisibilityController.java:39,54`), port **8080**, DB **`scm_inventory_visibility`** (PostgreSQL). The scm `gateway-service` routes `Path=/api/v1/inventory-visibility/**` → rewrites to `/api/inventory-visibility/${segment}` (`projects/scm-platform/apps/gateway-service/.../application.yml:75-89`). **Neither** the gateway route prefix (`/api/v1/inventory-visibility/**`) **nor** the service path (`/api/inventory-visibility/...`) matches the BFF's `/api/scm/inventory/visibility` — the BFF adapter path is a **pure bug** (prod + e2e).
- e2e runs **`scm-procurement-service`** (serves `/api/procurement/po` only, DB `scm_procurement`) and points `CONSOLE_BFF_OUTBOUND_SCM_BASE_URL=http://scm-procurement-service:8080` (`docker-compose.federation-e2e.yml:487`) → 404 → `degraded`.
- **Authz gap (decode-time layer)**: inventory-visibility-service has a **two-layer** authz. Layer 2 — the `TenantClaimEnforcer` servlet filter — **already** dual-accepts `entitled_domains` (`.../adapter/inbound/web/filter/TenantClaimEnforcer.java:78-88`, `isEntitled` 102-116). But Layer 1 — the **JWT decode-time** `tenantClaimValidator` (`projects/scm-platform/apps/inventory-visibility-service/.../config/ServiceLevelOAuth2Config.java:67-83`) — checks **only** `tenant_id` equality (`expectedTenantId.equals(tenantId)`), **no** `isEntitled` branch. A globex token (`tenant_id=globex-corp`, `entitled_domains=[scm,erp]`) is **rejected at decode** (401/403) before the filter's dual-accept is ever reached. (This is the inverse-shape of MONO-161: there the gap was the role/scope layer; here it is the decode-time validator.)

> The stale compose header comments (`docker-compose.federation-e2e.yml:22-23,36-37`) describe a **MONO-139-era** wiring (`/api/v1/master/warehouses`, `/api/scm/purchase-orders`) that the current BFF adapters no longer call. Update them.

---

# Goal

Make the operator-overview **WMS** and **SCM** legs resolve against the **correct** producers in `federation-hardening-e2e`, with entitlement-trust honored at every authz layer, so that:
- **A-side (acme, entitled [finance,wms])**: wms card is genuinely **ok** (200, non-empty), not a masked 500/degraded.
- **B-side (globex, entitled [scm,erp])**: scm card is **ok/not-forbidden**; finance+wms correctly **forbidden**.

Fail-closed preserved; **WRITE stays scope/role-gated** (entitlement-trust grants READ visibility only); net-zero for SUPER_ADMIN/scope/role-bearing + machine tokens.

---

# Scope

## In scope

### 1. SCM producer wiring (`tests/federation-hardening-e2e/`)
- **Decide the production-faithful scm inventory path** by reading `projects/platform-console/specs/.../§ 2.4.6` (scm inventory-visibility contract) + the scm gateway route. Two acceptable topologies — pick one and **document the choice + rationale** in the task close note and the compose comment:
  - **(Recommended) direct-to-service** (consistent with the cohort — wms/finance/erp legs all call producer services directly, no gateway in this e2e): fix `ScmInventoryReadAdapter` path → `/api/inventory-visibility/snapshot`; add an `scm-inventory-visibility-service` container + an `scm-inv-postgres` (PostgreSQL `POSTGRES_DB=scm_inventory_visibility`, user/pass `scm`/`scm`) sidecar; point `CONSOLE_BFF_OUTBOUND_SCM_BASE_URL=http://scm-inventory-visibility-service:8080`. **If** §2.4.6 mandates the gateway prefix as the canonical BFF path, instead use the gateway-faithful topology below.
  - **(Alt) gateway-faithful**: fix `ScmInventoryReadAdapter` path → `/api/v1/inventory-visibility/snapshot`; add **both** the scm `gateway-service` and `inventory-visibility-service` containers (+ the postgres sidecar); point SCM base-url at the gateway. Only choose this if the contract makes the gateway path canonical for the BFF — it adds a Spring Cloud Gateway failure surface the other legs don't have.
- **Decode-time dual-accept**: extend `inventory-visibility-service` `ServiceLevelOAuth2Config.tenantClaimValidator` (`config/ServiceLevelOAuth2Config.java:67-83`) to mirror the filter's `isEntitled` — accept when `tenant_id == required` **OR** `entitled_domains ∋ "scm"` (claim read only from the RS256/JWKS-verified token; fail-closed on shape anomaly, exactly like `TenantClaimEnforcer.isEntitled`). The wildcard (`*`) handling stays as-is. Without this the globex token is rejected at decode before Layer 2 runs.
- **Seed**: add `tests/federation-hardening-e2e/fixtures/seed-scm-inv.sql` (the `scm_inventory_visibility` read-model — `inventory_nodes` + `inventory_snapshots` or whatever the snapshot endpoint reads; verify the schema from the service's Flyway `db/migration/inventory-visibility`). Apply it in the workflow against the new postgres sidecar.

### 2. WMS producer wiring (`tests/federation-hardening-e2e/`)
- Add a `wms-admin-service` container (build context `projects/wms-platform/apps/admin-service`, `SERVER_PORT=8086`, DB **`admin_db`**) + a `wms-admin-postgres` (PostgreSQL `POSTGRES_DB=admin_db`, user/pass `admin`/`admin`) sidecar — admin-service uses `admin_db`, **NOT** master-service's `master_db`, so it needs its own sidecar (do not reuse `wms-postgres`).
- Repoint `CONSOLE_BFF_OUTBOUND_WMS_BASE_URL=http://wms-admin-service:8086`. Decide whether `wms-master-service` is still needed by any other leg/spec in this cohort (the warehouses endpoint) — if nothing else uses it, you may leave it running (harmless) or remove it; **do not** remove anything another spec depends on. Document the call.
- **Role-layer dual-accept**: extend admin-service `SecurityConfig.jwtAuthenticationConverter` (`config/SecurityConfig.java:91-103`) so that when `TenantClaimValidator.isEntitled(jwt, "wms")` is true it grants `ROLE_WMS_VIEWER` (so `@PreAuthorize("hasRole('WMS_VIEWER')")` READ passes). **WRITE-gated roles** (`WMS_OPERATOR`/`WMS_ADMIN`/`WMS_SUPERADMIN`) are **unaffected** — synthesizing only `WMS_VIEWER` does not satisfy the stronger checks. Reuse the existing `TenantClaimValidator.isEntitled` helper.
- **(Defense-in-depth, optional but recommended)**: add a `NoResourceFoundException` → **404** handler to wms **master-service** `GlobalExceptionHandler` (`.../master/.../advice/GlobalExceptionHandler.java`, before the line-209 catch-all) so a future mis-route degrades cleanly (404→degraded) instead of masquerading as a 500. Low-risk; keep it scoped.
- **Seed**: add `tests/federation-hardening-e2e/fixtures/seed-wms-admin.sql` (the `admin_inventory_snapshot` read-model — verify columns from admin-service Flyway `V2__init_readmodel.sql`; the table is tenant-neutral so a single row suffices for a non-empty 200). Apply it against `wms-admin-postgres` in the workflow.

### 3. console-bff (`projects/platform-console/apps/console-bff/`)
- `ScmInventoryReadAdapter.java:44` path fix per the chosen scm topology (the only console-bff code change). Update its Javadoc to the real path. **No other BFF change** — the credential/tenant/composition logic is correct (PC-BE-007 proven).
- If the BFF has tests asserting the old scm path string, update them.

### 4. Workflow + compose hygiene
- `.github/workflows/federation-hardening-e2e.yml`: add the new service(s) to the **Phase 2** `docker compose up` list (lines ~236-239) and the new seed exec step(s) to **Phase 2.5** (lines ~311-326), mirroring the existing `seed-wms.sql`/`seed-scm.sql` psql exec pattern (new sidecars → new `psql -U <user> -d <db>` exec lines).
- Update the stale compose header comments (`docker-compose.federation-e2e.yml:22-23,36-37`) to the real adapter paths + the new wiring.
- **CI path-filter** (`.github/workflows/*`): if adding new producer build contexts changes which paths trigger the federation-e2e workflow, follow the **pure-positive** `code-changed` filter convention (project memory `project_ci_path_filter_074_075_quirk` — never use negation). Verify the workflow's `paths`/`paths-filter` still triggers on these dirs.

### 5. Tests (per touched producer)
- **scm inventory-visibility-service**: unit test the decode-time `tenantClaimValidator` dual-accept — a token with `entitled_domains ∋ "scm"` and `tenant_id != "scm"` **decodes**; a token with neither → **rejected**. Keep existing decode/filter tests GREEN.
- **wms admin-service**: unit test the converter — `entitled_domains ∋ "wms"` → `ROLE_WMS_VIEWER` granted (READ `@PreAuthorize` passes); neither role nor entitlement → denied; entitlement-only WRITE → still denied. Existing security/role tests GREEN.

### 6. Spec / contracts
- Each touched producer's `architecture.md` § Authorization: note entitlement-trust dual-accept now applies at the **decode-time validator** (scm inventory-visibility) / **role-synthesis converter** (wms admin) READ layer, mirroring the tenant gate. Additive only (HARDSTOP-04).

## Out of scope
- The assume-tenant exchange + console-web switcher (BE-327/MONO-158/159/160) — correct; the tokens carry the right claims.
- WRITE/mutation authz — entitlement-trust grants READ visibility only.
- erp (MONO-161, done) and finance (A-side already passes — **verify** it has no analogous decode/role gap; if it does, fix with the same dual-accept and record it; do NOT change speculatively).
- The scm gateway routing config itself (unless the gateway-faithful topology is chosen, in which case only its e2e wiring, not its routes).

---

# Acceptance Criteria

- **AC-1 (scm decode dual-accept)**: inventory-visibility-service decodes a token with `entitled_domains ∋ "scm"` + `tenant_id != "scm"`; the snapshot READ then passes Layer-2. A token with neither → 401/403 (fail-closed). Unit-tested; existing tests GREEN.
- **AC-2 (wms role dual-accept)**: admin-service grants `ROLE_WMS_VIEWER` from `entitled_domains ∋ "wms"`; `GET /api/v1/admin/dashboard/inventory` READ returns 200 for an entitled-but-no-role token; WRITE endpoints still 403 such a token. Unit-tested; existing tests GREEN.
- **AC-3 (e2e producers wired)**: `docker compose -f docker-compose.federation-e2e.yml config` is valid; the scm inventory-visibility + wms admin producers (+ their postgres sidecars) boot healthy; the two BFF base-urls point at them; seeds applied. `docker compose config` validated locally (full stack boot is CI/post-merge — Docker is unreliable on the dev host).
- **AC-4 (fail-closed + net-zero)**: SUPER_ADMIN / scope-or-role-bearing / machine tokens authorize exactly as before (existing tests unmodified GREEN). `entitled_domains` read only from the verified JWT.
- **AC-5 (THE B-side proof)**: post-merge `gh workflow run federation-hardening-e2e.yml` → `tenant-switch-rescope.spec.ts` **SUCCESS** — A-side (acme): finance+wms **not forbidden**, scm+erp **forbidden**; B-side (globex): scm+erp **not forbidden**, finance+wms **forbidden**. All specs GREEN.
- **AC-6 (per-domain CI GREEN)**: each touched service's CI Integration (Testcontainers) GREEN; 0 regression across the affected projects' build.
- **AC-7 (build)**: `./gradlew :projects:scm-platform:apps:inventory-visibility-service:compileJava :projects:wms-platform:apps:admin-service:compileJava :projects:platform-console:apps:console-bff:compileJava` (+ the relevant `:test`) GREEN.

# Related Specs / Code

- BFF legs: `projects/platform-console/apps/console-bff/.../adapter/outbound/http/{ScmInventoryReadAdapter,WmsInventoryReadAdapter}.java`; `.../application/usecase/OperatorOverviewCompositionUseCase.java`.
- scm: `projects/scm-platform/apps/inventory-visibility-service/.../config/ServiceLevelOAuth2Config.java:67-83` (decode validator) + `.../adapter/inbound/web/filter/TenantClaimEnforcer.java:78-116` (filter reference) + `.../adapter/inbound/web/InventoryVisibilityController.java` + `application.yml` + Flyway `db/migration/inventory-visibility` + `Dockerfile`; `projects/scm-platform/apps/gateway-service/.../application.yml:75-89` (route reference).
- wms: `projects/wms-platform/apps/admin-service/.../config/SecurityConfig.java:91-103` + `.../infra/security/TenantClaimValidator.java:32` + `.../api/dashboard/InventoryDashboardController.java:19-20` + `application.yml:68` (port 8086) + Flyway `V2__init_readmodel.sql` (`admin_inventory_snapshot`) + `Dockerfile`; `projects/wms-platform/apps/master-service/.../advice/GlobalExceptionHandler.java:209` (404 defense).
- e2e: `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` (+ stale comments 22-23,36-37; base-urls 486-487) + `fixtures/{seed.sql,seed-domains.sql,seed-wms.sql,seed-scm.sql}` + `specs/tenant-switch-rescope.spec.ts` + `.github/workflows/federation-hardening-e2e.yml` (Phase 2 line ~236, Phase 2.5 lines ~311-326).
- ADR-MONO-019 § D5 (entitlement-trust) ; ADR-MONO-020 § 3.3 D4 ; `rules/traits/multi-tenant.md` M1-M7.

# Edge Cases / Failure Scenarios

- **WRITE must NOT widen** — only READ. Entitlement-only WRITE → 403 at both producers.
- **claim-shape fail-closed** — `entitled_domains` absent/non-list/non-string → `isEntitled` false (no NPE, no blanket trust); mirror `TenantClaimEnforcer.isEntitled`.
- **net-zero** — existing role/scope/SUPER_ADMIN/machine tokens unchanged (dual-accept only ADDS an OR branch).
- **scm decode-vs-filter ordering** — the decode-time validator runs FIRST; fixing only the filter (already correct) is insufficient — the token must survive decode. Both layers must dual-accept.
- **wms own DB** — admin-service is `admin_db`, master-service is `master_db`; a shared sidecar will fail Flyway/schema. Separate `wms-admin-postgres` required.
- **port** — admin-service default `SERVER_PORT=8086` (not 8081); healthcheck + base-url must use 8086.
- **meta (the recurring lesson)** — *when adding entitlement-trust to a domain, EVERY authz layer it passes through (decode validator, tenant filter, role/scope `@PreAuthorize`) must dual-accept — they are independent gates.* MONO-161 fixed the role layer (erp); this fixes the decode layer (scm) + role layer (wms). Verify finance/wms/scm have no further hidden layer.
- **e2e Docker on dev host** — do NOT attempt a full local stack boot (Docker unreliable here per memory `project_testcontainers_docker_desktop_blocker`); validate via `docker compose config` + gradle build/test; AC-5 is post-merge `gh workflow run`.

# Notes

- federation-hardening-e2e is `workflow_dispatch`/nightly (NOT PR-gated) — AC-5 verified post-merge via `gh workflow run federation-hardening-e2e.yml`; until GREEN it stays RED on the scm/wms cards.
- After this lands + AC-5 GREEN: close TASK-MONO-158/159/160/161 + this task per lifecycle (3-dim merge verify each; `git mv review→done` re-stage discipline).
- Conventional Commit: cross-project → use the dominant scope or `feat(mono):`/`test(e2e):` per the shared-e2e + multi-project nature; `!`-free (additive).
