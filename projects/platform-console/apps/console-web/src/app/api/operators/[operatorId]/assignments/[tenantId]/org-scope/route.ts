import { NextResponse } from 'next/server';
import { setOperatorOrgScope } from '@/features/operators/api/operators-api';
import {
  SetOrgScopeBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin set-org-scope proxy (TASK-PC-FE-050 / TASK-BE-339). PUT only —
 * `PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope`.
 * Sets / clears the (operator, tenant) assignment's `org_scope` (부서
 * subtree-root id 배열). The client supplies the tri-state `orgScope`
 * (`null` clear / `[]` 차단 / `[ids]` subtree) + an operator-entered
 * `reason`; the operator token + active tenant + `X-Operator-Reason` are
 * attached server-side by the api layer.
 *
 * PER-ENDPOINT HEADER MATRIX: `X-Operator-Reason` ONLY — NO `Idempotency-Key`
 * (mirror /roles + /status; idempotent full-replace PUT). The producer
 * rejects an empty reason (`400 REASON_REQUIRED`) and a tenantId mismatch
 * (`403 TENANT_SCOPE_MISMATCH` — `path tenantId != X-Tenant-Id`); both map
 * inline (no crash).
 *
 * PUT-ONLY: no GET/POST/PATCH/DELETE handler is exported — Next.js returns
 * 405 for any other method (a test pins the absence + the 405). The
 * server-only credential never reaches client JS (the api layer reads the
 * HttpOnly operator token via `getOperatorToken`).
 */
export async function PUT(
  req: Request,
  { params }: { params: Promise<{ operatorId: string; tenantId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId, tenantId } = await params;
  let body;
  try {
    body = SetOrgScopeBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await setOperatorOrgScope(
      operatorId,
      tenantId,
      { orgScope: body.orgScope },
      body.reason,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
