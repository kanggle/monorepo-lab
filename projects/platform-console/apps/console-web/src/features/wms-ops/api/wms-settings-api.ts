import {
  SettingPageSchema,
  SettingSchema,
  type Setting,
  type SettingPage,
  type SettingQueryParams,
} from './types';
import { callWmsAdmin, pageParams, type WmsResult } from './wms-client';

/**
 * wms `admin-service` **settings** read client (admin-service-api.md § 5.1 /
 * § 5.2) — TASK-PC-FE-224, the dedicated `/wms/operations` screen's first
 * consumer of this producer surface. Read is `WMS_VIEWER` or higher (§ 5.1/
 * 5.2 Auth) — same role floor as every other wms-ops read. `callWmsAdmin`
 * (same `WMS_ADMIN_BASE_URL` core as `wms-refs-api.ts` / `wms-api.ts`) — NO
 * new env var. Settings **write** (`PUT /settings/{key}`, § 5.3) is
 * `WMS_ADMIN`-gated + out of this task's READ-ONLY scope — not implemented
 * here (task § Out of Scope).
 */

// ---------------------------------------------------------------------------
// 5.1 settings — GET /settings
// ---------------------------------------------------------------------------

export function listSettings(
  params: SettingQueryParams = {},
): Promise<WmsResult<SettingPage>> {
  const qs = new URLSearchParams();
  if (params.keyPrefix) qs.set('keyPrefix', params.keyPrefix);
  if (params.scope) qs.set('scope', params.scope);
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/settings?${qs.toString()}` },
    (json) => SettingPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 5.2 setting — GET /settings/{key}
// ---------------------------------------------------------------------------

/**
 * `warehouseId` is required by the producer only for `WAREHOUSE`-scoped
 * keys (§ 5.2) — omitted for `GLOBAL` (both operational keys this task
 * surfaces are `GLOBAL`). Exported for parity with `wms-refs-api.ts`'s
 * single-row read shape; the `/wms/operations` screen itself seeds via
 * {@link listSettings} (one call, not per-key).
 */
export function getSetting(
  key: string,
  warehouseId?: string,
): Promise<WmsResult<Setting>> {
  const qs = new URLSearchParams();
  if (warehouseId) qs.set('warehouseId', warehouseId);
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  return callWmsAdmin(
    {
      method: 'GET',
      path: `/settings/${encodeURIComponent(key)}${suffix}`,
    },
    (json) => SettingSchema.parse(json),
  );
}
