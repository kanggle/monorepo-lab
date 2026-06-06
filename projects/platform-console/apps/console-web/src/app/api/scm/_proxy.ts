import { NextResponse } from 'next/server';
import {
  ScmUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the scm-ops same-origin proxy routes
 * (console-integration-contract § 2.4.6 / § 2.5). The HttpOnly **IAM OIDC
 * access token** is attached server-side in `scm-api.ts` — NOT the GAP
 * exchanged operator token (scm's gateway requires the IAM OIDC token; the
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

export const mapScmError = makeProxyErrorMapper(
  'scm',
  ScmUnavailableError,
  [
    // 429 ScmRateLimitedError — ONE bounded backoff, no re-storm (§ 2.4.6 Edge Case).
    (err, requestId) => {
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
      return null;
    },
  ],
);

export { newRequestId };
