import { NextResponse } from 'next/server';
import { getCommissionRate } from '@/features/ecommerce-ops/api/settlements-api';
import {
  mapEcommerceError,
  newRequestId,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce commission-rate proxy (TASK-PC-FE-221 Phase A):
 *   GET /api/ecommerce/settlements/commission-rates/{id} → effective rate + source
 *
 * Domain-facing IAM OIDC token server-side; NO X-Tenant-Id; NO Idempotency-Key.
 * READ-ONLY — the rate PUT is Phase B. 404 SETTLEMENT_NOT_FOUND → passthrough.
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
