import { NextResponse } from 'next/server';
import { getOrder } from '@/features/ecommerce-ops/api/orders-api';
import { mapEcommerceError, newRequestId } from '../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **order detail** read proxy
 * (console-integration-contract § 2.4.10 #16): `GET /admin/orders/{id}`.
 *
 * Used by the `useOrder` client hook for refetch after a status mutation.
 * The domain-facing IAM OIDC token is attached server-side in `orders-api.ts`.
 * No tenant header — ecommerce resolves tenant from the JWT claim (§ 2.4.10).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getOrder(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
