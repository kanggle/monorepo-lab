import { NextResponse } from 'next/server';
import {
  getCostCenterById,
  updateCostCenter,
} from '@/features/erp-ops/api/erp-api';
import { UpdateCostCenterBodySchema } from '@/features/erp-ops/api/types';
import {
  buildDetailParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp cost-center DETAIL read proxy (GET) + UPDATE proxy (POST →
 * upstream PATCH — TASK-PC-FE-048). E3 `?asOf=` threaded through on read.
 * Update carries an `Idempotency-Key`.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const detailParams = buildDetailParams(req);
    const result = await getCostCenterById(id, detailParams);
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
  let body: ReturnType<typeof UpdateCostCenterBodySchema.parse>;
  try {
    body = UpdateCostCenterBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid update-cost-center body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await updateCostCenter(id, input, idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
