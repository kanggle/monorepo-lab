import { NextResponse } from 'next/server';
import { getPurchaseOrder } from '@/features/scm-ops/api/scm-api';
import { mapScmError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm PO-detail read proxy (read-only — GET). The HttpOnly
 * IAM OIDC access token is attached server-side in `getPurchaseOrder()`
 * (§ 2.4.6 reusing the § 2.4.5 per-domain credential rule — NOT the
 * operator token). No mutation artifacts. PO write (`/submit|/confirm|
 * /cancel`) is explicitly out of scope — there is no such proxy route.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ poId: string }> },
) {
  const requestId = newRequestId();
  const { poId } = await params;
  try {
    const result = await getPurchaseOrder(poId);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapScmError(err, requestId);
  }
}
