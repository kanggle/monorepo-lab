import { NextResponse } from 'next/server';
import { listPurchaseOrders } from '@/features/scm-ops/api/scm-api';
import type { PoQueryParams } from '@/features/scm-ops/api/types';
import { mapScmError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm procurement-PO list read proxy for client components
 * (the typed API client's single backend entry point — no browser-direct
 * scm call, architecture.md § Forbidden Dependencies / contract § 2.3).
 * The HttpOnly **GAP OIDC access token** is attached server-side in
 * `listPurchaseOrders()` (NOT the GAP operator token — § 2.4.6 reusing
 * the § 2.4.5 per-domain credential rule). READ-ONLY: GET only, no
 * mutation branch, no Idempotency-Key, no X-Operator-Reason, no PO write.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: PoQueryParams = {
    status: sp.get('status') ?? undefined,
    supplierId: sp.get('supplierId') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listPurchaseOrders(params);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapScmError(err, requestId);
  }
}
