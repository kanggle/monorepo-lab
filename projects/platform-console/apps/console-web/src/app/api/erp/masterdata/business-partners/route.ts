import { NextResponse } from 'next/server';
import {
  listBusinessPartners,
  createBusinessPartner,
} from '@/features/erp-ops/api/erp-api';
import { CreateBusinessPartnerBodySchema } from '@/features/erp-ops/api/types';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp business-partners LIST read proxy (GET) + CREATE proxy
 * (POST — TASK-PC-FE-048). Confidential financial details (`paymentTerms`)
 * never logged. Create carries an `Idempotency-Key`; `partnerType` required.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    const result = await listBusinessPartners(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body: ReturnType<typeof CreateBusinessPartnerBodySchema.parse>;
  try {
    body = CreateBusinessPartnerBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message: 'invalid create-business-partner body',
      },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await createBusinessPartner(input, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
