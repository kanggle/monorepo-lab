/**
 * Shared TanStack Query **key roots** — the registry of cache-key namespaces
 * that MORE THAN ONE feature must reference (TASK-PC-FE-259).
 *
 * ── WHY THIS EXISTS ──
 * A feature normally owns its own query keys entirely (`features/<f>/api/
 * *-keys.ts`). The exception is **cross-feature invalidation**: when feature A
 * performs a write that changes what feature B's queries would return, A must
 * invalidate B's key subtree. Reaching into
 * `@/features/<B>/api/<B>-keys` to get the root is a
 * `features/A → features/B` import, which `architecture.md`
 * § Forbidden Dependencies bars —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * so the root string is promoted here and BOTH sides import it. The owning
 * feature's key factory re-exports it, so its own key builders and every
 * existing consumer are unchanged.
 *
 * ── ADDING A ROOT ──
 * Only add a root when a SECOND feature genuinely needs it, and record which
 * write drives the invalidation. A root nobody cross-invalidates belongs in
 * its feature, not here.
 */

/**
 * `features/erp-ops` query-key root.
 *
 * Cross-feature consumer: `features/operators` — a successful per-operator
 * **org_scope** write (`PUT /api/operators/{id}/assignments/{tenantId}/org-scope`,
 * TASK-PC-FE-050) changes the erp read-model's consume-time filter
 * (ERP-BE-008), so the operators mutation invalidates
 * `[ERP_KEY, 'read-model']` and keys the org_scope department picker's own
 * one-shot read under `[ERP_KEY, 'org-scope-picker', 'departments']`.
 *
 * Client-safe: a bare string constant. `features/operators`' hooks are
 * `'use client'` and MUST NOT import the `erp-ops` barrel (it re-exports
 * server-only state that would drag `next/headers` into the client bundle) —
 * which is precisely why the old code deep-imported `erp-keys` and why this
 * root now lives here.
 */
export const ERP_KEY = 'erp-ops';
