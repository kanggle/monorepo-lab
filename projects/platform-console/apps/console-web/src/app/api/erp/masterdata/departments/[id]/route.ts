import { NextResponse } from 'next/server';
import {
  getDepartmentById,
  updateDepartment,
} from '@/features/erp-ops/api/erp-api';
import { UpdateDepartmentBodySchema } from '@/features/erp-ops/api/types';
import {
  buildDetailParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp department DETAIL read proxy (read-only — GET).
 * E3 `?asOf=` threaded through verbatim. IAM OIDC access token
 * attached server-side. No mutation artifacts.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const detailParams = buildDetailParams(req);
    const result = await getDepartmentById(id, detailParams);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

/**
 * Same-origin erp department UPDATE proxy (write PILOT — TASK-PC-FE-046
 * / § 2.4.8). The typed client has only get/post; this same-origin
 * **POST** forwards to the UNCHANGED producer **PATCH**
 * `/api/erp/masterdata/departments/{id}` via `updateDepartment()`
 * (the operators "POST proxy → PATCH upstream" precedent). Update has
 * no producer reason slot — NO `X-Operator-Reason`. `Idempotency-Key`
 * is required (E1 / T1); a `422 MASTERDATA_EFFECTIVE_PERIOD_INVALID` /
 * `409 CONCURRENT_MODIFICATION` / `404 MASTERDATA_NOT_FOUND` /
 * `403 PERMISSION_DENIED` passes through `mapErpError` inline.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  let body: ReturnType<typeof UpdateDepartmentBodySchema.parse>;
  try {
    body = UpdateDepartmentBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid update-department body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await updateDepartment(id, input, idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
