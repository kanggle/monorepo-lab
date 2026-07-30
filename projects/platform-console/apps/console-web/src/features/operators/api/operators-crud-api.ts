import {
  CreateOperatorResultSchema,
  type CreateOperatorResult,
  type CreateOperatorInput,
  EditRolesResultSchema,
  type EditRolesResult,
  ChangeStatusResultSchema,
  type ChangeStatusResult,
  type OperatorStatus,
  GrantableRolesResponseSchema,
} from './types';
import { callGapOperators, OPERATORS_PREFIX } from './operators-client';

/**
 * operators api — admin operator management (TASK-PC-FE-110 split). The
 * privilege-sensitive surface: list + create + edit-roles + change-status +
 * admin-set-profile + grantable-roles. Re-exported verbatim through the
 * `operators-api` barrel. 0 behavior change.
 *
 * TASK-PC-FE-259 — the LIST read (`listOperators`) was promoted to
 * `shared/api/iam-operators-read.ts` because `features/dashboards` (the
 * § 2.4.4 composed overview, leg 3) and `features/iam-overview` (the `/iam`
 * landing) also consume it, and `architecture.md` § Forbidden Dependencies
 * bars a `features/A → features/B` import ("공유 가치는 `shared/` 로 승격").
 * It is re-exported below so the `operators-api` barrel — the feature's
 * contract-pinned public surface (console-integration-contract § 2.4.4 leg 3,
 * § 3.1) — is unchanged, and the 16-row parity attestation still passes
 * through this path. Every MUTATION here (§ 3.1 rows 12–14, 18) stays
 * feature-local and uses the SAME shared hardened call site.
 */
export { listOperators } from '@/shared/api/iam-operators-read';

// ---------------------------------------------------------------------------
// 0. grantable-roles — GET /api/admin/operators/grantable-roles
//    (feat/iam-grantable-roles-filter). READ; requires `operator.manage`.
//    Returns the seed role names the CALLING operator may grant — drives the
//    create / edit-roles role-checkbox pre-filter (types.ts § KNOWN_OPERATOR_
//    ROLES doc). No mutation headers. Throws `ApiError` /
//    `OperatorsUnavailableError` like every other fn here — the BFF proxy
//    route (`app/api/operators/grantable-roles/route.ts`) maps those via the
//    shared `mapError`. The SSR-page fail-graceful wrapper
//    (`getGrantableRolesOrNull`, `operators-self-api.ts`) catches on top of
//    this.
// ---------------------------------------------------------------------------

export async function getGrantableRoles(): Promise<string[]> {
  return callGapOperators(
    { method: 'GET', path: `${OPERATORS_PREFIX}/grantable-roles` },
    (json) => GrantableRolesResponseSchema.parse(json).roles,
  );
}

// ---------------------------------------------------------------------------
// 1. list — GET /api/admin/operators (status filter + pagination; READ)
//    No mutation headers (per the matrix). `listOperators` itself is defined
//    in `shared/api/iam-operators-read.ts` (TASK-PC-FE-259 promotion — three
//    consuming features) and re-exported from the top of this module, so this
//    section header still documents the read next to its siblings.
// ---------------------------------------------------------------------------

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
