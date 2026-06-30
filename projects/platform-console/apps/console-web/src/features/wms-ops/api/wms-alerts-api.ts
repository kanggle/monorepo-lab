import {
  AlertPageSchema,
  type AlertPage,
  type AlertQueryParams,
  AckResultSchema,
  type AckResult,
} from './types';
import { callWmsAdmin, pageParams, type WmsResult } from './wms-client';

// ---------------------------------------------------------------------------
// 1.6 alerts — GET /dashboard/alerts
// ---------------------------------------------------------------------------

export function listAlerts(
  params: AlertQueryParams = {},
): Promise<WmsResult<AlertPage>> {
  const qs = new URLSearchParams();
  if (params.alertType) qs.set('alertType', params.alertType);
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.acknowledged !== undefined) {
    qs.set('acknowledged', String(params.acknowledged));
  }
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/alerts?${qs.toString()}` },
    (json) => AlertPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.6 alert acknowledge — POST /dashboard/alerts/{alertId}/acknowledge
//     THE ONLY mutation. Idempotency-Key (caller-supplied, stable per
//     confirmed action) + EMPTY body. NO X-Operator-Reason (wms surface
//     does not define it — confirm-gated in the UI instead).
// ---------------------------------------------------------------------------

export function acknowledgeAlert(
  alertId: string,
  idempotencyKey: string,
): Promise<WmsResult<AckResult>> {
  return callWmsAdmin(
    {
      method: 'POST',
      path: `/dashboard/alerts/${encodeURIComponent(alertId)}/acknowledge`,
      idempotencyKey,
      // EMPTY body per admin-service-api.md § 1.6 (the producer sets
      // acknowledged_at = now(), acknowledged_by = X-Actor-Id).
      body: undefined,
    },
    (json) => AckResultSchema.parse(json),
  );
}
