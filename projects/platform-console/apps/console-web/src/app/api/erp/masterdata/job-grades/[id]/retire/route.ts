import { NextResponse } from 'next/server';
import { retireJobGrade } from '@/features/erp-ops/api/erp-api';
import { ErpRetireBodySchema } from '@/features/erp-ops/api/types';
import { mapErpError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp job-grade RETIRE proxy (POST — TASK-PC-FE-048). `reason`
 * (≤256, required) rides in the BODY. `Idempotency-Key` required. A
 * `409 MASTERDATA_REFERENCE_VIOLATION` (active employee revisions still
 * reference this grade) passes through inline-actionably.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  let body: ReturnType<typeof ErpRetireBodySchema.parse>;
  try {
    body = ErpRetireBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid retire body' },
      { status: 400 },
    );
  }
  try {
    const result = await retireJobGrade(id, body.reason, body.idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
