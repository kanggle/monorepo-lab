import { z } from 'zod';

/**
 * Feature-local types for the IAM cross-org partnership surface
 * (TASK-PC-FE-187 / ADR-MONO-045 §3.4).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam/specs/contracts/http/admin-api.md` § Partnership Management
 *   (BE-476/477/478): `POST /api/admin/partnerships`,
 *   `POST {id}:accept|:suspend|:reactivate|:terminate`, `GET`,
 *   `POST|DELETE {id}/participants/{operatorId}`.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 */

// --- partnership status ---------------------------------------------------

/** Partnership lifecycle state machine (admin-service authority — the console
 *  only DRIVES a transition target; the producer guards the machine). Drives
 *  the PartnershipsScreen status-transition button gating. */
export const PartnershipStatusSchema = z.enum([
  'PENDING',
  'ACTIVE',
  'SUSPENDED',
  'TERMINATED',
]);
export type PartnershipStatus = z.infer<typeof PartnershipStatusSchema>;

/** The active tenant's side of a partnership row (host = 내가 발행,
 *  partner = 나에게 위임됨). Drives the host-side / partner-side section split. */
export const PartnershipMyRoleSchema = z.enum(['host', 'partner']);
export type PartnershipMyRole = z.infer<typeof PartnershipMyRoleSchema>;

// --- delegated / participant scope ----------------------------------------

/** A bounded delegation scope: business `domains` + non-admin `roles`. Members
 *  are `z.string()` (an unknown/future domain or role name must never crash the
 *  parse — the producer is the authority on validity). */
export const ScopeSetSchema = z.object({
  domains: z.array(z.string()),
  roles: z.array(z.string()),
});
export type ScopeSet = z.infer<typeof ScopeSetSchema>;

// --- list (GET /api/admin/partnerships) -----------------------------------

/** A single partnership row (list item — admin-api.md § GET response `items`). */
export const PartnershipSchema = z.object({
  partnershipId: z.string(),
  hostTenantId: z.string(),
  partnerTenantId: z.string(),
  status: PartnershipStatusSchema,
  delegatedScope: ScopeSetSchema,
  myRole: PartnershipMyRoleSchema,
  invitedAt: z.string(),
  // Present once accepted (PENDING rows have no acceptedAt).
  acceptedAt: z.string().nullable().optional(),
  participantCount: z.number().int().nonnegative(),
});
export type Partnership = z.infer<typeof PartnershipSchema>;

/** GET envelope — `{ items, page, size, totalElements, totalPages }`. */
export const PartnershipListSchema = z.object({
  items: z.array(PartnershipSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});
export type PartnershipList = z.infer<typeof PartnershipListSchema>;

export interface PartnershipListParams {
  /** `host` | `partner` filter; undefined ⇒ both sides. */
  role?: PartnershipMyRole;
  /** status filter; undefined ⇒ all. */
  status?: PartnershipStatus;
  page?: number;
  size?: number;
}

// --- mutation result shapes -----------------------------------------------

/**
 * The lifecycle-mutation response (invite 201 / accept·suspend·reactivate·
 * terminate 200). The invite response omits `myRole` / `participantCount`, and
 * accept adds `acceptedAt` — so those three are optional here. `.passthrough()`
 * tolerates a forward-compat field. The console does not RENDER this result
 * (it `router.refresh()`es on success and re-reads the list); the schema only
 * has to not crash the parse.
 */
export const PartnershipMutationResultSchema = z
  .object({
    partnershipId: z.string(),
    hostTenantId: z.string(),
    partnerTenantId: z.string(),
    status: PartnershipStatusSchema,
    delegatedScope: ScopeSetSchema,
    myRole: PartnershipMyRoleSchema.optional(),
    invitedAt: z.string(),
    acceptedAt: z.string().nullable().optional(),
    participantCount: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type PartnershipMutationResult = z.infer<
  typeof PartnershipMutationResultSchema
>;

/** participant add 201 — `{ partnershipId, operatorId, participantScope?, assignedAt }`
 *  (`participantScope` omitted when null ⟺ full delegatedScope). */
export const ParticipantResultSchema = z
  .object({
    partnershipId: z.string(),
    operatorId: z.string(),
    participantScope: ScopeSetSchema.nullable().optional(),
    assignedAt: z.string(),
  })
  .passthrough();
export type ParticipantResult = z.infer<typeof ParticipantResultSchema>;

// --- request DTOs ---------------------------------------------------------

export interface InvitePartnershipInput {
  /** Partner tenant id (`!= host`, `!= '*'`, existing ACTIVE tenant — producer
   *  is the authority). The host tenant is NEVER client-supplied: it is the
   *  server-side active tenant (`X-Tenant-Id`). */
  partnerTenantId: string;
  /** The bounded delegation the host grants (domains + non-admin roles). */
  delegatedScope: ScopeSet;
}

export interface ParticipantAddInput {
  /** `null` ⟺ full delegatedScope (net-zero default); non-null must be
   *  `⊆ delegatedScope` (producer `422 PARTICIPANT_SCOPE_EXCEEDS_DELEGATION`). */
  participantScope: ScopeSet | null;
}
