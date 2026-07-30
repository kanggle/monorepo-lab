import { z } from 'zod';

/**
 * Feature-local types for the IAM operators-management surface.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam/specs/contracts/http/admin-api.md`
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

/** The producer's documented operator roles. The selectors offer these
 *  (pre-filtered by the caller's server-provided grantable set — see
 *  `GrantableRolesResponseSchema` below); the list tolerates any string
 *  (forward-compatible).
 *
 *  TASK-PC-FE-157 — the two tenant-scoped delegation roles
 *  (`TENANT_ADMIN` / `TENANT_BILLING_ADMIN`, iam rbac.md Seed Roles /
 *  ADR-MONO-024) are now offered so a SUPER_ADMIN can appoint a tenant
 *  admin through the UI (the missing first step of the delegation chain).
 *
 *  Grantable-roles pre-filter (`GET /api/admin/operators/grantable-roles`):
 *  the create / edit-roles selectors now render only the subset of
 *  `KNOWN_OPERATOR_ROLES` the CALLING operator may grant (platform-scope
 *  ⇒ all; non-platform ⇒ `SUPER_ADMIN` excluded + ≤ own subset) — this is
 *  a UX pre-filter ONLY. No-escalation stays PRODUCER-enforced
 *  (`403 ROLE_GRANT_FORBIDDEN` when the actor lacks the granted role's
 *  permissions — e.g. a TENANT_ADMIN, lacking `subscription.manage`,
 *  cannot grant `TENANT_BILLING_ADMIN`); the pre-filter never bypasses
 *  that authority — a bug in the filter, a stale grantable-roles fetch, or
 *  the fetch simply failing (fallback shows every `KNOWN_OPERATOR_ROLES`
 *  checkbox — never an empty list) all still resolve to the SAME producer
 *  403 on submit. */
export const KNOWN_OPERATOR_ROLES = [
  'SUPER_ADMIN',
  'TENANT_ADMIN',
  'TENANT_BILLING_ADMIN',
  'SUPPORT_LOCK',
  'SUPPORT_READONLY',
  'SECURITY_ANALYST',
] as const;
export type KnownOperatorRole = (typeof KNOWN_OPERATOR_ROLES)[number];

/** The privilege-elevating role — granting it (create or edit-roles) gets
 *  explicit elevated confirm copy (security UX). SUPER_ADMIN is the platform-
 *  wide grant; the TENANT_* roles are tenant-confined so they keep the
 *  standard (still reason+confirm-gated) copy. */
export const ELEVATED_ROLE: KnownOperatorRole = 'SUPER_ADMIN';

// --- grantable-roles (GET /api/admin/operators/grantable-roles) -----------

/**
 * `GET /api/admin/operators/grantable-roles` response envelope — the seed
 * role names the CALLING operator (JWT + `operator.manage`) may grant.
 * Platform-scope (`SUPER_ADMIN`) → the full seed-role set; non-platform →
 * `SUPER_ADMIN` excluded + a ≤-own subset (final no-escalation enforcement
 * stays producer-side: `403 ROLE_GRANT_FORBIDDEN`). `roles` is `z.string()`
 * members (not the `KnownOperatorRole` enum) — a forward/unknown role name
 * from the producer must never crash the parse; the create/edit-roles
 * selectors intersect this against `KNOWN_OPERATOR_ROLES` when filtering.
 */
export const GrantableRolesResponseSchema = z.object({
  roles: z.array(z.string()),
});
export type GrantableRolesResponse = z.infer<
  typeof GrantableRolesResponseSchema
>;

// --- operator status + list (GET /api/admin/operators) --------------------
//
// TASK-PC-FE-259 — the list wire shapes were promoted to
// `shared/api/iam-operators-read.ts`: `features/dashboards` (§ 2.4.4 composed
// overview, leg 3) and `features/iam-overview` (`/iam` landing) consume the
// same read, and `architecture.md` § Forbidden Dependencies bars a
// `features/A → features/B` import ("공유 가치는 `shared/` 로 승격").
// Re-exported here so this module stays the operators public type surface and
// every existing `../api/types` import is unchanged. The privilege-sensitive
// CREATE / ROLES / STATUS / PASSWORD / PROFILE / ORG-SCOPE shapes below are
// NOT promoted — single consumer, feature-local (§ 3.1 rows 12–15, 17–18).

// NOTE: the pure-zod `iam-operators-types` module, NOT the server
// `iam-operators-read` client — this module is imported by CLIENT components
// (`ChangePasswordForm`, `AccountSelfService`, the operators hooks) and the
// read client reaches `next/headers`.
export {
  OPERATOR_STATUSES,
  OperatorContextSchema,
  OperatorSummarySchema,
  OperatorPageSchema,
} from '@/shared/api/iam-operators-types';
export type {
  OperatorStatus,
  OperatorContext,
  OperatorSummary,
  OperatorPage,
  OperatorListParams,
} from '@/shared/api/iam-operators-types';

// --- create (POST /api/admin/operators) -----------------------------------

export interface CreateOperatorInput {
  email: string;
  displayName: string;
  /**
   * OPTIONAL break-glass local password (ADR-MONO-035 O2 / TASK-BE-377).
   * Omitted ⇒ an OIDC-only operator (primary login is the unified IAM
   * credential of this email's account). Plaintext — server-side only, NEVER
   * logged/echoed (security invariant).
   */
  password?: string;
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

// --- org-scope assignments (TASK-PC-FE-050 / TASK-BE-339) -----------------

/**
 * Per-(operator, active-tenant) assignment row carrier (TASK-BE-339,
 * admin-api.md § `GET /api/admin/operators/{operatorId}/assignments` +
 * § `PUT .../assignments/{tenantId}/org-scope`). The active tenant is
 * always sent as `X-Tenant-Id`; the producer scopes the response to it
 * and returns 0 or 1 rows (`home-tenant-only` operator → empty array →
 * org_scope 부적용 / 전체).
 *
 * `orgScope` is field-level `@JsonInclude(NON_NULL)` on BOTH the GET list
 * item AND the PUT response — the producer OMITS the key when the column
 * is `NULL` (미설정 ⟺ `["*"]` net-zero / 전체). The console parses an
 * ABSENT key to `null` (전체), an explicit `[]` to `[]` (차단 / zero-scope,
 * distinct from null per BE-338 fail-closed), and a populated array to the
 * department subtree-root ids. `null` ≠ `[]` — the tri-state must be
 * unambiguous end-to-end.
 *
 * `permissionSetId` is also `@JsonInclude(NON_NULL)` (omitted when the
 * assignment inherits the operator-level role) — parsed to `null` when
 * absent. v1 reads it for display only (the PUT touches org_scope alone).
 *
 * `.passthrough()` tolerates a future sibling field on the row without a
 * crash (forward-compat read posture; the org-scope tri-state is the only
 * field this task writes).
 */
export const OperatorAssignmentSchema = z
  .object({
    tenantId: z.string(),
    // ABSENT key (NON_NULL omit) ⇒ null (전체 / net-zero). An explicit
    // `[]` (차단 / zero-scope) survives — it is NOT coerced to null.
    orgScope: z.array(z.string()).nullable().optional(),
    permissionSetId: z.number().int().nullable().optional(),
  })
  .passthrough()
  .transform((row) => ({
    tenantId: row.tenantId,
    // Normalise the absent/undefined NON_NULL omission to an explicit
    // `null` (전체) so downstream code never has to distinguish
    // `undefined` (absent key) from `null`. An explicit `[]` is preserved.
    orgScope: row.orgScope ?? null,
    permissionSetId: row.permissionSetId ?? null,
  }));
export type OperatorAssignment = z.infer<typeof OperatorAssignmentSchema>;

/** `GET .../assignments` envelope — `{ assignments: [...] }` (0 or 1 rows
 *  for the active tenant). */
export const OperatorAssignmentsResponseSchema = z.object({
  assignments: z.array(OperatorAssignmentSchema),
});
export type OperatorAssignmentsResponse = z.infer<
  typeof OperatorAssignmentsResponseSchema
>;

/**
 * `PUT .../assignments/{tenantId}/org-scope` request body. The producer
 * preserves the three-way semantics verbatim:
 *   - `null`  → clear (전체 / net-zero ⟺ `["*"]`); column `NULL`.
 *   - `[]`    → explicit zero-scope (차단; distinct from `null`).
 *   - `[ids]` → department subtree-root ids (producer trims · rejects blank
 *               · dedupes order-preserving · ≤ 256; IAM does NOT verify the
 *               ids exist in erp — ERP-BE-008 验证 at consume time).
 */
export interface SetOrgScopeInput {
  /** `null` clears (전체); `[]` is explicit 차단; `[ids]` is the subtree set. */
  readonly orgScope: string[] | null;
}

/** `PUT` response — the updated assignment (same shape as a GET item; the
 *  `orgScope` key is again NON_NULL-omitted when cleared → parsed to null). */
export const SetOrgScopeResultSchema = OperatorAssignmentSchema;
export type SetOrgScopeResult = z.infer<typeof SetOrgScopeResultSchema>;

// --- update-profile (PATCH .../me/profile) — self, 204 No Content ---------

/**
 * Self update-profile input (TASK-PC-FE-016). v1 carries a single
 * attribute under `operatorContext.defaultAccountId` — the operator's
 * chosen default finance-platform account UUID (opaque to IAM — TASK-
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
