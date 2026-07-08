import { NextResponse } from 'next/server';
import { executePayouts } from '@/features/ecommerce-ops/api/settlements-api';
import {
  mapEcommerceError,
  newRequestId,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce period payout EXECUTE proxy (TASK-PC-FE-221 Phase B):
 *   POST /api/ecommerce/settlements/periods/{id}/payouts/execute → 200 Payout[]
 *
 * SIMULATED payout (no real disbursement). Bodyless POST (NO Content-Type);
 * domain-facing IAM OIDC token server-side; NO X-Tenant-Id; NO Idempotency-Key
 * (`(periodId, sellerId)` producer-idempotent). 409 PERIOD_NOT_CLOSED body is
 * preserved as-is (no 500 오변환) for inline surfacing; 404 SETTLEMENT_NOT_FOUND
 * → passthrough.
 */
export async function POST(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await executePayouts(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
