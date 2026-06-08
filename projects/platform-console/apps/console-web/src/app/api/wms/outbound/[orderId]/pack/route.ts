import { NextResponse } from 'next/server';
import {
  getOrder,
  createPackingUnit,
  sealPackingUnit,
  type PackingUnitLine,
} from '@/features/wms-outbound-ops/api/outbound-api';
import {
  ActionBodySchema,
  mapOutboundError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms outbound **Pack** mutation proxy (console-integration-
 * contract § 2.4.5.1). Packing has NO bulk "/packing/confirm" endpoint — it
 * is TWO producer calls, each with its OWN stable `Idempotency-Key`:
 *
 *   1. `GET /orders/{id}` → the order lines (qty = ordered qty).
 *   2. `POST /orders/{id}/packing-units` (§3.1) — all order lines,
 *      `cartonNo = "BOX-" + <short>`, `packingType = "BOX"` → read
 *      `packingUnitId` + `version` from the 201 response.
 *   3. `PATCH /packing-units/{packingUnitId}` (§3.2) `seal:true` using that
 *      `version` → order PACKED.
 *
 * The client supplies ONE stable key for the confirmed action; the two
 * upstream calls derive their OWN keys from it (`<key>:create` / `<key>:seal`)
 * so each is independently idempotent (a replayed confirmed Pack reuses both;
 * a fresh attempt regenerates the client key → new derived keys).
 *
 * On `409 CONFLICT` (someone advanced the unit/order) the error is surfaced
 * inline → refetch + retry-prompt in the UI (no silent auto-retry). The
 * domain-facing IAM OIDC token is attached server-side (NOT the operator
 * token); no `X-Operator-Reason`.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ orderId: string }> },
) {
  const requestId = newRequestId();
  const { orderId } = await params;

  let body;
  try {
    body = ActionBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }

  try {
    const order = await getOrder(orderId);
    const lines: PackingUnitLine[] = order.lines.map((l) => ({
      orderLineId: l.orderLineId,
      skuId: l.skuId,
      lotId: l.lotId ?? null,
      qty: l.qtyOrdered,
    }));

    const short = body.idempotencyKey.slice(0, 8);
    // Create the packing unit (its own idempotency key).
    const unit = await createPackingUnit(
      orderId,
      `BOX-${short}`,
      lines,
      `${body.idempotencyKey}:create`,
    );

    // Seal it with the create-response version (its own idempotency key).
    const sealed = await sealPackingUnit(
      unit.packingUnitId,
      unit.version,
      `${body.idempotencyKey}:seal`,
    );
    return NextResponse.json(sealed);
  } catch (err) {
    return mapOutboundError(err, requestId);
  }
}
