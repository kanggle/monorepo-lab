import { z } from 'zod';

/**
 * Feature-local types for the erp `approval-service` delegation surface
 * (TASK-PC-FE-054 — PC-FE-053 follow-up; realises TASK-ERP-BE-013).
 *
 * Authoritative producer contract (do NOT redefine — consume):
 *   `erp-platform/specs/contracts/http/approval-api.md`
 *     §v2.1 AMENDMENT — delegation grant management:
 *     POST   /api/erp/approval/delegations        (create grant)
 *     GET    /api/erp/approval/delegations        (list grants)
 *     POST   /api/erp/approval/delegations/{id}/revoke (revoke grant)
 *
 * NON_NULL absent-field convention (producer § `@JsonInclude(NON_NULL)`):
 * nullable response fields (`validTo`, `reason`, `revokedAt`, `revokedBy`)
 * are ABSENT from the JSON when unset — never serialized as `null`. These
 * parse to optional/undefined here (zod `.optional()`); the UI renders an
 * absent field as "무기한" / hidden, never a crash (same convention as
 * the approval surface).
 *
 * TOLERANCE: `status` is a free string (ACTIVE|REVOKED are known values).
 */

// ---------------------------------------------------------------------------
// DelegationGrant — the wire shape returned by list + create + revoke.
//   NON_NULL absent fields: validTo, reason, revokedAt, revokedBy.
// ---------------------------------------------------------------------------

export const DelegationGrantSchema = z
  .object({
    id: z.string(),
    delegatorId: z.string(),
    delegateId: z.string(),
    validFrom: z.string(),
    // ABSENT when open-ended (NON_NULL).
    validTo: z.string().optional(),
    // ABSENT when not provided at create time (NON_NULL).
    reason: z.string().optional(),
    // Free-string tolerant: known values are ACTIVE | REVOKED.
    status: z.string(),
    createdAt: z.string(),
    createdBy: z.string(),
    // ABSENT until REVOKED (NON_NULL).
    revokedAt: z.string().optional(),
    // ABSENT until REVOKED (NON_NULL).
    revokedBy: z.string().optional(),
  })
  .passthrough();
export type DelegationGrant = z.infer<typeof DelegationGrantSchema>;

// ---------------------------------------------------------------------------
// List response envelope — `{ data: [grant…], meta }`.
// ---------------------------------------------------------------------------

export const DelegationListMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type DelegationListMeta = z.infer<typeof DelegationListMetaSchema>;

export const DelegationListResponseSchema = z.object({
  data: z.array(DelegationGrantSchema),
  meta: DelegationListMetaSchema,
});
export type DelegationListResponse = z.infer<typeof DelegationListResponseSchema>;

// ---------------------------------------------------------------------------
// Input types.
// ---------------------------------------------------------------------------

/** Input for `POST /api/erp/approval/delegations` (delegator = caller's JWT
 *  sub — NOT sent in the body; erp resolves the delegator from the token). */
export interface CreateDelegationInput {
  delegateId: string;
  validFrom: string;
  /** Absent = open-ended grant (no expiry). */
  validTo?: string;
  /** Optional creation reason. */
  reason?: string;
}

// ---------------------------------------------------------------------------
// Proxy-route body validators — the route handlers validate before forwarding.
// ---------------------------------------------------------------------------

/** `POST /api/erp/approval/delegations` create proxy body. */
export const CreateDelegationBodySchema = z.object({
  delegateId: z.string().min(1),
  validFrom: z.string().min(1),
  validTo: z.string().optional(),
  reason: z.string().max(512).optional(),
  idempotencyKey: z.string().min(1),
});

/** `POST /api/erp/approval/delegations/{id}/revoke` revoke proxy body. */
export const RevokeDelegationBodySchema = z.object({
  reason: z.string().min(1).max(512),
  idempotencyKey: z.string().min(1),
});

// ---------------------------------------------------------------------------
// Helper — is a grant active (status ACTIVE and not yet past validTo).
// ---------------------------------------------------------------------------

/**
 * True when the grant's `status` is "ACTIVE" AND its `validTo` (if present)
 * is not in the past relative to `nowIso` (defaults to `Date.now()`).
 * Used by the UI to distinguish "활성" from "만료" ACTIVE grants.
 */
export function isActiveGrant(
  g: DelegationGrant,
  nowIso?: string,
): boolean {
  if (g.status !== 'ACTIVE') return false;
  if (!g.validTo) return true; // open-ended — never expires
  const now = nowIso ? new Date(nowIso) : new Date();
  return new Date(g.validTo) >= now;
}
