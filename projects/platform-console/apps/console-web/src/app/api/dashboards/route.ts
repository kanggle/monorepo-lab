import { NextResponse } from 'next/server';
import { getOperatorOverview } from '@/features/dashboards';
import { ApiError } from '@/shared/api/errors';
import { getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin composed-overview read proxy for client components (the typed
 * API client's single backend entry point — no browser-direct IAM call,
 * architecture.md § Forbidden Dependencies / contract § 2.3 / § 2.4.4).
 * The HttpOnly operator token + active tenant are attached server-side
 * inside each reused FE-002/003/004 client; this route only composes the
 * bounded fan-out and maps the WHOLE-overview outcome to an HTTP shape.
 *
 * READ-ONLY (§ 2.4.4): GET only — there is NO request body schema and NO
 * mutation branch (the FE-002 `_proxy` mutation mapping is deliberately
 * NOT reused; only its error→HTTP read shape is mirrored, as the FE-003
 * audit proxy does). NO `X-Operator-Reason` / `Idempotency-Key` anywhere.
 *
 * Per-source isolation is INSIDE `getOperatorOverview()` — a degraded /
 * forbidden CARD is part of the 200 payload (a card status, never an HTTP
 * error). This route only maps the WHOLE-overview outcomes:
 *   - 401 on ANY leg → 401 (the client api-client triggers refresh →
 *     re-login; no partial authed state — 401 is never a per-card degrade).
 *   - 400 NO_ACTIVE_TENANT → 400 (tenant gate; never an empty header).
 *   - any other whole-fan-out failure → 503 (the overview degrades, the
 *     console shell stays intact — never blank).
 *
 * No token / source PII is ever logged (only the request id + status).
 */
export async function GET() {
  const requestId = newRequestId();

  // Pre-flight tenant gate (no empty `X-Tenant-Id` ever leaves on any
  // leg — § 2.4.4). Mirrors the FE-003 audit proxy / `overview-state.ts`.
  const tenant = await getActiveTenant();
  if (!tenant) {
    return NextResponse.json(
      { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
      { status: 400 },
    );
  }

  try {
    const overview = await getOperatorOverview();
    return NextResponse.json(overview);
  } catch (err) {
    // 401 on ANY leg → whole-overview forced re-login (no partial authed
    // state). `getOperatorOverview()` re-throws a leg 401 as ApiError(401).
    if (err instanceof ApiError && err.status === 401) {
      return NextResponse.json(
        { code: err.code, message: 'session expired' },
        { status: 401 },
      );
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return NextResponse.json(
        { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
        { status: 400 },
      );
    }
    // Any other whole-fan-out failure → 503 (the overview degrades; the
    // console shell stays intact — never a blank crash). Per-card
    // failures never reach here (isolated inside the fan-out).
    logger.warn('overview_proxy_degraded', { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'overview unavailable' },
      { status: 503 },
    );
  }
}
