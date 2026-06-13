import { NextResponse } from 'next/server';
import { z } from 'zod';
import {
  ScmReplenishmentUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the scm-replenishment same-origin proxy
 * routes (console-integration-contract § 2.4.6.1 / § 2.5). The HttpOnly
 * **domain-facing IAM OIDC access token** is attached server-side in
 * `demand-planning-api.ts` — NOT the IAM exchanged operator token (scm's
 * gateway requires the IAM OIDC token; the #569 invariant is GAP-domain-scoped
 * — the § 2.4.5/§ 2.4.6 rule reused, NOT re-derived). Mirrors the FE-008
 * scm-ops `_proxy` shape for the scm (FLAT) envelope.
 *
 * The two ACTION routes (approve / dismiss) carry an OPTIONAL note/reason in
 * the request BODY — there is NO `Idempotency-Key` and NO `X-Operator-Reason`
 * header (demand-planning-api defines neither; the producer is idempotent by
 * suggestion state). approve materialises a DRAFT PO only — there is NO
 * procurement submit/confirm/cancel route here.
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION re-login).
 *   - 403 → 403 (token not scm-scoped → inline "not scoped").
 *   - 429 → 429 + Retry-After (one bounded backoff already done; no re-storm).
 *   - 400 / 404 / 409 / 422 → passthrough (inline actionable, no crash; the
 *     idempotent approve path returns a 200 so it never reaches here, a hard
 *     409 SUGGESTION_ALREADY_MATERIALIZED surfaces as a benign notice).
 *   - 503 / timeout / network → 503 (ONLY this section degrades).
 *
 * No token / scm data (incl. the note/reason) is ever logged.
 */

/** Optional action request body — `note` (approve) / `reason` (dismiss). Both
 *  OPTIONAL (the producer accepts an empty body); the reason rides in the BODY,
 *  never an `X-Operator-Reason` header. A non-string is rejected as a bad body
 *  so the proxy never forwards a malformed reason. */
export const ActionBodySchema = z
  .object({
    note: z.string().optional(),
    reason: z.string().optional(),
  })
  .passthrough();
export type ActionBody = z.infer<typeof ActionBodySchema>;

export const mapReplenishmentError = makeProxyErrorMapper(
  'scm-replenishment',
  ScmReplenishmentUnavailableError,
  [
    // 429 ScmRateLimitedError — ONE bounded backoff, no re-storm (reused from
    // the § 2.4.6 scm read surface; the same rate-limited scm gateway).
    (err, requestId) => {
      if (err instanceof ScmRateLimitedError) {
        logger.warn('scm_replenishment_proxy_rate_limited', {
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

export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

export { newRequestId };
