import { z } from 'zod';

/**
 * Shared **client-safe** IAM `admin-service` accounts SEARCH/LIST wire shapes
 * (TASK-PC-FE-002 producer binding / TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * The `GET /api/admin/accounts` read is consumed by three features —
 * `features/accounts`, `features/dashboards` (§ 2.4.4 composed overview) and
 * `features/iam-overview` (`/iam` landing). `architecture.md`
 * § Forbidden Dependencies bars a `features/A → features/B` import
 * ("공유 가치는 `shared/` 로 승격"), so the read and its shapes were promoted.
 *
 * ── WHY THE SHAPES ARE A SEPARATE MODULE FROM THE CLIENT ──
 * `shared/api/iam-accounts-read.ts` transitively imports
 * `shared/lib/session.ts` → `next/headers`, which is **server-only**.
 * `features/accounts/api/types.ts` re-exports these shapes and is imported by
 * CLIENT components / hooks, so folding the shapes into the client module
 * would make `next build` fail with "You're importing a component that needs
 * next/headers". Same split as `iam-audit-types.ts` / `iam-audit-read.ts`.
 *
 * **Keep this module zod-only.** No `next/headers`, no cookies, no fetch.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam/specs/contracts/http/admin-api.md` § `GET /api/admin/accounts`.
 * Consumer obligation: `console-integration-contract.md` § 2.4.1 / § 2.4.4.
 */

export const AccountSummarySchema = z.object({
  id: z.string(),
  email: z.string(),
  // Producer documents ACTIVE; LOCKED/DELETED are reachable post-mutation.
  status: z.string(),
  createdAt: z.string(),
});
export type AccountSummary = z.infer<typeof AccountSummarySchema>;

export const AccountPageSchema = z.object({
  content: z.array(AccountSummarySchema),
  totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalPages: z.number().int().nonnegative(),
});
export type AccountPage = z.infer<typeof AccountPageSchema>;

export interface AccountSearchParams {
  /** Single-lookup by email; mutually exclusive with list pagination. */
  email?: string;
  page?: number;
  size?: number;
  /**
   * TASK-BE-475 / TASK-PC-FE-181 — optional lifecycle-status filter
   * (`ACTIVE`|`LOCKED`|`DORMANT`|`DELETED`). Applies to the LIST branch only
   * (the producer ignores it on the `email` single-lookup). Drives the IAM
   * overview's 잠금 현황 count (`{ status: 'LOCKED', size: 1 }.totalElements`).
   */
  status?: string;
  /**
   * TASK-BE-357 — explicit tenant scope (SUPER_ADMIN cross-tenant). When omitted
   * the api layer defaults it to the active tenant (mirror of the audit view),
   * so the 계정 운영 search follows the tenant switcher. The producer gates it
   * against the operator's effective scope (403 TENANT_SCOPE_DENIED).
   */
  tenantId?: string;
}
