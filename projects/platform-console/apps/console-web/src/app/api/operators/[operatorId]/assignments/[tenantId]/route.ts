import { NextResponse } from 'next/server';
import {
  assignOperatorToTenant,
  unassignOperatorFromTenant,
} from '@/features/operators/api/operators-api';
import {
  AssignmentReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin operator↔tenant assignment proxy (TASK-PC-FE-157 / TASK-BE-347,
 * ADR-MONO-024 D3-i). Sibling of the PUT-only `.../{tenantId}/org-scope`
 * route — this one owns the assignment ROW lifecycle:
 *
 *   - POST   → create the (operator, tenant) `operator_tenant_assignment`
 *              row ("내 직원에게 내 테넌트 접근 부여"). 201 + created row.
 *   - DELETE → remove the row. 204 no content.
 *
 * PER-ENDPOINT HEADER MATRIX: `X-Operator-Reason` ONLY (required) — NO
 * `Idempotency-Key` (the (operator, tenant) PK is the natural dedupe; a
 * re-create is `409 ASSIGNMENT_ALREADY_EXISTS`). The client supplies the
 * `reason` in the body; the operator token + active tenant + the
 * `X-Operator-Reason` header are attached server-side by the api layer.
 *
 * Tenant confinement is PRODUCER-side (`TenantScopeGuard` on the path
 * `tenantId` — a `TENANT_ADMIN @ acme` may assign to acme only; SUPER_ADMIN
 * `'*'` net-zero). Producer errors (403 PERMISSION_DENIED / 403
 * TENANT_SCOPE_DENIED / 404 OPERATOR_NOT_FOUND / 404 ASSIGNMENT_NOT_FOUND /
 * 409 ASSIGNMENT_ALREADY_EXISTS / 400 REASON_REQUIRED) map inline; 401 →
 * re-login; 503/timeout → section degrade. The server-only credential never
 * reaches client JS (the api layer reads the HttpOnly operator token).
 *
 * POST/DELETE only: no GET/PUT/PATCH handler is exported — Next.js returns
 * 405 for any other method.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ operatorId: string; tenantId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId, tenantId } = await params;
  let body;
  try {
    body = AssignmentReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await assignOperatorToTenant(
      operatorId,
      tenantId,
      body.reason,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ operatorId: string; tenantId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId, tenantId } = await params;
  let body;
  try {
    body = AssignmentReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await unassignOperatorFromTenant(operatorId, tenantId, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
