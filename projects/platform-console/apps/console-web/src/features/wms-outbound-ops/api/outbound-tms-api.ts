import { getServerEnv } from '@/shared/config/env';
import {
  AdminShipmentRefPageSchema,
  TmsRetryResultSchema,
  type TmsRetryResult,
} from './types';
import { callOutbound } from './outbound-client';

/**
 * TMS domain — wms outbound-service TMS retry operations
 * (TASK-PC-FE-147 sub-module; behavior-preserving split of `outbound-api.ts`).
 *
 * Covers: § 4.3 retryTmsNotify (OUTBOUND_ADMIN) and the admin read-model
 * resolver used to look up the shipmentId for an order
 * (TASK-PC-FE-087, console-integration-contract § 2.4.5 / § 2.4.5.1).
 *
 * External code must import from `outbound-api.ts` (the barrel), not directly
 * from this file.
 */

// ===========================================================================
// TMS RETRY (OUTBOUND_ADMIN — reason-free; TASK-PC-FE-087, op 10)
// ===========================================================================

/**
 * Resolves the `shipmentId` for an order from the wms admin read-model
 * (admin-service-api.md § 1.3 `GET /api/v1/admin/dashboard/shipments?orderId`).
 *
 * The TMS-retry producer endpoint (§ 4.3) is shipment-keyed, but the
 * outbound order-centric reads carry no `shipmentId` (§ 1.2 order detail =
 * create-response shape; there is NO `GET /orders/{id}/shipments`). So the id
 * is read from the admin projection (the `orderId` filter is contracted). This
 * hits `WMS_ADMIN_BASE_URL` with the SAME IAM-OIDC domain-facing credential —
 * same wms gateway, distinct `/api/v1/admin` path prefix (§ 2.4.5 / § 2.4.5.1).
 * `503`/timeout/network still map to `WmsOutboundUnavailableError` (the
 * outbound section degrade), NOT a crash. Returns `null` when the order has no
 * projected shipment (→ the proxy maps that to a `404 SHIPMENT_NOT_FOUND`).
 */
export async function resolveShipmentIdForOrder(
  orderId: string,
): Promise<string | null> {
  const env = getServerEnv();
  const page = await callOutbound(
    {
      method: 'GET',
      path: `/dashboard/shipments?orderId=${encodeURIComponent(orderId)}&size=1`,
      baseUrl: env.WMS_ADMIN_BASE_URL,
      timeoutMs: env.WMS_TIMEOUT_MS,
    },
    (j) => AdminShipmentRefPageSchema.parse(j),
  );
  return page.content[0]?.shipmentId ?? null;
}

/**
 * 4.3 — POST /shipments/{id}:retry-tms-notify (manual TMS retry).
 * TASK-PC-FE-087, console-integration-contract § 2.4.5.1 op 10.
 *
 * Reason-free (re-notifies the carrier only; stock already consumed — UNLIKE
 * the reason-required cancel). Empty `{}` body + `Idempotency-Key`. Role is
 * producer-enforced (`OUTBOUND_ADMIN`) — a 403 maps inline; the console never
 * pre-gates. Note the `:retry-tms-notify` action suffix on the path.
 */
export function retryTmsNotify(
  shipmentId: string,
  idempotencyKey: string,
): Promise<TmsRetryResult> {
  return callOutbound(
    {
      method: 'POST',
      path: `/shipments/${encodeURIComponent(shipmentId)}:retry-tms-notify`,
      idempotencyKey,
      body: {},
    },
    (j) => TmsRetryResultSchema.parse(j),
  );
}
