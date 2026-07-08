import { NextResponse } from 'next/server';
import { getSellerBalance } from '@/features/ecommerce-ops/api/settlements-api';
import {
  mapEcommerceError,
  newRequestId,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce seller settlement balance proxy (TASK-PC-FE-221 Phase A):
 *   GET /api/ecommerce/settlements/sellers/{id}/balance → seller balance rollup
 *
 * Domain-facing IAM OIDC token server-side; NO X-Tenant-Id; NO Idempotency-Key.
 * READ-ONLY. 404 SETTLEMENT_NOT_FOUND (cross-tenant / cross-seller) → passthrough.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getSellerBalance(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
