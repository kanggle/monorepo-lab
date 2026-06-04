import { NextResponse } from 'next/server';
import { retireBusinessPartner } from '@/features/erp-ops/api/erp-api';
import { ErpRetireBodySchema } from '@/features/erp-ops/api/types';
import { mapErpError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp business-partner RETIRE proxy (POST — TASK-PC-FE-048).
 * `reason` (≤256, required) rides in the BODY. `Idempotency-Key` required.
 * BusinessPartner does not emit `MASTERDATA_REFERENCE_VIOLATION` in v1
 * (cross-domain references are read-only per E5).
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
    const result = await retireBusinessPartner(
      id,
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
