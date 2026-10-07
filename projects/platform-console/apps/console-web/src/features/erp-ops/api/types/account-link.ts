import { z } from 'zod';
import { ErpMetaSchema } from './common';

/**
 * erp employee ↔ IAM account link types (TASK-PC-FE-318 — `TASK-MONO-774` S4).
 *
 * Authoritative producer contract (consume, do not redefine):
 *   `erp-platform/specs/contracts/http/masterdata-api.md` § Employee ↔ IAM account link
 *     POST /employees/{id}/account-link-proposals          (propose — erp.write)
 *     GET  /employees/{id}/account-link-proposals          (one employee's history)
 *     GET  /account-link-proposals/mine                    (proposals addressed to me)
 *     POST /account-link-proposals/{proposalId}/accept     (account owner)
 *     POST /account-link-proposals/{proposalId}/decline    (account owner)
 *     POST /account-link-proposals/{proposalId}/revoke     (erp.write)
 *     POST /employees/{id}/account-link/unlink             (erp.write or the owner)
 * Consumer obligation: `console-integration-contract.md` § 2.4.8
 * «Employee ↔ IAM account link binding».
 *
 * TOLERANT: `status` is a free string (a future value renders generically);
 * the `?` fields are ABSENT when unset (`@JsonInclude(NON_NULL)`).
 */

export const ACCOUNT_LINK_STATUSES = [
  'PENDING',
  'ACCEPTED',
  'DECLINED',
  'REVOKED',
] as const;

export const EmployeeAccountLinkProposalSchema = z
  .object({
    id: z.string(),
    employeeId: z.string(),
    accountId: z.string(),
    status: z.string(),
    proposedBy: z.string(),
    proposedAt: z.string(),
    reason: z.string().optional(),
    decidedBy: z.string().optional(),
    decidedAt: z.string().optional(),
    decisionReason: z.string().optional(),
    // Only on `GET /account-link-proposals/mine` (the acceptor needs to know
    // WHICH employee — they cannot read that employee's master otherwise).
    employeeName: z.string().optional(),
    employeeNumber: z.string().optional(),
  })
  .passthrough();
export type EmployeeAccountLinkProposal = z.infer<
  typeof EmployeeAccountLinkProposalSchema
>;

export const AccountLinkProposalListResponseSchema = z.object({
  data: z.array(EmployeeAccountLinkProposalSchema),
  meta: ErpMetaSchema,
});
export type AccountLinkProposalListResponse = z.infer<
  typeof AccountLinkProposalListResponseSchema
>;

/** The three proposal actions behind one dynamic proxy route. */
export const ACCOUNT_LINK_PROPOSAL_ACTIONS = [
  'accept',
  'decline',
  'revoke',
] as const;
export type AccountLinkProposalAction =
  (typeof ACCOUNT_LINK_PROPOSAL_ACTIONS)[number];

/** `revoke` requires a reason (producer `@NotBlank`); accept takes none. */
export function proposalActionRequiresReason(a: string): boolean {
  return a === 'revoke';
}

// ---------------------------------------------------------------------------
// proxy-route body parsers (the route handlers validate before forwarding).
// ---------------------------------------------------------------------------

/** Propose body. `accountId` is form-checked only (producer: non-blank, ≤64 —
 *  existence in IAM is deliberately NOT checked at proposal time). */
export const ProposeAccountLinkBodySchema = z.object({
  accountId: z.string().trim().min(1).max(64),
  reason: z.string().max(256).optional(),
  idempotencyKey: z.string().min(1),
});

/** accept / decline / revoke body — `reason` per action (see
 *  {@link proposalActionRequiresReason}). */
export const AccountLinkProposalActionBodySchema = z.object({
  reason: z.string().max(256).optional(),
  idempotencyKey: z.string().min(1),
});

/** Unlink body — reason required (≤256). */
export const UnlinkAccountBodySchema = z.object({
  reason: z.string().trim().min(1).max(256),
  idempotencyKey: z.string().min(1),
});
