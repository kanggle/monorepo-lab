import { NextResponse } from 'next/server';
import {
  listCostCenters,
  createCostCenter,
} from '@/features/erp-ops/api/erp-api';
import { CreateCostCenterBodySchema } from '@/features/erp-ops/api/types';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp cost-centers LIST read proxy (GET) + CREATE proxy (POST —
 * TASK-PC-FE-048). E3 `?asOf=` threaded through on read. Create carries an
 * `Idempotency-Key`; `departmentId` is an optional FK (the producer rejects
 * an unknown one with 404).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    const result = await listCostCenters(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body: ReturnType<typeof CreateCostCenterBodySchema.parse>;
  try {
    body = CreateCostCenterBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid create-cost-center body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await createCostCenter(input, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
