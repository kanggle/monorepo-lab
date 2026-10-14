import {
  DomainRoleListSchema,
  DomainPermissionSetSchema,
  DomainPermissionSetListSchema,
  type DomainRoleList,
  type DomainPermissionSet,
  type DomainPermissionSetList,
  type CreateDomainPermissionSetInput,
  type UpdateDomainPermissionSetInput,
} from './types';
import {
  callDomainPermissionSets,
  DOMAIN_ROLES_PATH,
  DOMAIN_PERMISSION_SETS_PREFIX,
} from './domain-permission-sets-client';

/**
 * Server-side ADR-MONO-083 「업무 권한 세트」 API functions (TASK-PC-FE-338 /
 * admin-api.md § 도메인 업무 권한 세트). Every call goes through
 * {@link callDomainPermissionSets} (the hardened `callAdminGateway` core) —
 * server-only by construction (imported exclusively from server components
 * and `runtime = 'nodejs'` route handlers).
 *
 * Header matrix per the contract (rows 1-5 — this slice only; rows 6-11, the
 * operator/group grant/revoke surfaces, are a later slice):
 *   1. `GET /domain-roles` — no reason, no idempotency key.
 *   2. `GET /domain-permission-sets[/{setId}]` — no reason, no idempotency key.
 *   3. `POST /domain-permission-sets` — reason + idempotency key REQUIRED.
 *   4. `PATCH /domain-permission-sets/{setId}` — reason required, idempotency
 *      key MUST NOT be sent.
 *   5. `DELETE /domain-permission-sets/{setId}` — reason required, idempotency
 *      key MUST NOT be sent.
 */

// 1. role catalog — GET /api/admin/domain-roles ------------------------------

export async function listDomainRoles(): Promise<DomainRoleList> {
  return callDomainPermissionSets(
    { method: 'GET', path: DOMAIN_ROLES_PATH },
    (json) => DomainRoleListSchema.parse(json),
  );
}

// 2. list sets — GET /api/admin/domain-permission-sets -----------------------

export async function listDomainPermissionSets(): Promise<DomainPermissionSetList> {
  return callDomainPermissionSets(
    { method: 'GET', path: DOMAIN_PERMISSION_SETS_PREFIX },
    (json) => DomainPermissionSetListSchema.parse(json),
  );
}

// 2b. get one — GET /api/admin/domain-permission-sets/{setId} ---------------

export async function getDomainPermissionSet(
  setId: string,
): Promise<DomainPermissionSet> {
  return callDomainPermissionSets(
    {
      method: 'GET',
      path: `${DOMAIN_PERMISSION_SETS_PREFIX}/${encodeURIComponent(setId)}`,
    },
    (json) => DomainPermissionSetSchema.parse(json),
  );
}

// 3. create — POST /api/admin/domain-permission-sets (201; reason + Idempotency-Key) --

export async function createDomainPermissionSet(
  input: CreateDomainPermissionSetInput,
  reason: string,
  idempotencyKey: string,
): Promise<DomainPermissionSet> {
  const body: Record<string, unknown> = {
    name: input.name,
    roles: input.roles,
  };
  if (input.description !== undefined) body.description = input.description;
  return callDomainPermissionSets(
    {
      method: 'POST',
      path: DOMAIN_PERMISSION_SETS_PREFIX,
      reason,
      idempotencyKey,
      body,
    },
    (json) => DomainPermissionSetSchema.parse(json),
  );
}

// 4. edit — PATCH /api/admin/domain-permission-sets/{setId} (reason only, NO key) ---

export async function updateDomainPermissionSet(
  setId: string,
  patch: UpdateDomainPermissionSetInput,
  reason: string,
): Promise<DomainPermissionSet> {
  const body: Record<string, unknown> = {};
  if (patch.name !== undefined) body.name = patch.name;
  if (patch.description !== undefined) body.description = patch.description;
  if (patch.roles !== undefined) body.roles = patch.roles;
  return callDomainPermissionSets(
    {
      method: 'PATCH',
      path: `${DOMAIN_PERMISSION_SETS_PREFIX}/${encodeURIComponent(setId)}`,
      reason,
      body,
    },
    (json) => DomainPermissionSetSchema.parse(json),
  );
}

// 5. delete — DELETE /api/admin/domain-permission-sets/{setId} (204, reason only, NO key) --

export async function deleteDomainPermissionSet(
  setId: string,
  reason: string,
): Promise<void> {
  await callDomainPermissionSets(
    {
      method: 'DELETE',
      path: `${DOMAIN_PERMISSION_SETS_PREFIX}/${encodeURIComponent(setId)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}
