import { clampPageSize } from '@/shared/lib/pagination';
import {
  GroupSchema,
  GroupPageSchema,
  GroupMemberListSchema,
  GroupMemberAddResultSchema,
  GroupGrantListSchema,
  GroupGrantAddResultSchema,
  type Group,
  type GroupPage,
  type GroupMemberList,
  type GroupMemberAddResult,
  type GroupGrantList,
  type GroupGrantAddResult,
  type GroupListParams,
  type CreateGroupInput,
  type UpdateGroupInput,
  type AddMemberInput,
  type AddGrantsInput,
} from './types';
import { callGapGroups, GROUPS_PREFIX } from './operator-groups-client';

/**
 * Server-side IAM operator-group API functions (TASK-PC-FE-250 / ADR-MONO-046 /
 * admin-api.md § Operator Group Management). Every call goes through
 * {@link callGapGroups} (the hardened `callAdminGateway` core) — server-only by
 * construction (imported exclusively from server components and
 * `runtime = 'nodejs'` route handlers).
 *
 * Path-segment ids are UUIDs (`groupId` / `grantId` / `operatorId`) —
 * `encodeURIComponent`-escaped; names / descriptions ride in the JSON body
 * only. Header matrix per the contract: create / add-member / add-grants carry
 * `X-Operator-Reason` + `Idempotency-Key`; update / delete / remove-member /
 * revoke-grant carry `X-Operator-Reason` only.
 */

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;

// 1. list — GET /api/admin/groups -------------------------------------------

export async function listGroups(
  params: GroupListParams = {},
): Promise<GroupPage> {
  const qs = new URLSearchParams();
  if (params.tenantId) qs.set('tenantId', params.tenantId);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(clampPageSize(params.size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE)),
  );
  return callGapGroups(
    { method: 'GET', path: `${GROUPS_PREFIX}?${qs.toString()}` },
    (json) => GroupPageSchema.parse(json),
  );
}

// 2. get — GET /api/admin/groups/{groupId} ----------------------------------

export async function getGroup(groupId: string): Promise<Group> {
  return callGapGroups(
    { method: 'GET', path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}` },
    (json) => GroupSchema.parse(json),
  );
}

// 3. create — POST /api/admin/groups (201; reason + Idempotency-Key) ---------

export async function createGroup(
  input: CreateGroupInput,
  reason: string,
  idempotencyKey: string,
): Promise<Group> {
  const body: Record<string, unknown> = {
    tenantId: input.tenantId,
    name: input.name,
  };
  if (input.description !== undefined) body.description = input.description;
  return callGapGroups(
    { method: 'POST', path: GROUPS_PREFIX, reason, idempotencyKey, body },
    (json) => GroupSchema.parse(json),
  );
}

// 4. update — PATCH /api/admin/groups/{groupId} (rename / describe) ----------

export async function updateGroup(
  groupId: string,
  patch: UpdateGroupInput,
  reason: string,
): Promise<Group> {
  const body: Record<string, unknown> = {};
  if (patch.name !== undefined) body.name = patch.name;
  if (patch.description !== undefined) body.description = patch.description;
  return callGapGroups(
    {
      method: 'PATCH',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}`,
      reason,
      body,
    },
    (json) => GroupSchema.parse(json),
  );
}

// 5. delete — DELETE /api/admin/groups/{groupId} (204 no content) ------------

export async function deleteGroup(
  groupId: string,
  reason: string,
): Promise<void> {
  await callGapGroups(
    {
      method: 'DELETE',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}

// 6. list members — GET /{groupId}/members ----------------------------------

export async function listMembers(groupId: string): Promise<GroupMemberList> {
  return callGapGroups(
    {
      method: 'GET',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/members`,
    },
    (json) => GroupMemberListSchema.parse(json),
  );
}

// 7. add member — POST /{groupId}/members (201; reason + Idempotency-Key) ----

export async function addMember(
  groupId: string,
  input: AddMemberInput,
  reason: string,
  idempotencyKey: string,
): Promise<GroupMemberAddResult> {
  return callGapGroups(
    {
      method: 'POST',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/members`,
      reason,
      idempotencyKey,
      body: { operatorId: input.operatorId },
    },
    (json) => GroupMemberAddResultSchema.parse(json),
  );
}

// 8. remove member — DELETE /{groupId}/members/{operatorId} (204) -----------

export async function removeMember(
  groupId: string,
  operatorId: string,
  reason: string,
): Promise<void> {
  await callGapGroups(
    {
      method: 'DELETE',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/members/${encodeURIComponent(operatorId)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}

// 9. list grants — GET /{groupId}/grants ------------------------------------

export async function listGrants(groupId: string): Promise<GroupGrantList> {
  return callGapGroups(
    {
      method: 'GET',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/grants`,
    },
    (json) => GroupGrantListSchema.parse(json),
  );
}

// 10. add grants — POST /{groupId}/grants (201; reason + Idempotency-Key) ----

export async function addGrants(
  groupId: string,
  input: AddGrantsInput,
  reason: string,
  idempotencyKey: string,
): Promise<GroupGrantAddResult> {
  const body: Record<string, unknown> = {};
  if (input.roles !== undefined) body.roles = input.roles;
  if (input.tenantAssignments !== undefined) {
    body.tenantAssignments = input.tenantAssignments;
  }
  return callGapGroups(
    {
      method: 'POST',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/grants`,
      reason,
      idempotencyKey,
      body,
    },
    (json) => GroupGrantAddResultSchema.parse(json),
  );
}

// 11. revoke grant — DELETE /{groupId}/grants/{grantId} (204) ---------------

export async function removeGrant(
  groupId: string,
  grantId: string,
  reason: string,
): Promise<void> {
  await callGapGroups(
    {
      method: 'DELETE',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/grants/${encodeURIComponent(grantId)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}
