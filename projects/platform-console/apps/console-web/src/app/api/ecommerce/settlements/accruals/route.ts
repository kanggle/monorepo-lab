import { NextResponse } from 'next/server';
import { listAccruals } from '@/features/ecommerce-ops/api/settlements-api';
import {
  mapEcommerceError,
  newRequestId,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce settlement accruals proxy (TASK-PC-FE-221 Phase A):
 *   GET /api/ecommerce/settlements/accruals?sellerId=&orderId=&page=&size= → list
 *
 * Domain-facing IAM OIDC token attached server-side (NOT the operator token —
 * § 2.4.10); NO X-Tenant-Id; NO Idempotency-Key. Targets ECOMMERCE_ADMIN_BASE_URL
 * (settlements live under /api/admin/settlements — admin subtree). READ-ONLY.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const sellerId = searchParams.get('sellerId') ?? undefined;
  const orderId = searchParams.get('orderId') ?? undefined;
  const page = searchParams.has('page')
    ? Number(searchParams.get('page'))
    : undefined;
  const size = searchParams.has('size')
    ? Number(searchParams.get('size'))
    : undefined;

  try {
    const result = await listAccruals({ sellerId, orderId, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
