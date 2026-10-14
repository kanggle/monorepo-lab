import {
  GroupDomainSetListSchema,
  GrantGroupDomainSetResultSchema,
  type GrantGroupDomainSetResult,
} from './domain-sets-types';
import { callGapGroups, GROUPS_PREFIX } from './operator-groups-client';

/**
 * Server-side group ADR-MONO-083 「업무 권한 세트」 API functions (TASK-PC-FE-338
 * slice b — admin-api.md § 도메인 업무 권한 세트 rows 9-11). Every call goes
 * through {@link callGapGroups} (the hardened `callAdminGateway` core) —
 * server-only by construction, consumed ONLY by the same-origin BFF routes
 * under `app/api/groups/[groupId]/domain-permission-sets[/[setId]]`.
 *
 * Header matrix (contract rows 9-11 — NOT uniform with the group grant
 * surface's own matrix, see `operator-groups-api.ts`):
 *   - `GET  …/domain-permission-sets`             → no mutation headers (read);
 *   - `POST …/domain-permission-sets` (add)        → `X-Operator-Reason` +
 *     `Idempotency-Key` BOTH required (same posture as add-member/add-grants);
 *   - `DELETE …/domain-permission-sets/{setId}`    → `X-Operator-Reason` ONLY.
 *
 * No `X-Tenant-Id` — the acting tenant is the GROUP's own `tenant_id`
 * (admin-api.md § 행동 테넌트 row 27), mirroring the group grant/member
 * surface exactly.
 */

export async function listGroupDomainSets(groupId: string) {
  return callGapGroups(
    {
      method: 'GET',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/domain-permission-sets`,
    },
    (json) => GroupDomainSetListSchema.parse(json).items,
  );
}

export async function addGroupDomainSet(
  groupId: string,
  setId: string,
  reason: string,
  idempotencyKey: string,
): Promise<GrantGroupDomainSetResult> {
  return callGapGroups(
    {
      method: 'POST',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/domain-permission-sets`,
      reason,
      idempotencyKey,
      body: { setId },
    },
    (json) => GrantGroupDomainSetResultSchema.parse(json),
  );
}

export async function removeGroupDomainSet(
  groupId: string,
  setId: string,
  reason: string,
): Promise<void> {
  await callGapGroups(
    {
      method: 'DELETE',
      path: `${GROUPS_PREFIX}/${encodeURIComponent(groupId)}/domain-permission-sets/${encodeURIComponent(setId)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}
