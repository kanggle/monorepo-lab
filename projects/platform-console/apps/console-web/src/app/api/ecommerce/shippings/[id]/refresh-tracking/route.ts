import { NextResponse } from 'next/server';
import { refreshTracking } from '@/features/ecommerce-ops/api/shippings-api';
import {
  mapEcommerceError,
  newRequestId,
} from '../../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **refresh-tracking** mutation proxy
 * (TASK-PC-FE-088 — console-integration-contract § 2.4.10.3):
 * `POST /api/ecommerce/shippings/{id}/refresh-tracking`
 *
 * Empty body. Best-effort: returns 200 with the (possibly unchanged) shipment.
 * When the carrier mode is mock or the carrier is unreachable, the producer
 * returns 200 with unchanged status (no error surfaced — best-effort per spec).
 *
 * Domain-facing IAM OIDC token attached server-side; NO X-Tenant-Id;
 * NO Idempotency-Key.
 */
export async function POST(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  try {
    const result = await refreshTracking(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
