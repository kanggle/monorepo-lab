# TASK-BE-355 — Extend RESOURCE_TAG to tenant + account resources (admin-local tag table)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (authorization enforcement — security-sensitive)

---

## Goal

Complete the RESOURCE_TAG access-condition's **resource** surface — currently only admin **operators** are tag-gated (TASK-BE-353) — by extending it to admin **tenants** and **accounts**, mirroring the operator pattern's **trusted-local-column / anti-spoof** invariant (ADR-029 § D2-C).

Operators were clean because admin-service owns `admin_operators.tags` locally. Tenants and accounts are persisted by **account-service** (admin-service reaches them only via `AccountServiceClient` internal HTTP). To preserve the anti-spoof invariant **and** avoid a synchronous cross-service call in the authorization hot path (which would couple every gated mutation to account-service availability + add a fail-safe-deny-on-unreachable failure mode), the tenant/account **governance tags** live in a NEW **admin-local** table `admin_resource_tags` — admin governance attributes are owned by admin-service, exactly as operator tags are. (User decision 2026-06-12: "admin 로컬 tag 테이블 (operator 패턴 미러)".)

This also generalizes the enforcement seam from a **single** `ResourceTagResolver` to **all** registered resolvers (operator + tenant + account), consulted at the single decision site.

## Scope

**In scope (iam admin-service):**
1. `RequiresPermissionAspect.anyConditionUnmet` — replace the single `resourceTagResolverProvider.getIfAvailable()` with `resourceTagResolverProvider.orderedStream()`, consulting each resolver in turn; the first that returns `Optional.of(tags)` (applicable) is evaluated against the (BE-354) configured `ResourceTagCondition` list, then the loop stops (request paths are disjoint, so at most one resolver applies). Non-applicable resolvers return empty → skipped (net-zero).
2. **V0035** migration — `admin_resource_tags(resource_type VARCHAR, resource_id VARCHAR, tags VARCHAR(512) NULL, PRIMARY KEY(resource_type, resource_id))`. A generic admin-local governance-tags table for non-operator resources (operators keep `admin_operators.tags`). Seed/admin-SQL only — no tag-set API (mirrors operators).
3. `AdminResourceTagJpaRepository` — native projection `findTags(resourceType, resourceId) → Optional<String>` (NULL column / absent row both → empty downstream).
4. `TenantResourceTagResolver` — path `^/api/admin/tenants/([^/]+)$` (the `PATCH /api/admin/tenants/{tenantId}` mutation; `POST /api/admin/tenants` collection-create has no id → not matched), resource_type `TENANT`, reads the local table.
5. `AccountResourceTagResolver` — path `^/api/admin/accounts/([^/]+)/(?:lock|unlock)$` (lock/unlock; `POST /api/admin/accounts/bulk-lock` has no single id → not matched), resource_type `ACCOUNT`, reads the local table.
6. `rbac.md` — record that RESOURCE_TAG resolvers now cover operator/tenant/account (the trusted-local-table source).
7. Tests: resolver unit tests (path match + tag read, mocked repo) + a multi-resolver enforcement slice proving a tenant/account mutation is gated by its resolver while the others return empty.

**Out of scope:**
- The shared `ResourceTagCondition` lib + `platform/access-conditions.md` (unchanged — § 1 already defines the type + both modes; this is consumer wiring).
- Any account-service change (no cross-service tag fetch; the table is admin-local).
- A tag-set API (tags are seed/admin-SQL only, mirroring operators).
- A federation-e2e proof (RESOURCE_TAG is already fed-proven for the type, MONO-228; new resources are deterministically slice/Integration-provable — optional follow-on).
- The require-mode wiring (TASK-BE-354, already done) — this task reuses it; both modes apply to all resources.

## Acceptance Criteria

- **AC-1 (tenant)** — With `forbidden=[protected]` configured and `admin_resource_tags(TENANT, <id>, 'protected')` seeded, `PATCH /api/admin/tenants/{id}` is denied `403 ACCESS_CONDITION_UNMET`; an untagged tenant proceeds.
- **AC-2 (account)** — With `forbidden=[protected]` configured and `admin_resource_tags(ACCOUNT, <id>, 'protected')` seeded, `POST /api/admin/accounts/{id}/lock` is denied; an untagged account proceeds. `bulk-lock` (no single id) is never gated (skipped).
- **AC-3 (multi-resolver)** — The aspect consults ALL resolvers: an operator path is gated by the operator resolver (tenant/account resolvers return empty for it) and vice-versa; exactly one resolver applies per request.
- **AC-4 (net-zero / fail-safe preserved)** — Unconfigured condition (empty forbidden+required) → no gate. A path that matches no resolver → skipped. NULL/absent tag row → empty set (allowed under forbidden). A `null` resolved set would deny (fail-safe) — but the resolvers return `Optional.of(emptySet)` for untagged, never null. The require mode (BE-354) composes unchanged.
- **AC-5** — `:admin-service:check` BUILD SUCCESSFUL; CI "Integration (iam, Testcontainers)" GREEN (no regression to operator RESOURCE_TAG / SOURCE_IP / TIME_WINDOW enforcement or the broader admin surface; V0035 applies cleanly).

## Related Specs / Contracts

- `projects/iam-platform/specs/services/admin-service/rbac.md` (access-condition section — updated here).
- `platform/access-conditions.md` § 1 (RESOURCE_TAG type) + § 4 (single decision site, resolver seam) — unchanged.
- `docs/adr/ADR-MONO-029-resource-tag-access-condition.md` (§ D2-A resolver seam, § D2-C trusted-source anti-spoof).

## Edge Cases

- **Multiple resolver beans** — `getIfAvailable()` would throw `NoUniqueBeanDefinitionException` once 3 resolvers exist; `orderedStream()` is required (mirrors the BE-354 condition generalization). Existing slice tests that wire ONE resolver bean still work via `orderedStream()`.
- **Disjoint paths** — operator/tenant/account regexes don't overlap; the `break` after the first applicable resolver is an optimization, correct because at most one matches. Without it, others return empty (still correct).
- **Generic table key** — `(resource_type, resource_id)` composite PK; a tenant and an account could share an id string without collision (different type). Resolvers pass their fixed type.
- **bulk-lock / create-tenant** — collection-level mutations with no single resource id in the path are not matched by the resolvers → skipped (net-zero), consistent with how operator collection paths are skipped.
- **Operator tags stay put** — operators keep reading `admin_operators.tags` (BE-353); only the NEW resources use `admin_resource_tags`. Do not migrate operator tags.

## Failure Scenarios

- **F1 — cross-service coupling regression** — if a resolver fetched tags via account-service HTTP, every gated tenant/account mutation would depend on account-service availability. Avoided by the admin-local table (no cross-service call in the authz path).
- **F2 — dropping the operator gate** — if the `orderedStream()` generalization mis-handled multiple resolvers, the BE-353 operator deny-if-present (and MONO-228 fed behaviour) would break. Guarded by AC-3 + existing operator slice/Integration.
- **F3 — spoofable tags** — if tags came from the request, a caller could bypass the gate. Guarded by reading only the trusted `admin_resource_tags` column (anti-spoof, § D2-C).
