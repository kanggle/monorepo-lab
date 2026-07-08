import { NextResponse } from 'next/server';
import {
  getCommissionRate,
  setCommissionRate,
} from '@/features/ecommerce-ops/api/settlements-api';
import { SetCommissionRateBodySchema } from '@/features/ecommerce-ops/api/settlement-types';
import {
  mapEcommerceError,
  newRequestId,
  badRequest,
  tryParse,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce commission-rate proxy:
 *   GET /api/ecommerce/settlements/commission-rates/{id} → rate + source (Phase A)
 *   PUT /api/ecommerce/settlements/commission-rates/{id} → set rate (Phase B)
 *
 * Domain-facing IAM OIDC token server-side; NO X-Tenant-Id; NO Idempotency-Key.
 * PUT body is Zod SHAPE-validated (defence-in-depth) so a malformed body never
 * reaches the upstream; the `[0,10000]` range gate stays producer-authoritative
 * (422 COMMISSION_RATE_INVALID passthrough). 4xx bodies are preserved as-is (no
 * 500 오변환). 404 SETTLEMENT_NOT_FOUND → passthrough.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getCommissionRate(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

export async function PUT(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(SetCommissionRateBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await setCommissionRate(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
