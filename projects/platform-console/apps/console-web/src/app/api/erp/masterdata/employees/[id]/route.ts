import { NextResponse } from 'next/server';
import {
  getEmployeeById,
  updateEmployee,
} from '@/features/erp-ops/api/erp-api';
import { UpdateEmployeeBodySchema } from '@/features/erp-ops/api/types';
import {
  buildDetailParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp employee DETAIL read proxy (GET) + UPDATE proxy (POST →
 * upstream PATCH — TASK-PC-FE-048; the typed client has only get/post). E3
 * `?asOf=` threaded through on read. Confidential PII never logged. Update
 * carries an `Idempotency-Key` (no `X-Operator-Reason`).
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const detailParams = buildDetailParams(req);
    const result = await getEmployeeById(id, detailParams);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  let body: ReturnType<typeof UpdateEmployeeBodySchema.parse>;
  try {
    body = UpdateEmployeeBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid update-employee body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await updateEmployee(id, input, idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
