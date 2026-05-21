import { z } from 'zod';

/**
 * Feature-local types for the GAP operators-management surface.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `global-account-platform/specs/contracts/http/admin-api.md`
 *   §§ `GET /api/admin/operators`, `POST /api/admin/operators`,
 *      `PATCH /api/admin/operators/{operatorId}/roles`,
 *      `PATCH /api/admin/operators/{operatorId}/status`,
 *      `PATCH /api/admin/operators/me/password`.
 * Consumer obligation: `console-integration-contract.md` § 2.4.3.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 *
 * Role names are the producer's enum, but the LIST view must tolerate an
 * unknown/future role (a generic chip, never a crash) — so the row schema
 * uses `z.string()` for role members; the known enum drives only the
 * create/edit-roles selectors (console-integration-contract § 2.4.3 "Role
 * tolerance" / task Edge Case).
 */

// --- roles ----------------------------------------------------------------

/** The producer's documented operator roles. The selectors offer these;
 *  the list tolerates any string (forward-compatible). */
export const KNOWN_OPERATOR_ROLES = [
  'SUPER_ADMIN',
  'SUPPORT_LOCK',
  'SUPPORT_READONLY',
  'SECURITY_ANALYST',
] as const;
export type KnownOperatorRole = (typeof KNOWN_OPERATOR_ROLES)[number];

/** The privilege-elevating role — granting it (create or edit-roles) gets
 *  explicit elevated confirm copy (security UX). */
export const ELEVATED_ROLE: KnownOperatorRole = 'SUPER_ADMIN';

// --- operator status ------------------------------------------------------

export const OPERATOR_STATUSES = ['ACTIVE', 'SUSPENDED'] as const;
export type OperatorStatus = (typeof OPERATOR_STATUSES)[number];

// --- list (GET /api/admin/operators) --------------------------------------

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

// --- create (POST /api/admin/operators) -----------------------------------

export interface CreateOperatorInput {
  email: string;
  displayName: string;
  /** Plaintext — server-side only, NEVER logged/echoed (security invariant). */
  password: string;
  roles: string[];
  /** Tenant the new operator belongs to. '*' is the SUPER_ADMIN platform
   *  sentinel — only a platform-scope operator may create another. The UI
   *  never offers '*' to a non-platform operator (task Edge Case). */
  tenantId: string;
}

export const CreateOperatorResultSchema = z.object({
  operatorId: z.string(),
  email: z.string(),
  displayName: z.string(),
  status: z.string(),
  roles: z.array(z.string()),
  totpEnrolled: z.boolean().optional(),
  createdAt: z.string(),
  auditId: z.string(),
  tenantId: z.string(),
});
export type CreateOperatorResult = z.infer<typeof CreateOperatorResultSchema>;

// --- edit-roles (PATCH .../{operatorId}/roles) ----------------------------

export const EditRolesResultSchema = z.object({
  operatorId: z.string(),
  roles: z.array(z.string()),
  auditId: z.string(),
});
export type EditRolesResult = z.infer<typeof EditRolesResultSchema>;

// --- change-status (PATCH .../{operatorId}/status) ------------------------

export const ChangeStatusResultSchema = z.object({
  operatorId: z.string(),
  previousStatus: z.string(),
  currentStatus: z.string(),
  auditId: z.string(),
});
export type ChangeStatusResult = z.infer<typeof ChangeStatusResultSchema>;

// --- change-password (PATCH .../me/password) — self, 204 No Content -------

export interface ChangePasswordInput {
  /** Plaintext current password — server-side only, NEVER logged. */
  currentPassword: string;
  /** Plaintext new password — server-side only, NEVER logged. */
  newPassword: string;
}

/**
 * Client-side password-policy mirror (a UX pre-check ONLY — the producer is
 * the final authority; console-integration-contract § 2.4.3 password
 * safety). Mirrors the create-form producer policy: ≥10 chars, ≥1 letter,
 * ≥1 digit, ≥1 special. Returns the first failing reason key (a stable
 * error code, NEVER the password itself) or null when OK.
 */
export function passwordPolicyError(password: string): string | null {
  if (password.length < 10) return 'PASSWORD_POLICY_VIOLATION';
  if (!/[A-Za-z]/.test(password)) return 'PASSWORD_POLICY_VIOLATION';
  if (!/[0-9]/.test(password)) return 'PASSWORD_POLICY_VIOLATION';
  if (!/[^A-Za-z0-9]/.test(password)) return 'PASSWORD_POLICY_VIOLATION';
  return null;
}

/**
 * The audit reason an operator must enter before any reason-bearing
 * mutation fires (→ `X-Operator-Reason` header). Required, non-empty.
 * change-password (self) carries NO reason header per the producer.
 */
export interface OperatorMutationReason {
  reason: string;
}

// --- update-profile (PATCH .../me/profile) — self, 204 No Content ---------

/**
 * Self update-profile input (TASK-PC-FE-016). v1 carries a single
 * attribute under `operatorContext.defaultAccountId` — the operator's
 * chosen default finance-platform account UUID (opaque to GAP — TASK-
 * BE-304 § Decision authority). Explicit `null` clears the column;
 * a string must be non-empty after trim, ≤ 36 chars, with no internal
 * whitespace and no control chars (producer-authoritative). The body
 * shape mirrors the READ shape on the registry (`operatorContext.
 * defaultAccountId?: string`) verbatim — read → mutate → re-read on
 * the same JSON path.
 */
export interface UpdateProfileInput {
  /** UUID-like opaque string OR null to clear. */
  readonly defaultAccountId: string | null;
}
