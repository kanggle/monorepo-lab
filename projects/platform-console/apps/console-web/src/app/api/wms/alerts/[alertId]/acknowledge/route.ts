import { NextResponse } from 'next/server';
import { acknowledgeAlert } from '@/features/wms-ops/api/wms-api';
import {
  AckBodySchema,
  mapWmsError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms alert-acknowledge mutation proxy — THE ONLY mutation in
 * the wms-ops slice (console-integration-contract § 2.4.5). The client
 * supplies a stable `idempotencyKey` per the confirmed action (the
 * confirm-dialog generates it via `crypto.randomUUID()`); the **GAP OIDC
 * access token** is attached server-side in `acknowledgeAlert()` (NOT the
 * GAP operator token — wms requires the GAP OIDC token; the #569 invariant
 * is GAP-domain-scoped).
 *
 * Reason-free: wms does NOT define `X-Operator-Reason` on this surface —
 * the body is ONLY the idempotency key (no reason captured / sent;
 * carrying GAP's § 2.4.1 reason header over is a header-matrix-drift
 * defect). The action is confirm-gated in the UI instead.
 *
 * `STATE_TRANSITION_INVALID` (422 — alert already acknowledged) /
 * `409 DUPLICATE_REQUEST` are mapped to inline actionable (no crash).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ alertId: string }> },
) {
  const requestId = newRequestId();
  const { alertId } = await params;
  let body;
  try {
    body = AckBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await acknowledgeAlert(alertId, body.idempotencyKey);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
