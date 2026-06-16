import { getActiveTenant } from '@/shared/lib/session';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  OperatorPageSchema,
  type OperatorPage,
  type OperatorListParams,
  CreateOperatorResultSchema,
  type CreateOperatorResult,
  type CreateOperatorInput,
  EditRolesResultSchema,
  type EditRolesResult,
  ChangeStatusResultSchema,
  type ChangeStatusResult,
  type OperatorStatus,
} from './types';
import { callGapOperators, OPERATORS_PREFIX } from './operators-client';

/**
 * operators api — admin operator management (TASK-PC-FE-110 split). The
 * privilege-sensitive surface: list + create + edit-roles + change-status +
 * admin-set-profile. Re-exported verbatim through the `operators-api` barrel.
 * 0 behavior change.
 */

// ---------------------------------------------------------------------------
// 1. list — GET /api/admin/operators (status filter + pagination; READ)
//    No mutation headers (per the matrix).
// ---------------------------------------------------------------------------

export async function listOperators(
  params: OperatorListParams = {},
): Promise<OperatorPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  // TASK-MONO-175: scope the operator list to the ACTIVE tenant so 운영자 관리
  // follows the tenant switcher (the producer scopes by the `tenantId` query
  // param — home ∪ assignment — and gates it against the caller's effective
  // scope; mirror of the audit `tenantId` pattern). The same active tenant is
  // also sent as `X-Tenant-Id` by `callGapOperators`; when none is selected
  // that call blocks with NO_ACTIVE_TENANT before any fetch.
  const tenant = await getActiveTenant();
  if (tenant) qs.set('tenantId', tenant);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampPageSize(params.size, 20, 100)));
  return callGapOperators(
    { method: 'GET', path: `${OPERATORS_PREFIX}?${qs.toString()}` },
    (json) => OperatorPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 2. create — POST /api/admin/operators
//    HEADERS: X-Operator-Reason + Idempotency-Key (BOTH required, per the
//    producer). The password is in the body — server-side only, NEVER
//    logged/echoed.
// ---------------------------------------------------------------------------

export async function createOperator(
  input: CreateOperatorInput,
  reason: string,
  idempotencyKey: string,
): Promise<CreateOperatorResult> {
  return callGapOperators(
    {
      method: 'POST',
      path: OPERATORS_PREFIX,
      reason,
      idempotencyKey,
      body: {
        email: input.email,
        displayName: input.displayName,
        password: input.password,
        roles: input.roles,
        tenantId: input.tenantId,
      },
    },
    (json) => CreateOperatorResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 3. edit-roles — PATCH /api/admin/operators/{operatorId}/roles
//    HEADERS: X-Operator-Reason ONLY. The producer does NOT list
//    Idempotency-Key — sending it is a contract deviation; full-replace
//    PATCH is idempotent producer-side. `[]` allowed = remove all roles.
// ---------------------------------------------------------------------------

export async function editOperatorRoles(
  operatorId: string,
  roles: string[],
  reason: string,
): Promise<EditRolesResult> {
  return callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/roles`,
      reason,
      // NO idempotencyKey — per the producer header matrix (§ 2.4.3).
      body: { roles },
    },
    (json) => EditRolesResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 4. change-status — PATCH /api/admin/operators/{operatorId}/status
//    HEADERS: X-Operator-Reason ONLY (NO Idempotency-Key — idempotent PATCH).
// ---------------------------------------------------------------------------

export async function changeOperatorStatus(
  operatorId: string,
  status: OperatorStatus,
  reason: string,
): Promise<ChangeStatusResult> {
  return callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/status`,
      reason,
      // NO idempotencyKey — per the producer header matrix (§ 2.4.3).
      body: { status },
    },
    (json) => ChangeStatusResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 7. admin-set-profile — PATCH /api/admin/operators/{operatorId}/profile
//    (TASK-BE-307 producer / TASK-PC-FE-017 consumer). Admin-on-behalf-of:
//    SUPER_ADMIN sets ANOTHER operator's `operatorContext.defaultAccountId`
//    with `operator.manage` permission + explicit `X-Operator-Reason`. Self
//    via this path → producer `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`
//    (the UI gates the per-row button when the row is self — UX layer; this
//    api fn forwards whatever the producer returns).
//
//    HEADERS: `X-Operator-Reason` required (producer rejects empty); NO
//    `Idempotency-Key` per the producer matrix (mirror rows 13 + 14
//    `{id}/roles` + `{id}/status` non-uniformity — full-replace PATCH on the
//    column is idempotent; sending the key is a header-matrix-drift defect).
//    204 No Content on success. The value is the target operator's chosen
//    default finance-platform account UUID (opaque to IAM — TASK-BE-304
//    § Decision authority); `null` clears the column.
// ---------------------------------------------------------------------------

export async function setOperatorProfile(
  operatorId: string,
  defaultAccountId: string | null,
  reason: string,
): Promise<void> {
  await callGapOperators(
    {
      method: 'PATCH',
      path: `${OPERATORS_PREFIX}/${encodeURIComponent(operatorId)}/profile`,
      reason,
      // NO idempotencyKey — per the producer header matrix (§ 2.4.3 row 7,
      // mirror /roles + /status). Header-matrix-drift defense.
      body: {
        operatorContext: { defaultAccountId },
      },
      expectNoContent: true,
    },
    () => undefined,
  );
}
