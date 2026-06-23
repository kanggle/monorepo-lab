import { NextResponse } from 'next/server';
import { listOrders } from '@/features/ecommerce-ops/api/orders-api';
import { mapEcommerceError, newRequestId } from '../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **order list** read proxy
 * (console-integration-contract § 2.4.10 #15): `GET /admin/orders?status&page&size`.
 *
 * Used by the `useOrders` client hook for re-query on status-filter /
 * pagination (the server-rendered first page is seeded as `initialData`; only
 * a filter/page change reaches this handler). The domain-facing IAM OIDC token
 * is attached server-side in `orders-api.ts` — NOT the operator token
 * (§ 2.4.10); NO X-Tenant-Id (ecommerce resolves the tenant from the JWT
 * claim). 503/timeout/network → 503 (only this section degrades).
 *
 * READ-ONLY: NO POST/PATCH/DELETE here — the status mutation lives at the
 * `[id]/status` sub-route.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const status = searchParams.get('status') ?? undefined;
  const page = searchParams.has('page')
    ? Number(searchParams.get('page'))
    : undefined;
  const size = searchParams.has('size')
    ? Number(searchParams.get('size'))
    : undefined;

  try {
    const result = await listOrders({ status, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
