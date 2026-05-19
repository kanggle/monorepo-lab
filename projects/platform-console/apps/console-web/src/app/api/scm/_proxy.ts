import { NextResponse } from 'next/server';
import {
  ApiError,
  ScmUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the scm-ops same-origin proxy routes
 * (console-integration-contract § 2.4.6 / § 2.5). The HttpOnly **GAP OIDC
 * access token** is attached server-side in `scm-api.ts` — NOT the GAP
 * exchanged operator token (scm's gateway requires the GAP OIDC token; the
 * #569 invariant is GAP-domain-scoped — the § 2.4.5 rule reused, NOT
 * re-derived). Mirrors the FE-007 wms `_proxy` shape but for the scm
 * (FLAT) envelope.
 *
 * STRICTLY READ-ONLY: these proxies expose ONLY GET routes — there is NO
 * mutation route (no PO write, no webhook, no Idempotency-Key, no
 * X-Operator-Reason, no request body).
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION re-login;
 *     no partial authed state — NOT a per-section degrade).
 *   - 403 → 403 (token not scm-scoped → inline "not available / not
 *     scoped").
 *   - 429 → 429 (rate-limited; the api client already did ONE bounded
 *     backoff — the proxy surfaces it, the client does NOT re-storm).
 *   - 400 / 404 / 422 → passthrough (inline actionable, no crash).
 *   - 503 / timeout / network → 503 (ONLY the scm section degrades; the
 *     console shell + GAP/wms sections stay intact).
 *
 * No token / scm data is ever logged.
 */
export function mapScmError(err: unknown, requestId: string): NextResponse {
  if (err instanceof ApiError && err.status === 401) {
    return NextResponse.json(
      { code: err.code || 'UNAUTHORIZED', message: 'session expired' },
      { status: 401 },
    );
  }
  if (err instanceof ApiError && err.status === 403) {
    return NextResponse.json(
      { code: err.code || 'TENANT_FORBIDDEN', message: 'not permitted' },
      { status: 403 },
    );
  }
  if (err instanceof ScmRateLimitedError) {
    logger.warn('scm_proxy_rate_limited', {
      requestId,
      retryAfterSeconds: err.retryAfterSeconds,
    });
    return NextResponse.json(
      { code: err.code, message: 'scm gateway rate-limited' },
      {
        status: 429,
        headers: { 'Retry-After': String(err.retryAfterSeconds) },
      },
    );
  }
  if (err instanceof ApiError) {
    // 400/422 VALIDATION_ERROR / 404 PO_NOT_FOUND|NODE_NOT_FOUND /
    // 409 CONFLICT → inline actionable (passthrough, no crash).
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof ScmUnavailableError) {
    logger.warn('scm_proxy_degraded', { requestId, reason: err.reason });
    return NextResponse.json(
      { code: err.code, message: 'scm unavailable' },
      { status: 503 },
    );
  }
  logger.error('scm_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'SERVICE_UNAVAILABLE', message: 'scm unavailable' },
    { status: 503 },
  );
}

export { newRequestId };
