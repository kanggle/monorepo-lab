import { NextResponse } from 'next/server';
import { closePeriod } from '@/features/ecommerce-ops/api/settlements-api';
import {
  mapEcommerceError,
  newRequestId,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce settlement period CLOSE proxy (TASK-PC-FE-221 Phase B):
 *   POST /api/ecommerce/settlements/periods/{id}/close → 200 close result
 *
 * Bodyless POST (NO Content-Type — the producer takes no body); domain-facing
 * IAM OIDC token server-side; NO X-Tenant-Id; NO Idempotency-Key. Irreversible
 * OPEN → CLOSED. 409 PERIOD_ALREADY_CLOSED body is preserved as-is (no 500
 * 오변환) for inline surfacing; 404 SETTLEMENT_NOT_FOUND → passthrough.
 */
export async function POST(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await closePeriod(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
