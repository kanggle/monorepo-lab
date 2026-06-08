import { NextResponse } from 'next/server';
import { listOrders } from '@/features/wms-outbound-ops/api/outbound-api';
import type { OutboundListParams } from '@/features/wms-outbound-ops/api/types';
import { mapOutboundError, newRequestId } from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms outbound order-list read proxy for client components (the
 * typed API client's single backend entry point — no browser-direct wms
 * call, architecture.md § Forbidden Dependencies / contract § 2.3). The
 * HttpOnly **domain-facing IAM OIDC access token** is attached server-side in
 * `listOrders()` (NOT the IAM operator token — § 2.4.5.1 per-domain
 * credential divergence). READ-ONLY: GET only, no Idempotency-Key, no
 * X-Operator-Reason, no body.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: OutboundListParams = {
    status: sp.get('status') ?? undefined,
    warehouseId: sp.get('warehouseId') ?? undefined,
    orderNo: sp.get('orderNo') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const orders = await listOrders(params);
    return NextResponse.json(orders);
  } catch (err) {
    return mapOutboundError(err, requestId);
  }
}
