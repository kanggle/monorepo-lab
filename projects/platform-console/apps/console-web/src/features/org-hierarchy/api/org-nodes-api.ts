import {
  OrgNodeSchema,
  OrgNodeListSchema,
  SubtreeTenantsSchema,
  OrgAdminListSchema,
  OrgAdminGrantSchema,
  PlacementEffectSchema,
  PlacementResultSchema,
  type PlacementEffect,
  type PlacementResult,
  type OrgNode,
  type OrgNodeList,
  type SubtreeTenants,
  type OrgAdminList,
  type OrgAdminGrant,
  type Ceiling,
  type CreateOrgNodeInput,
  type UpdateOrgNodeInput,
  type GrantOrgAdminInput,
} from './types';
import { callOrgNodes, ORG_NODES_PREFIX } from './org-nodes-client';

/** The placement endpoints live under the TENANT path but are `org.manage`-
 *  gated org-hierarchy writes (TASK-BE-625) — so they ride the org-nodes
 *  profile (same degrade class, same `org_nodes` log prefix). */
const TENANT_PLACEMENT_PREFIX = '/api/admin/tenants';

/**
 * Server-side IAM org-node hierarchy API functions (TASK-PC-FE-237 / ADR-047 /
 * admin-api.md § org-node). Every call goes through {@link callOrgNodes} (the
 * hardened `callAdminGateway` core) — server-only by construction (imported
 * exclusively from server components and `runtime = 'nodejs'` route handlers).
 *
 * Path-segment ids are UUIDs/numbers (never node names — the non-ASCII-name
 * `[id]` trap, task Edge Case); names ride in the JSON body only.
 */

// 1. list — GET /api/admin/org-nodes (flat, reach-scoped by the server) ------

export async function listOrgNodes(): Promise<OrgNodeList> {
  return callOrgNodes(
    { method: 'GET', path: ORG_NODES_PREFIX },
    (json) => OrgNodeListSchema.parse(json),
  );
}

// 2. get — GET /api/admin/org-nodes/{orgNodeId} ------------------------------

export async function getOrgNode(id: string): Promise<OrgNode> {
  return callOrgNodes(
    { method: 'GET', path: `${ORG_NODES_PREFIX}/${encodeURIComponent(id)}` },
    (json) => OrgNodeSchema.parse(json),
  );
}

// 3. create — POST /api/admin/org-nodes --------------------------------------

export async function createOrgNode(
  input: CreateOrgNodeInput,
  reason: string,
): Promise<OrgNode> {
  return callOrgNodes(
    {
      method: 'POST',
      path: ORG_NODES_PREFIX,
      reason,
      body: {
        name: input.name,
        parentId: input.parentId,
        ceiling: input.ceiling,
      },
    },
    (json) => OrgNodeSchema.parse(json),
  );
}

// 4. update — PATCH /api/admin/org-nodes/{orgNodeId} (rename / re-parent) -----

export async function updateOrgNode(
  id: string,
  patch: UpdateOrgNodeInput,
  reason: string,
): Promise<OrgNode> {
  const body: Record<string, unknown> = {};
  if (patch.name !== undefined) body.name = patch.name;
  if (patch.parentId !== undefined) body.parentId = patch.parentId;
  return callOrgNodes(
    {
      method: 'PATCH',
      path: `${ORG_NODES_PREFIX}/${encodeURIComponent(id)}`,
      reason,
      body,
    },
    (json) => OrgNodeSchema.parse(json),
  );
}

// 5. delete — DELETE /api/admin/org-nodes/{orgNodeId} (204 no content) --------

export async function deleteOrgNode(id: string, reason: string): Promise<void> {
  await callOrgNodes(
    {
      method: 'DELETE',
      path: `${ORG_NODES_PREFIX}/${encodeURIComponent(id)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}

// 6. set ceiling — PUT /api/admin/org-nodes/{orgNodeId}/ceiling --------------
//    Body IS the ceiling (not wrapped).

export async function setOrgNodeCeiling(
  id: string,
  ceiling: Ceiling,
  reason: string,
): Promise<OrgNode> {
  return callOrgNodes(
    {
      method: 'PUT',
      path: `${ORG_NODES_PREFIX}/${encodeURIComponent(id)}/ceiling`,
      reason,
      body: ceiling,
    },
    (json) => OrgNodeSchema.parse(json),
  );
}

// 7. subtree tenants — GET /api/admin/org-nodes/{orgNodeId}/tenants ----------

export async function getOrgNodeTenants(id: string): Promise<SubtreeTenants> {
  return callOrgNodes(
    {
      method: 'GET',
      path: `${ORG_NODES_PREFIX}/${encodeURIComponent(id)}/tenants`,
    },
    (json) => SubtreeTenantsSchema.parse(json),
  );
}

// 8. list admins — GET /api/admin/org-nodes/{orgNodeId}/admins ---------------

export async function listOrgNodeAdmins(id: string): Promise<OrgAdminList> {
  return callOrgNodes(
    {
      method: 'GET',
      path: `${ORG_NODES_PREFIX}/${encodeURIComponent(id)}/admins`,
    },
    (json) => OrgAdminListSchema.parse(json),
  );
}

// 9. grant admin — POST /api/admin/org-nodes/{orgNodeId}/admins (201) --------

export async function grantOrgNodeAdmin(
  id: string,
  input: GrantOrgAdminInput,
  reason: string,
): Promise<OrgAdminGrant> {
  return callOrgNodes(
    {
      method: 'POST',
      path: `${ORG_NODES_PREFIX}/${encodeURIComponent(id)}/admins`,
      reason,
      body: { operatorId: input.operatorId, roleName: input.roleName },
    },
    (json) => OrgAdminGrantSchema.parse(json),
  );
}

// 10. revoke admin — DELETE /{orgNodeId}/admins/{operatorId} (204) -----------

export async function revokeOrgNodeAdmin(
  id: string,
  operatorId: string,
  reason: string,
): Promise<void> {
  await callOrgNodes(
    {
      method: 'DELETE',
      path: `${ORG_NODES_PREFIX}/${encodeURIComponent(id)}/admins/${encodeURIComponent(operatorId)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}

// 11. placement preview — GET /api/admin/tenants/{tenantId}/org-node/preview --
//     (TASK-PC-FE-312 / TASK-BE-625). `toOrgNodeId === null` ⇒ the query
//     parameter is OMITTED (= detach). Same two-sided reach check as the write.

export async function previewTenantPlacement(
  tenantId: string,
  toOrgNodeId: string | null,
): Promise<PlacementEffect> {
  const qs =
    toOrgNodeId === null
      ? ''
      : `?orgNodeId=${encodeURIComponent(toOrgNodeId)}`;
  return callOrgNodes(
    {
      method: 'GET',
      path: `${TENANT_PLACEMENT_PREFIX}/${encodeURIComponent(tenantId)}/org-node/preview${qs}`,
    },
    (json) => PlacementEffectSchema.parse(json),
  );
}

// 12. placement write — PUT /api/admin/tenants/{tenantId}/org-node ------------
//     Body `{ orgNodeId }` — the KEY is always present; `null` = detach (the
//     producer 400s a `{}` body so a typo'd key never silently detaches).

export async function placeTenant(
  tenantId: string,
  toOrgNodeId: string | null,
  reason: string,
): Promise<PlacementResult> {
  return callOrgNodes(
    {
      method: 'PUT',
      path: `${TENANT_PLACEMENT_PREFIX}/${encodeURIComponent(tenantId)}/org-node`,
      reason,
      body: { orgNodeId: toOrgNodeId },
    },
    (json) => PlacementResultSchema.parse(json),
  );
}
