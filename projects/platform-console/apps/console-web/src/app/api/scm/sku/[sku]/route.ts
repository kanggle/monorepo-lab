import { NextResponse } from 'next/server';
import { getSkuBreakdown } from '@/features/scm-ops/api/scm-api';
import { mapScmError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm inventory-visibility per-SKU breakdown read proxy
 * (read-only — GET). The HttpOnly IAM OIDC access token is attached
 * server-side in `getSkuBreakdown()` (§ 2.4.6 reusing § 2.4.5 — NOT the
 * operator token). The full `{ data, meta }` envelope is forwarded so the
 * REQUIRED S5 `meta.warning` reaches the client and is rendered
 * prominently — NEVER stripped. The producer `X-Cache`
 * (HIT|MISS|UNAVAILABLE) is surfaced as a response header (honest
 * freshness — § 2.4.6).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ sku: string }> },
) {
  const requestId = newRequestId();
  const { sku } = await params;
  try {
    const result = await getSkuBreakdown(sku);
    const res = NextResponse.json(result.data);
    if (result.cache) res.headers.set('X-Cache', result.cache);
    return res;
  } catch (err) {
    return mapScmError(err, requestId);
  }
}
