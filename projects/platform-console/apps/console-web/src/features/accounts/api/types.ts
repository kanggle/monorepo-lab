import { z } from 'zod';

/**
 * Feature-local types for the IAM accounts operator surface.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam/specs/contracts/http/admin-api.md`
 *   §§ `GET /api/admin/accounts`, `.../{id}/lock`, `bulk-lock`,
 *      `.../{id}/unlock`, `POST /api/admin/sessions/{accountId}/revoke`,
 *      `.../{id}/gdpr-delete`, `GET .../{id}/export`.
 * Consumer obligation: `console-integration-contract.md` § 2.4.1.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 */

// --- search / list (GET /api/admin/accounts) ------------------------------
//
// TASK-PC-FE-259 — the search/list wire shapes were promoted to
// `shared/api/iam-accounts-read.ts`: `features/dashboards` (§ 2.4.4 composed
// overview) and `features/iam-overview` (`/iam` landing) consume the same read,
// and `architecture.md` § Forbidden Dependencies bars a `features/A →
// features/B` import ("공유 가치는 `shared/` 로 승격"). Re-exported here so
// this module stays the accounts public type surface and every existing
// `../api/types` import is unchanged. The MUTATION result shapes below are NOT
// promoted — single consumer, feature-local (§ 3.1 rows 3–8).

export const AccountStatusSchema = z.enum(['ACTIVE', 'LOCKED', 'DELETED']);
export type AccountStatus = z.infer<typeof AccountStatusSchema>;

// NOTE: the pure-zod `iam-accounts-types` module, NOT the server
// `iam-accounts-read` client — this module is imported by CLIENT components
// and the read client reaches `next/headers`.
export {
  AccountSummarySchema,
  AccountPageSchema,
} from '@/shared/api/iam-accounts-types';
export type {
  AccountSummary,
  AccountPage,
  AccountSearchParams,
} from '@/shared/api/iam-accounts-types';

// --- lock / unlock --------------------------------------------------------

// TASK-BE-619 / TASK-PC-FE-332 — shared "whose scope did this mutation apply
// to" enum. A SITE operator's lock/unlock/gdpr-delete of a consumer-POOL
// member affects only that site's membership (`SITE_MEMBERSHIP`), never the
// whole account (`ACCOUNT`). Declared once, before every result schema that
// carries it (lock/unlock below, gdpr-delete further down), so none of them
// forward-reference it.
export const MutationScopeSchema = z.enum(['ACCOUNT', 'SITE_MEMBERSHIP']);
export type MutationScope = z.infer<typeof MutationScopeSchema>;
/** @deprecated alias kept for the gdpr-delete section's existing name/export. */
export const GdprDeleteScopeSchema = MutationScopeSchema;
export type GdprDeleteScope = MutationScope;

// TASK-PC-FE-332 (admin-api.md § lock/unlock, TASK-BE-621): a SITE operator's
// lock/unlock of a consumer-POOL member changes only that site's membership —
// the account's own `status` (and this row's `AccountSummary.status`) is
// untouched, so the response says so out loud via `scope`/`siteTenantId`
// (same shape as GdprDeleteResultSchema's TASK-BE-619 fields below). `scope`
// is optional so an older admin-service (no field) still parses — read as
// `ACCOUNT` per the contract's documented fallback.
export const LockResultSchema = z.object({
  accountId: z.string(),
  previousStatus: z.string(),
  currentStatus: z.string(),
  operatorId: z.string(),
  lockedAt: z.string(),
  auditId: z.string(),
  scope: MutationScopeSchema.optional(),
  siteTenantId: z.string().nullable().optional(),
});
export type LockResult = z.infer<typeof LockResultSchema>;

export const UnlockResultSchema = z.object({
  accountId: z.string(),
  previousStatus: z.string(),
  currentStatus: z.string(),
  operatorId: z.string(),
  unlockedAt: z.string(),
  auditId: z.string(),
  scope: MutationScopeSchema.optional(),
  siteTenantId: z.string().nullable().optional(),
});
export type UnlockResult = z.infer<typeof UnlockResultSchema>;

// --- bulk-lock (partial-failure: per-account results) ---------------------

export const BulkLockOutcomeSchema = z.enum([
  'LOCKED',
  'NOT_FOUND',
  'ALREADY_LOCKED',
  'FAILURE',
]);
export type BulkLockOutcome = z.infer<typeof BulkLockOutcomeSchema>;

export const BulkLockItemSchema = z.object({
  accountId: z.string(),
  outcome: BulkLockOutcomeSchema,
  error: z
    .object({ code: z.string(), message: z.string() })
    .optional(),
});
export type BulkLockItem = z.infer<typeof BulkLockItemSchema>;

export const BulkLockResultSchema = z.object({
  results: z.array(BulkLockItemSchema),
});
export type BulkLockResult = z.infer<typeof BulkLockResultSchema>;

// --- revoke-session -------------------------------------------------------

export const RevokeSessionResultSchema = z.object({
  accountId: z.string(),
  revokedSessionCount: z.number().int().nonnegative(),
  operatorId: z.string(),
  revokedAt: z.string(),
  auditId: z.string(),
});
export type RevokeSessionResult = z.infer<typeof RevokeSessionResultSchema>;

// --- gdpr-delete (irreversible) -------------------------------------------

// TASK-BE-619 (iam admin-api.md § gdpr-delete): a SITE operator's request on a
// consumer-pool account (one IAM account shared by the fan and store sites)
// erases nothing — it ends only that site's membership (`scope =
// SITE_MEMBERSHIP`, `maskedAt = null`, `status` = the account's unchanged
// status). Erasing the pool account itself is the person's or a platform
// admin's. `scope` is optional so an older admin-service still parses.
// (`GdprDeleteScopeSchema` is declared above, next to `MutationScopeSchema`,
// so `LockResultSchema`/`UnlockResultSchema` can share it without a
// forward-reference.)

export const GdprDeleteResultSchema = z.object({
  accountId: z.string(),
  status: z.string(),
  maskedAt: z.string().nullable(),
  auditId: z.string(),
  scope: GdprDeleteScopeSchema.optional(),
  siteTenantId: z.string().nullable().optional(),
});
export type GdprDeleteResult = z.infer<typeof GdprDeleteResultSchema>;

// --- export (unmasked PII — never buffered into client state) -------------

export const AccountExportSchema = z.object({
  accountId: z.string(),
  email: z.string(),
  status: z.string(),
  createdAt: z.string(),
  profile: z
    .object({
      displayName: z.string().nullable().optional(),
      phoneNumber: z.string().nullable().optional(),
      birthDate: z.string().nullable().optional(),
      locale: z.string().nullable().optional(),
      timezone: z.string().nullable().optional(),
    })
    .partial()
    .optional(),
  exportedAt: z.string(),
});
export type AccountExport = z.infer<typeof AccountExportSchema>;

/**
 * The audit reason an operator must enter before any mutation fires
 * (→ `X-Operator-Reason` header + body `reason`). Required, non-empty;
 * bulk-lock additionally requires ≥ 8 chars (producer constraint).
 */
export interface MutationReason {
  reason: string;
  ticketId?: string;
}
