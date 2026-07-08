import { NextResponse } from 'next/server';
import { listPeriods } from '@/features/ecommerce-ops/api/settlements-api';
import {
  mapEcommerceError,
  newRequestId,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce settlement periods proxy (TASK-PC-FE-221 Phase A):
 *   GET /api/ecommerce/settlements/periods?page=&size= → periods list
 *
 * Domain-facing IAM OIDC token server-side; NO X-Tenant-Id; NO Idempotency-Key.
 * Targets ECOMMERCE_ADMIN_BASE_URL. READ-ONLY — period open/close is Phase B.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const page = searchParams.has('page')
    ? Number(searchParams.get('page'))
    : undefined;
  const size = searchParams.has('size')
    ? Number(searchParams.get('size'))
    : undefined;

  try {
    const result = await listPeriods({ page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
