# Task ID

TASK-BE-340

# Title

iam admin-service: rename 3 console product catalog displayNames — wms "Platform"→"System", scm/finance drop "Platform" (registry source of truth)

# Status

done

# Owner

claude (Opus 4.8 analysis / Sonnet 4.6 impl) — registry displayName constants + contract table; data-driven (console-web shows these verbatim, zero FE change).

# Task Tags

- code
- spec

---

# Dependency Markers

- **user request (2026-06-09)**: 카탈로그의 제품 라벨 변경 — `wms` "Warehouse Management Platform"→"Warehouse Management System", `scm` "Supply Chain Management Platform"→"Supply Chain Management", `finance` "Finance Platform"→"Finance".
- **source of truth**: `ProductCatalog.java` (admin-service console registry, TASK-BE-296). console-web renders `displayName` verbatim from the registry (data-driven, console-integration-contract §2.2) — so this is a registry-only change; no console-web code change.
- **note**: only the **product** displayName changes. The unrelated **tenant** entity displayNames (e.g. tenant `wms` "Warehouse Management Platform" in the IT mock / tenant registry) are a separate concept not shown in the catalog (the catalog lists tenant SLUGS) — left unchanged.

# Goal

Update the 3 product displayName constants in `ProductCatalog.java` and the matching `console-registry-api.md` "Product catalog" table rows. iam ("Identity & Access Management") and erp ("Enterprise Resource Planning") unchanged.

# Scope

## In Scope

- **`apps/admin-service/src/main/java/com/example/admin/application/console/ProductCatalog.java`** — `ENTRIES`:
  - `wms` displayName → `"Warehouse Management System"`
  - `scm` displayName → `"Supply Chain Management"`
  - `finance` displayName → `"Finance"`
- **`specs/contracts/http/console-registry-api.md`** — the JSON example "Product catalog" displayName values + the lower `productKey | displayName` table rows for wms/scm/finance, kept in sync.

## Out of Scope

- iam / erp product displayNames (unchanged).
- Tenant entity displayNames (different concept; not catalog-shown).
- `available` / `tenants` / `baseRoute` / tenant binding logic.
- console-web code (data-driven — renders the new strings with no change).

# Acceptance Criteria

- [ ] `ProductCatalog.java` ENTRIES: wms="Warehouse Management System", scm="Supply Chain Management", finance="Finance"; iam/erp unchanged.
- [ ] `console-registry-api.md` product-catalog example + table rows match the new names.
- [ ] `ConsoleRegistryIntegrationTest` stays green — it asserts only `$.products[0].displayName` (iam, unchanged) + productKey/available/tenants for wms/scm/finance (no product-displayName assertion for those), so no test change is required; the V1 registry shape/order is unchanged.
- [ ] `./gradlew :iam-platform:apps:admin-service:test` green (or the admin-service module's `:check`).

# Related Specs

- `projects/iam-platform/specs/contracts/http/console-registry-api.md` § Product catalog (updated here).
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.2 (consumer — `displayName` is the tile label, rendered verbatim).

# Related Contracts

- `console-registry-api.md` (the producer — updated in lockstep with the code).

# Target Service

- `iam-platform` / `apps/admin-service` — registry product catalog constants + contract doc. No DB / migration / API-shape change.

# Architecture

- The registry is the single source of product truth (ADR-MONO-013 / BE-296); displayName is a pure label constant. Changing it ripples to the console catalog with zero console code change (data-driven proof).

# Edge Cases

- iam/erp rows must remain byte-identical (only wms/scm/finance change).
- The IT's tenant-mock displayNames (tenant entities) must NOT be touched — they are not the product catalog.

# Failure Scenarios

- Changing a tenant displayName by mistake → IT tenant assertions drift → AC scopes the change to product entries only.
- Contract table left stale → spec↔code drift → AC requires the doc update in lockstep.

# Definition of Done

- [ ] 3 product displayNames renamed in code + contract; iam/erp unchanged
- [ ] admin-service tests green; registry shape/order unchanged
- [ ] Acceptance Criteria satisfied
- [ ] Ready for review
