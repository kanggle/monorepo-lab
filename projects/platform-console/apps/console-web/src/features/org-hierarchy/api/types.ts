import { z } from 'zod';

/**
 * Feature-local types for the ADR-047 org-node hierarchy surface
 * (TASK-PC-FE-237).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam-platform/specs/contracts/http/admin-api.md` § org-node —
 *     `GET/POST /api/admin/org-nodes`, `GET/PATCH/DELETE /{orgNodeId}`,
 *     `PUT /{orgNodeId}/ceiling`, `GET /{orgNodeId}/tenants`,
 *     `GET/POST/DELETE /{orgNodeId}/admins`.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. Feature-local (not cross-feature) per architecture.md § Allowed
 * Dependencies.
 *
 * ── SEMANTICS (ADR-047 D2/D3 — the correctness core of this feature):
 *
 *   - A `Ceiling` is an entitlement **CAP**, not a grant. It only ever NARROWS
 *     which domains a company's tenants may use; selecting a domain grants
 *     nothing.
 *   - `UNBOUNDED` and `BOUNDED([])` are OPPOSITES, never one control:
 *       · `UNBOUNDED` — no cap at all (a domain added tomorrow is still
 *         permitted; it is NOT "all currently known domains").
 *       · `BOUNDED([])` — permits NOTHING.
 */

/** The five entitlement domains a ceiling may bound. */
export const ORG_DOMAIN_KEYS = ['wms', 'scm', 'erp', 'finance', 'iam'] as const;
export type OrgDomainKey = (typeof ORG_DOMAIN_KEYS)[number];

/** The role granted to a node's admins (ADR-047 D3 — subtree-scoped). */
export const ORG_ADMIN_ROLE = 'ORG_ADMIN';

/** Max org-tree depth (root = 1). */
export const MAX_ORG_NODE_DEPTH = 5;

/**
 * The assignable roles offered by the admin-grant selector, pre-filtered by
 * the operator's server-provided grantable set (see `OrgAdminPanel`). Modelled
 * on `features/operators` `KNOWN_OPERATOR_ROLES` but with `SUPER_ADMIN`
 * DELIBERATELY absent — a node admin grant is subtree-scoped and can never
 * mint the platform-wide role. `ORG_ADMIN` is the default / primary choice.
 */
export const KNOWN_ORG_ADMIN_ROLES = [
  'ORG_ADMIN',
  'TENANT_ADMIN',
  'TENANT_BILLING_ADMIN',
  'SUPPORT_LOCK',
  'SUPPORT_READONLY',
  'SECURITY_ANALYST',
] as const;

/**
 * The role the node-admin selector must NEVER offer. `RoleGrantGuard`
 * (ADR-024 D3, reused unchanged by ADR-047 D5) refuses it server-side; this
 * constant keeps the client selector honest even if
 * {@link KNOWN_ORG_ADMIN_ROLES} is ever widened by mistake.
 */
export const ELEVATED_ROLE_NEVER_GRANTABLE = 'SUPER_ADMIN';

// --- ceiling ---------------------------------------------------------------

/**
 * Discriminated on `mode`. `BOUNDED` REQUIRES a `domains: string[]`;
 * `UNBOUNDED` forbids/ignores it (zod strips an incidental `domains` key on
 * an UNBOUNDED payload). `domains` members are `z.string()` (not the
 * `OrgDomainKey` enum) so a forward/unknown domain key from the producer never
 * crashes the parse — the checkbox editor intersects against `ORG_DOMAIN_KEYS`.
 */
export const CeilingSchema = z.discriminatedUnion('mode', [
  z.object({ mode: z.literal('UNBOUNDED') }),
  z.object({ mode: z.literal('BOUNDED'), domains: z.array(z.string()) }),
]);
export type Ceiling = z.infer<typeof CeilingSchema>;

// --- org node --------------------------------------------------------------

export const OrgNodeSchema = z.object({
  orgNodeId: z.string(),
  parentId: z.string().nullable(),
  name: z.string(),
  depth: z.number().int(),
  ceiling: CeilingSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type OrgNode = z.infer<typeof OrgNodeSchema>;

/** `GET /api/admin/org-nodes` — a FLAT array (client assembles the tree). */
export const OrgNodeListSchema = z.object({
  items: z.array(OrgNodeSchema),
});
export type OrgNodeList = z.infer<typeof OrgNodeListSchema>;

// --- subtree tenants -------------------------------------------------------

/** `GET /{orgNodeId}/tenants` — node + ALL descendants. */
export const SubtreeTenantsSchema = z.object({
  tenantIds: z.array(z.string()),
});
export type SubtreeTenants = z.infer<typeof SubtreeTenantsSchema>;

// --- node admins -----------------------------------------------------------

export const OrgAdminSchema = z.object({
  operatorId: z.string(),
  roleName: z.string(),
  grantedAt: z.string(),
});
export type OrgAdmin = z.infer<typeof OrgAdminSchema>;

export const OrgAdminListSchema = z.object({
  items: z.array(OrgAdminSchema),
});
export type OrgAdminList = z.infer<typeof OrgAdminListSchema>;

/** `POST /{orgNodeId}/admins` 201 response. */
export const OrgAdminGrantSchema = z.object({
  orgNodeId: z.string(),
  operatorId: z.string(),
  roleName: z.string(),
  grantedAt: z.string(),
});
export type OrgAdminGrant = z.infer<typeof OrgAdminGrantSchema>;

// --- inputs ----------------------------------------------------------------

export interface CreateOrgNodeInput {
  name: string;
  parentId: string | null;
  ceiling: Ceiling;
}

/** At least one of `name` / `parentId` must be present (producer + proxy
 *  refine). `parentId: null` re-parents to a root. */
export interface UpdateOrgNodeInput {
  name?: string;
  parentId?: string | null;
}

export interface GrantOrgAdminInput {
  operatorId: string;
  roleName: string;
}
