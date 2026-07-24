import { getServerEnv } from '@/shared/config/env';
import { AdminShipmentRefPageSchema } from './types';
import { callOutbound } from './outbound-client';

/**
 * wms admin read-model shipment-id resolver (TASK-PC-FE-087 sub-module; renamed
 * from `outbound-tms-api.ts` by TASK-PC-FE-258 when the TMS side-channel was
 * dropped from the console action).
 *
 * Covers the admin read-model resolver used to look up the `shipmentId` for an
 * order — the wms half of the two-hop "발송 재시도" resolve (orderId →
 * shipmentId → dispatchId). The dispatch retry itself now targets
 * `logistics-service` on the scm gateway (see `outbound-logistics-api.ts`); this
 * module keeps ONLY the wms-admin shipment lookup, unchanged.
 *
 * External code must import from `outbound-api.ts` (the barrel), not directly
 * from this file.
 */

/**
 * Resolves the `shipmentId` for an order from the wms admin read-model
 * (admin-service-api.md § 1.3 `GET /api/v1/admin/dashboard/shipments?orderId`).
 *
 * The outbound order-centric reads carry no `shipmentId` (§ 1.2 order detail =
 * create-response shape; there is NO `GET /orders/{id}/shipments`), so the id
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
