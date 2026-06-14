import { NextResponse } from 'next/server';
import {
  getOrder,
  cancelOrder,
} from '@/features/wms-outbound-ops/api/outbound-api';
import {
  CancelBodySchema,
  mapOutboundError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms outbound **Cancel** mutation proxy (TASK-PC-FE-085 —
 * console-integration-contract § 2.4.5.1 op 9):
 *
 *   1. `GET /orders/{id}` → the current order `version` (optimistic lock).
 *   2. `POST /orders/{id}:cancel` (§ 1.4) `{ reason, version }` → order
 *      `CANCELLED` (saga `CANCELLATION_REQUESTED` → eventual `CANCELLED` once
 *      `inventory.released` is consumed — async).
 *
 * Diverges from the forward (pick/pack/ship) proxies in TWO ways the producer
 * § 1.4 actually requires (do NOT cargo-cult the reason-free `ActionBody`):
 *   - the body carries a **REQUIRED `reason`** (3..500) — it rides in the
 *     producer JSON body, NOT a header (the wms surface still has no
 *     `X-Operator-Reason`).
 *   - the producer enforces **role escalation** (`OUTBOUND_WRITE` for PICKING,
 *     `OUTBOUND_ADMIN` for post-pick) — the console does NOT pre-gate on role;
 *     a `403 FORBIDDEN` is mapped to an inline actionable state.
 *
 * On `409 CONFLICT` (stale version) the error is surfaced inline → the UI
 * refetches + prompts retry (no silent auto-retry). The domain-facing IAM OIDC
 * token is attached server-side (NOT the operator token — § 2.4.5.1).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ orderId: string }> },
) {
  const requestId = newRequestId();
  const { orderId } = await params;

  let body;
  try {
    body = CancelBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }

  try {
    const order = await getOrder(orderId);
    const result = await cancelOrder(
      orderId,
      order.version,
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapOutboundError(err, requestId);
  }
}
