import { NextResponse } from 'next/server';
import { listAsns } from '@/features/wms-ops/api/wms-api';
import type { AsnQueryParams } from '@/features/wms-ops/api/types';
import { mapWmsError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms 입고예정(ASN) read proxy for client components (TASK-PC-FE-222
 * — the client `listAsns` existed since the wms-shipments-api split but was
 * never wired to UI). The typed API client's single backend entry point — no
 * browser-direct wms call (architecture.md § Forbidden Dependencies / contract
 * § 2.3). The HttpOnly **IAM OIDC access token** is attached server-side in
 * `listAsns()` (NOT the IAM operator token — § 2.4.5 per-domain credential
 * divergence). READ-ONLY: GET only, no mutation branch, no Idempotency-Key, no
 * X-Operator-Reason.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: AsnQueryParams = {
    warehouseId: sp.get('warehouseId') ?? undefined,
    supplierPartnerId: sp.get('supplierPartnerId') ?? undefined,
    status: sp.get('status') ?? undefined,
    expectedArriveDateFrom: sp.get('expectedArriveDateFrom') ?? undefined,
    expectedArriveDateTo: sp.get('expectedArriveDateTo') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listAsns(params);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
