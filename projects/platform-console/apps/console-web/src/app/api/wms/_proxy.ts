import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the wms-ops same-origin proxy routes
 * (console-integration-contract § 2.4.5 / § 2.5). The HttpOnly **GAP OIDC
 * access token** is attached server-side in `wms-api.ts` — NOT the GAP
 * exchanged operator token (the wms gateway requires the GAP OIDC token;
 * the #569 invariant is GAP-domain-scoped — § 2.4.5). Mirrors the FE-002
 * `_proxy` shape but for the wms (nested) envelope + GAP-token credential.
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION re-login;
 *     no partial authed state — NOT a per-section degrade).
 *   - 403 → 403 (role-insufficient → inline "not available to your role").
 *   - 400 / 404 / 422 STATE_TRANSITION_INVALID / 409 DUPLICATE_REQUEST →
 *     passthrough (inline actionable, no crash).
 *   - 503 / timeout / network → 503 (ONLY the wms section degrades; the
 *     console shell + GAP sections stay intact).
 *
 * No token / wms data is ever logged.
 */

/** Alert-ack request body: ONLY an idempotency key (the wms alert-ack is
 *  reason-free — NO `X-Operator-Reason`; confirm-gated in the UI). */
export const AckBodySchema = z.object({
  idempotencyKey: z.string().min(1),
});
export type AckBody = z.infer<typeof AckBodySchema>;

export function mapWmsError(err: unknown, requestId: string): NextResponse {
  if (err instanceof ApiError && err.status === 401) {
    return NextResponse.json(
      { code: err.code || 'UNAUTHORIZED', message: 'session expired' },
      { status: 401 },
    );
  }
  if (err instanceof ApiError && err.status === 403) {
    return NextResponse.json(
      { code: err.code || 'FORBIDDEN', message: 'not permitted' },
      { status: 403 },
    );
  }
  if (err instanceof ApiError) {
    // 400 VALIDATION_ERROR / 404 NOT_FOUND /
    // 422 STATE_TRANSITION_INVALID / 409 DUPLICATE_REQUEST → inline
    // actionable (passthrough, no crash).
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof WmsUnavailableError) {
    logger.warn('wms_proxy_degraded', { requestId, reason: err.reason });
    return NextResponse.json(
      { code: err.code, message: 'wms unavailable' },
      { status: 503 },
    );
  }
  logger.error('wms_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'SERVICE_UNAVAILABLE', message: 'wms unavailable' },
    { status: 503 },
  );
}

export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

export { newRequestId };
