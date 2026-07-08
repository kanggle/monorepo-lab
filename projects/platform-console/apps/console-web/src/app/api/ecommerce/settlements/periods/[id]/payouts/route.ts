import { NextResponse } from 'next/server';
import { listPayouts } from '@/features/ecommerce-ops/api/settlements-api';
import {
  mapEcommerceError,
  newRequestId,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce period payouts proxy (TASK-PC-FE-221 Phase A):
 *   GET /api/ecommerce/settlements/periods/{id}/payouts?page=&size= → payouts list
 *
 * Domain-facing IAM OIDC token server-side; NO X-Tenant-Id; NO Idempotency-Key.
 * READ-ONLY — payout execute is Phase B. 404 SETTLEMENT_NOT_FOUND → passthrough.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  const { searchParams } = new URL(req.url);
  const page = searchParams.has('page')
    ? Number(searchParams.get('page'))
    : undefined;
  const size = searchParams.has('size')
    ? Number(searchParams.get('size'))
    : undefined;

  try {
    const result = await listPayouts(id, { page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
