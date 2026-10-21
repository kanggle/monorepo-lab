import { NextResponse } from 'next/server';
import { getThroughput } from '@/features/wms-ops/api/wms-inventory-api';
import { validateThroughputQuery } from '@/features/wms-ops/lib/throughput';
import { mapWmsError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms **daily throughput** read proxy for the `/wms` 개요 card's
 * warehouse switch (TASK-PC-FE-358 — console-integration-contract § 2.4.5
 * routing note + § 2.4.5.2 «Daily throughput card»; producer
 * `admin-service-api.md` § 1.2). Mirrors `master/refs/[type]/route.ts`: the
 * domain-facing IAM OIDC token is attached server-side inside
 * `getThroughput()`; READ-ONLY (GET only — no Idempotency-Key, no
 * X-Operator-Reason, no body).
 *
 * **Validated BEFORE any upstream call** → `400 VALIDATION_ERROR`:
 * `warehouseId` not a UUID · `from`/`to` missing or not a real ISO LocalDate ·
 * `to < from` · inclusive span > 90 days. These are exactly the inputs § 1.2
 * documents the producer rejecting, so the console never sends one.
 *
 * The `200` body is the producer's § 1.2 shape (parsed; unknown extra fields
 * pass through). Zero-fill is the CLIENT's job (`zeroFillThroughput`), so the
 * proxy stays a faithful pass-through. Errors → `mapWmsError` (401 → 401
 * whole-session re-login, 403 → 403, 400/404 passthrough, 503/timeout → 503).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const q = {
    warehouseId: sp.get('warehouseId'),
    from: sp.get('from'),
    to: sp.get('to'),
  };
  const invalid = validateThroughputQuery(q);
  if (invalid !== null) {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: invalid },
      { status: 400 },
    );
  }
  try {
    const result = await getThroughput({
      warehouseId: q.warehouseId as string,
      from: q.from as string,
      to: q.to as string,
    });
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
