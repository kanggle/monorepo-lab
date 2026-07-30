import { z } from 'zod';

/**
 * Shared **client-safe** IAM `admin-service` operator LIST wire shapes
 * (TASK-PC-FE-004 producer binding / TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * The `GET /api/admin/operators` read is consumed by three features —
 * `features/operators`, `features/dashboards` (§ 2.4.4 composed overview,
 * leg 3) and `features/iam-overview` (`/iam` landing). `architecture.md`
 * § Forbidden Dependencies bars a `features/A → features/B` import
 * ("공유 가치는 `shared/` 로 승격"), so the read and its shapes were promoted.
 *
 * ── WHY THE SHAPES ARE A SEPARATE MODULE FROM THE CLIENT ──
 * `shared/api/iam-operators-read.ts` transitively imports
 * `shared/lib/session.ts` → `next/headers`, which is **server-only**.
 * `features/operators/api/types.ts` re-exports these shapes and is imported by
 * CLIENT components (`ChangePasswordForm`, `AccountSelfService`, the operators
 * hooks), so folding the shapes into the client module made `next build` fail
 * with "You're importing a component that needs next/headers". Keeping the
 * pure-zod shapes here — the same split `iam-audit-types.ts` /
 * `iam-audit-read.ts` uses — keeps the client bundle server-free.
 *
 * **Keep this module zod-only.** No `next/headers`, no cookies, no fetch.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam/specs/contracts/http/admin-api.md` § `GET /api/admin/operators`.
 * Consumer obligation: `console-integration-contract.md` § 2.4.3 / § 2.4.4.
 */

// --- operator status ------------------------------------------------------

export const OPERATOR_STATUSES = ['ACTIVE', 'SUSPENDED'] as const;
export type OperatorStatus = (typeof OPERATOR_STATUSES)[number];

// --- list wire shapes (GET /api/admin/operators) --------------------------

/**
 * Per-operator profile carrier (TASK-BE-308). Optional field on each list-
 * response item — omitted by the producer when the operator's
 * {@code finance_default_account_id} is NULL (field-level
 * {@code @JsonInclude.NON_NULL}); present with
 * {@code { defaultAccountId: "<uuid>" }} when set. The shape is byte-
 * identical to the registry response item carrier and the
 * {@code me/profile} + {@code admin/{operatorId}/profile} request bodies
 * (admin-api.md § "carrier shape 대칭성"). Strict on the nested key set —
 * a forward-compat new sibling key (e.g. wmsDefaultWarehouseId) is a
 * fail-fast signal, not a silent acceptance.
 */
export const OperatorContextSchema = z.object({
  defaultAccountId: z.string().optional(),
});
export type OperatorContext = z.infer<typeof OperatorContextSchema>;

export const OperatorSummarySchema = z.object({
  operatorId: z.string(),
  email: z.string(),
  displayName: z.string(),
  // Producer documents ACTIVE/SUSPENDED; keep as string so an unknown
  // future status never crashes the list render.
  status: z.string(),
  // Role members are strings (unknown/future role ⇒ generic chip, no crash).
  roles: z.array(z.string()),
  totpEnrolled: z.boolean().optional(),
  lastLoginAt: z.string().nullable().optional(),
  createdAt: z.string(),
  // TASK-BE-308 — optional profile carrier; PC-FE-018 consumer reads
  // operatorContext?.defaultAccountId to pre-populate the admin
  // profile-edit dialog with the operator's current value.
  operatorContext: OperatorContextSchema.optional(),
});
export type OperatorSummary = z.infer<typeof OperatorSummarySchema>;

export const OperatorPageSchema = z.object({
  content: z.array(OperatorSummarySchema),
  totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalPages: z.number().int().nonnegative(),
});
export type OperatorPage = z.infer<typeof OperatorPageSchema>;

export interface OperatorListParams {
  /** ACTIVE | SUSPENDED filter; undefined ⇒ all. */
  status?: OperatorStatus;
  page?: number;
  size?: number;
}
