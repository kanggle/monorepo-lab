import { NextResponse } from 'next/server';
import { getInventoryByKey } from '@/features/wms-ops/api/wms-api';
import { mapWmsError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms inventory **by-key** detail read proxy for client
 * components (TASK-PC-FE-172 — the `/wms/inventory` row "상세" panel; the
 * typed API client's single backend entry point — no browser-direct wms
 * call, architecture.md § Forbidden Dependencies / contract § 2.3). The
 * HttpOnly **IAM OIDC access token** is attached server-side in
 * `getInventoryByKey()` (NOT the IAM operator token — § 2.4.5 per-domain
 * credential divergence). READ-ONLY: GET only, no mutation branch, no
 * Idempotency-Key, no X-Operator-Reason.
 *
 * The composite key is location+sku+lot (no single `[id]`) — `locationId`
 * and `skuId` are required, `lotId` is optional. A `404` (zero stock at
 * that key) is passed through by `mapWmsError` as an inline `ApiError`
 * (NOT a degrade) — the caller (`useWmsInventoryByKey`) distinguishes it as
 * "재고 없음".
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const locationId = sp.get('locationId');
  const skuId = sp.get('skuId');
  const lotId = sp.get('lotId') ?? undefined;

  if (!locationId || !skuId) {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'locationId and skuId are required' },
      { status: 422 },
    );
  }

  try {
    const result = await getInventoryByKey({ locationId, skuId, lotId });
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
