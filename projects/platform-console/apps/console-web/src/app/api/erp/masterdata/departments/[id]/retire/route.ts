import { NextResponse } from 'next/server';
import { retireDepartment } from '@/features/erp-ops/api/erp-api';
import { RetireDepartmentBodySchema } from '@/features/erp-ops/api/types';
import { mapErpError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp department RETIRE proxy (write PILOT — TASK-PC-FE-046
 * / § 2.4.8). Forwards to the UNCHANGED producer
 * `POST /api/erp/masterdata/departments/{id}/retire` via
 * `retireDepartment()`. Retire IS a producer reason-slot endpoint —
 * the `reason` (≤256, required) rides in the BODY (NOT an
 * `X-Operator-Reason` header — erp does not read it). `Idempotency-Key`
 * required. A `409 MASTERDATA_REFERENCE_VIOLATION` (live referents) /
 * `404` / `403 PERMISSION_DENIED` passes through inline-actionably.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  let body: ReturnType<typeof RetireDepartmentBodySchema.parse>;
  try {
    body = RetireDepartmentBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid retire-department body' },
      { status: 400 },
    );
  }
  try {
    const result = await retireDepartment(
      id,
      { reason: body.reason },
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
