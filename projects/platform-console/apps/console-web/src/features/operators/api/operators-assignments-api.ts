import {
  OperatorAssignmentsResponseSchema,
  type OperatorAssignmentsResponse,
  SetOrgScopeResultSchema,
  type SetOrgScopeResult,
  type SetOrgScopeInput,
} from './types';
import { callGapOperators, OPERATORS_PREFIX } from './operators-client';

/**
 * operators api — org-scope assignments surface (TASK-PC-FE-110 split;
 * TASK-PC-FE-050 / TASK-BE-339). Reads the (operator, active-tenant)
 * assignment row and sets / clears its `org_scope` (부서 subtree-root id
 * 배열). Re-exported verbatim through the `operators-api` barrel. 0 behavior
 * change.
 */

// ---------------------------------------------------------------------------
// 9. list-assignments — GET /api/admin/operators/{operatorId}/assignments
//    TASK-PC-FE-050 / TASK-BE-339. Reads the operator's assignment row for
//    the ACTIVE tenant (0 or 1 rows; empty ⇒ home-tenant-only operator with
//    no explicit assignment ⇒ org_scope 부적용 / 전체). READ — no mutation
//    headers (no reason / no idempotency key, per the producer). The active
//    tenant is sent as `X-Tenant-Id` by `callGapOperators`; the producer
//    scopes the response to it (타 테넌트 assignment 미노출).
// ---------------------------------------------------------------------------

export async function listOperatorAssignments(
  operatorId: string,
): Promise<OperatorAssignmentsResponse> {
  return callGapOperators(
    {
      method: 'GET',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/assignments`,
    },
    (json) => OperatorAssignmentsResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 10. set-org-scope —
//     PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope
//     TASK-PC-FE-050 / TASK-BE-339. Sets / clears the (operator, tenant)
//     assignment's `org_scope` (부서 subtree-root id 배열). reason-gated —
//     `X-Operator-Reason` REQUIRED (producer `400 REASON_REQUIRED`
//     otherwise); NO `Idempotency-Key` (mirror the /roles + /status
//     non-uniformity — full-replace PUT is naturally idempotent).
//
//     The body carries `orgScope` with the THREE-WAY semantics preserved
//     verbatim — `null` (clear / 전체), `[]` (explicit 차단 / zero-scope,
//     distinct from null), or `[ids]` (subtree set). `orgScope: null` is
//     serialised as the JSON `null` literal (NOT omitted) — the
//     `body !== undefined` guard in `callGapOperators` keeps `{ orgScope }`
//     on the wire so the producer sees the clear intent explicitly.
//
//     `path tenantId` MUST equal the active `X-Tenant-Id` (producer
//     `403 TENANT_SCOPE_MISMATCH` otherwise) — the caller passes the
//     assignment row's own `tenantId` (= the active tenant) so the two
//     always agree.
// ---------------------------------------------------------------------------

export async function setOperatorOrgScope(
  operatorId: string,
  tenantId: string,
  input: SetOrgScopeInput,
  reason: string,
): Promise<SetOrgScopeResult> {
  return callGapOperators(
    {
      method: 'PUT',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/assignments/${encodeURIComponent(tenantId)}/org-scope`,
      reason,
      // NO idempotencyKey — per the producer header matrix (mirror /roles +
      // /status; idempotent full-replace PUT).
      // `orgScope` may be `null` (clear) — kept as an explicit JSON null
      // (NOT omitted) so the producer reads the clear intent.
      body: { orgScope: input.orgScope },
    },
    (json) => SetOrgScopeResultSchema.parse(json),
  );
}
