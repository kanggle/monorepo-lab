import { NextResponse } from 'next/server';
import {
  getBusinessPartnerById,
  updateBusinessPartner,
} from '@/features/erp-ops/api/erp-api';
import { UpdateBusinessPartnerBodySchema } from '@/features/erp-ops/api/types';
import {
  buildDetailParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp business-partner DETAIL read proxy (GET) + UPDATE proxy
 * (POST → upstream PATCH — TASK-PC-FE-048). Confidential financial details
 * never logged. Update carries an `Idempotency-Key`.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const detailParams = buildDetailParams(req);
    const result = await getBusinessPartnerById(id, detailParams);
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
  let body: ReturnType<typeof UpdateBusinessPartnerBodySchema.parse>;
  try {
    body = UpdateBusinessPartnerBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message: 'invalid update-business-partner body',
      },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await updateBusinessPartner(id, input, idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
