import { NextResponse } from 'next/server';
import { listRefs } from '@/features/wms-ops/api/wms-refs-api';
import { REF_TYPES, type RefQueryParams } from '@/features/wms-ops/api/types';
import { mapWmsError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms **마스터**(master reference data) read proxy for client
 * components (TASK-PC-FE-223 — the client `listRefs` existed since the
 * `wms-refs-api.ts` § 1.7 client split but was never wired to UI). The typed
 * API client's single backend entry point — no browser-direct wms call
 * (architecture.md § Forbidden Dependencies / contract § 2.3). The HttpOnly
 * **IAM OIDC access token** is attached server-side in `listRefs()` (NOT the
 * IAM operator token — § 2.4.5 per-domain credential divergence).
 * READ-ONLY: GET only, no mutation branch, no Idempotency-Key, no
 * X-Operator-Reason.
 *
 * **Type whitelist** (task § Failure Scenarios — a missing whitelist would
 * let an arbitrary `{type}` pass through to the producer, surfacing a raw
 * 404/500 instead of a clean client-side error): `{type}` MUST be one of
 * `REF_TYPES` (`admin-service-api.md` § 1.7's documented set) — an
 * unsupported type is rejected here with `400 VALIDATION_ERROR`, BEFORE any
 * upstream call is made.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ type: string }> },
) {
  const requestId = newRequestId();
  const { type } = await params;

  if (!(REF_TYPES as readonly string[]).includes(type)) {
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message: `unsupported ref type: ${type}`,
      },
      { status: 400 },
    );
  }

  const sp = new URL(req.url).searchParams;
  const params_: RefQueryParams = {
    q: sp.get('q') ?? undefined,
    status: sp.get('status') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listRefs(type, params_);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
