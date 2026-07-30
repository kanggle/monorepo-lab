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

export const LockResultSchema = z.object({
  accountId: z.string(),
  previousStatus: z.string(),
  currentStatus: z.string(),
  operatorId: z.string(),
  lockedAt: z.string(),
  auditId: z.string(),
});
export type LockResult = z.infer<typeof LockResultSchema>;

export const UnlockResultSchema = z.object({
  accountId: z.string(),
  previousStatus: z.string(),
  currentStatus: z.string(),
  operatorId: z.string(),
  unlockedAt: z.string(),
  auditId: z.string(),
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

export const GdprDeleteResultSchema = z.object({
  accountId: z.string(),
  status: z.string(),
  maskedAt: z.string(),
  auditId: z.string(),
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
