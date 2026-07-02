import {
  OperatorAssignmentsResponseSchema,
  type OperatorAssignmentsResponse,
  OperatorAssignmentSchema,
  type OperatorAssignment,
  SetOrgScopeResultSchema,
  type SetOrgScopeResult,
  type SetOrgScopeInput,
} from './types';
import { callGapOperators, OPERATORS_PREFIX } from './operators-client';

/**
 * operators api — tenant-assignment surface (TASK-PC-FE-110 split;
 * TASK-PC-FE-050 / TASK-BE-339 org-scope + TASK-PC-FE-157 / TASK-BE-347
 * assignment create/remove). Reads the (operator, active-tenant) assignment
 * row, sets / clears its `org_scope` (부서 subtree-root id 배열), and
 * creates / removes the assignment row itself ("내 직원에게 내 테넌트 접근
 * 부여"). Re-exported verbatim through the `operators-api` barrel.
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

// ---------------------------------------------------------------------------
// 11. assign-operator — POST /api/admin/operators/{operatorId}/assignments/{tenantId}
//     TASK-PC-FE-157 / TASK-BE-347 (ADR-MONO-024 D3-i). Creates the
//     (operator, tenant) `operator_tenant_assignment` row ("내 직원에게 내
//     테넌트 접근 부여"). The created row is whole-tenant (org_scope=null ⟺
//     ["*"], permission_set_id=null = operator-level role 상속); org_scope is
//     later refined via the org-scope PUT above.
//
//     PER-ENDPOINT HEADER MATRIX: `X-Operator-Reason` REQUIRED (producer
//     `400 REASON_REQUIRED` otherwise); NO `Idempotency-Key` (the (operator,
//     tenant) PK is the natural dedupe — a re-create is `409
//     ASSIGNMENT_ALREADY_EXISTS`, mirroring the /roles + /status non-uniform
//     matrix). Tenant confinement is producer-side (`TenantScopeGuard` on the
//     path `tenantId` — a `TENANT_ADMIN @ acme` may assign to acme only;
//     SUPER_ADMIN `'*'` net-zero). `201` returns the created assignment.
// ---------------------------------------------------------------------------

export async function assignOperatorToTenant(
  operatorId: string,
  tenantId: string,
  reason: string,
): Promise<OperatorAssignment> {
  return callGapOperators(
    {
      method: 'POST',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/assignments/${encodeURIComponent(tenantId)}`,
      reason,
      // No body — the assignment is fully specified by the path (operatorId,
      // tenantId). The producer creates a whole-tenant row.
    },
    (json) => OperatorAssignmentSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 12. unassign-operator — DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}
//     TASK-PC-FE-157 / TASK-BE-347. Removes the (operator, tenant) assignment
//     row. `X-Operator-Reason` REQUIRED; NO `Idempotency-Key`. `204` no
//     content. A home-tenant-only operator (no explicit assignment) → `404
//     ASSIGNMENT_NOT_FOUND` (maps inline). Confinement identical to POST.
// ---------------------------------------------------------------------------

export async function unassignOperatorFromTenant(
  operatorId: string,
  tenantId: string,
  reason: string,
): Promise<void> {
  await callGapOperators(
    {
      method: 'DELETE',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/assignments/${encodeURIComponent(tenantId)}`,
      reason,
      expectNoContent: true,
    },
    () => undefined,
  );
}
