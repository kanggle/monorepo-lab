import { NextResponse } from 'next/server';
import { getAsnInspection } from '@/features/wms-ops/api/wms-api';
import { mapWmsError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms ASN **검수(inspection)** detail read proxy for client
 * components (TASK-PC-FE-222 — the `/wms/inbound` row "검수" panel; the
 * typed API client's single backend entry point — no browser-direct wms
 * call, architecture.md § Forbidden Dependencies / contract § 2.3). The
 * HttpOnly **IAM OIDC access token** is attached server-side in
 * `getAsnInspection()` (NOT the IAM operator token — § 2.4.5 per-domain
 * credential divergence). READ-ONLY: GET only, no mutation branch, no
 * Idempotency-Key, no X-Operator-Reason.
 *
 * A `404` (no inspection projected yet — `inbound.inspection.completed` has
 * not fired for this ASN) is passed through by `mapWmsError` as an inline
 * `ApiError` (NOT a degrade — Failure Scenario guard) — the caller
 * (`useWmsAsnInspection`) distinguishes it as "검수 내역 없음".
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ asnId: string }> },
) {
  const requestId = newRequestId();
  const { asnId } = await params;
  try {
    const result = await getAsnInspection(asnId);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
