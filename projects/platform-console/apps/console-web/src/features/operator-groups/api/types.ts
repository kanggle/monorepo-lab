import { z } from 'zod';

/**
 * Feature-local types for the ADR-MONO-046 operator-group surface
 * (TASK-PC-FE-250).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam-platform/specs/contracts/http/admin-api.md`
 *   § "Operator Group Management (ADR-MONO-046)" —
 *     `POST/GET /api/admin/groups`,
 *     `GET/PATCH/DELETE /api/admin/groups/{groupId}`,
 *     `GET/POST /api/admin/groups/{groupId}/members`,
 *     `DELETE /api/admin/groups/{groupId}/members/{operatorId}`,
 *     `GET/POST /api/admin/groups/{groupId}/grants`,
 *     `DELETE /api/admin/groups/{groupId}/grants/{grantId}`.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. Feature-local (not cross-feature) per architecture.md
 * § Allowed Dependencies.
 *
 * ── SEMANTICS (ADR-MONO-046 D2-A — the correctness core of this surface):
 *   - A group is a NAMED aggregate of operators; a group grant is a TEMPLATE
 *     (역할 or tenant-assignment) that fans out to a plain per-operator flat
 *     row (`group_origin` marker) for every current member. Group membership is
 *     NOT an evaluation-time edge — the derived permission is an ordinary direct
 *     grant. This screen manages the aggregate + its membership/grant templates
 *     only; it never becomes a new confinement axis.
 *   - Every endpoint (reads included) is `group.manage`-gated; every mutation is
 *     `X-Operator-Reason`-gated; role grants + add-member additionally pass the
 *     ≤-own no-escalation guard (surfaced verbatim as `403 ROLE_GRANT_FORBIDDEN`
 *     / `422 GROUP_GRANT_NO_ESCALATION`).
 */

/**
 * The assignable roles offered by the group-grant role picker, pre-filtered by
 * the operator's server-provided grantable set (see `GroupGrantDialog`).
 * Modelled on `features/operators` `KNOWN_OPERATOR_ROLES` but feature-local
 * (no cross-feature import) and with `SUPER_ADMIN` DELIBERATELY absent — a
 * group grant can never mint the platform-wide role (`RoleGrantGuard` refuses
 * it server-side; the contract's POST /grants row rejects it as
 * `403 ROLE_GRANT_FORBIDDEN`).
 */
export const KNOWN_GROUP_ROLES = [
  'TENANT_ADMIN',
  'TENANT_BILLING_ADMIN',
  'SUPPORT_LOCK',
  'SUPPORT_READONLY',
  'SECURITY_ANALYST',
] as const;

/**
 * The role the group-grant picker must NEVER offer. `RoleGrantGuard`
 * (ADR-MONO-024 § D3, reused by ADR-MONO-046 D4) refuses it server-side; this
 * constant keeps the client picker honest even if {@link KNOWN_GROUP_ROLES} is
 * ever widened by mistake.
 */
export const ELEVATED_ROLE_NEVER_GRANTABLE = 'SUPER_ADMIN';

// --- group ------------------------------------------------------------------

export const GroupSchema = z.object({
  groupId: z.string(),
  tenantId: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  memberCount: z.number().int(),
  grantCount: z.number().int(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type Group = z.infer<typeof GroupSchema>;

/** `GET /api/admin/groups` — a Spring page envelope (`items`, not `content`). */
export const GroupPageSchema = z.object({
  items: z.array(GroupSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});
export type GroupPage = z.infer<typeof GroupPageSchema>;

// --- members ----------------------------------------------------------------

export const GroupMemberSchema = z.object({
  operatorId: z.string(),
  displayName: z.string(),
  addedAt: z.string(),
});
export type GroupMember = z.infer<typeof GroupMemberSchema>;

export const GroupMemberListSchema = z.object({
  items: z.array(GroupMemberSchema),
});
export type GroupMemberList = z.infer<typeof GroupMemberListSchema>;

/**
 * `POST /{groupId}/members` 201 — the member row plus `fannedOutGrants` (the
 * number of grants newly materialised for this member; equivalent direct grants
 * are idempotent-skipped and not counted). Kept lenient (`fannedOutGrants`
 * optional int) so a producer that omits it never crashes the parse.
 */
export const GroupMemberAddResultSchema = GroupMemberSchema.extend({
  fannedOutGrants: z.number().int().optional(),
});
export type GroupMemberAddResult = z.infer<typeof GroupMemberAddResultSchema>;

// --- grants (discriminated union on `type`) --------------------------------

export const GROUP_GRANT_TYPES = ['ROLE', 'TENANT_ASSIGNMENT'] as const;
export type GroupGrantType = (typeof GROUP_GRANT_TYPES)[number];

export const GroupGrantSchema = z.discriminatedUnion('type', [
  z.object({
    grantId: z.string(),
    type: z.literal('ROLE'),
    roleName: z.string(),
    grantedAt: z.string(),
  }),
  z.object({
    grantId: z.string(),
    type: z.literal('TENANT_ASSIGNMENT'),
    tenantId: z.string(),
    grantedAt: z.string(),
  }),
]);
export type GroupGrant = z.infer<typeof GroupGrantSchema>;

export const GroupGrantListSchema = z.object({
  items: z.array(GroupGrantSchema),
});
export type GroupGrantList = z.infer<typeof GroupGrantListSchema>;

/** `POST /{groupId}/grants` 201 — the created grant templates + fan-out count
 *  (`fannedOutRows`; equivalent direct grants idempotent-skipped). */
export const GroupGrantAddResultSchema = z.object({
  items: z.array(GroupGrantSchema),
  fannedOutRows: z.number().int().optional(),
});
export type GroupGrantAddResult = z.infer<typeof GroupGrantAddResultSchema>;

// --- inputs -----------------------------------------------------------------

export interface CreateGroupInput {
  tenantId: string;
  name: string;
  description?: string;
}

/** At least one of `name` / `description` must be present (producer + proxy
 *  refine). */
export interface UpdateGroupInput {
  name?: string;
  description?: string;
}

export interface AddMemberInput {
  operatorId: string;
}

/** At least one of `roles` / `tenantAssignments` must be non-empty (producer +
 *  proxy refine). */
export interface AddGrantsInput {
  roles?: string[];
  tenantAssignments?: { tenantId: string }[];
}

export interface GroupListParams {
  /** Optional tenant filter; absent ⇒ the actor's full `group.manage` scope. */
  tenantId?: string;
  page?: number;
  size?: number;
}
