import { NextResponse } from 'next/server';
import {
  getOrder,
  getSaga,
} from '@/features/wms-outbound-ops/api/outbound-api';
import { mapOutboundError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms outbound order-drill read proxy: composes §1.2 order detail
 * (lines + status + version) + §5.1 saga state into a single `{ detail, saga }`
 * envelope server-side (one browser round-trip). READ-ONLY — GET only, no
 * mutation artifacts. The HttpOnly **domain-facing IAM OIDC access token** is
 * attached server-side in `getOrder()`/`getSaga()` (NOT the operator token —
 * § 2.4.5.1).
 *
 * Both legs are bounded by the same per-section timeout; a `401` on either →
 * whole-session re-login; a `403`/`404`/`503` is mapped by the shared error
 * mapper (the saga read is attempted only after the detail read resolves, so
 * a `404 ORDER_NOT_FOUND` surfaces inline before the saga call).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ orderId: string }> },
) {
  const requestId = newRequestId();
  const { orderId } = await params;
  try {
    const detail = await getOrder(orderId);
    const saga = await getSaga(orderId);
    return NextResponse.json({ detail, saga });
  } catch (err) {
    return mapOutboundError(err, requestId);
  }
}
