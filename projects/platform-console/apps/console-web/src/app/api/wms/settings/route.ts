import { NextResponse } from 'next/server';
import { listSettings } from '@/features/wms-ops/api/wms-settings-api';
import type { SettingQueryParams } from '@/features/wms-ops/api/types';
import { mapWmsError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms **운영 설정**(operations settings) read proxy for client
 * components (TASK-PC-FE-224 — the client `listSettings` is a NEW addition,
 * `wms-settings-api.ts`, § 5.1). The typed API client's single backend
 * entry point — no browser-direct wms call (architecture.md § Forbidden
 * Dependencies / contract § 2.3). The HttpOnly **IAM OIDC access token** is
 * attached server-side in `listSettings()` (NOT the IAM operator token —
 * § 2.4.5 per-domain credential divergence). READ-ONLY: GET only, no
 * mutation branch, no Idempotency-Key, no X-Operator-Reason. Settings
 * **write** (`PUT /settings/{key}`, § 5.3) is `WMS_ADMIN`-gated + out of
 * this task's scope — no proxy route for it exists here.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: SettingQueryParams = {
    keyPrefix: sp.get('keyPrefix') ?? undefined,
    scope: sp.get('scope') ?? undefined,
    warehouseId: sp.get('warehouseId') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listSettings(params);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
