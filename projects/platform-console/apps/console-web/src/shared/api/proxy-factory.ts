import { NextResponse } from 'next/server';
import { ApiError } from '@/shared/api/errors';
import { logger } from '@/shared/lib/logger';

/**
 * An `UnavailableError`-shaped instance: has a string `code` and
 * `reason` field (matching all `*UnavailableError` classes in
 * `errors.ts`). Used by {@link makeProxyErrorMapper} to access
 * the degrade signal fields after an `instanceof` check.
 */
export interface UnavailableErrorLike extends Error {
  readonly code: string;
  readonly reason: string;
}

/**
 * A domain-specific extra handler inserted into the shared skeleton
 * between the {@code ApiError} 403 branch and the generic
 * {@code ApiError} passthrough. Use this to handle cases unique to
 * one domain (e.g. `NO_ACTIVE_TENANT`, `ScmRateLimitedError`).
 *
 * Return a {@link NextResponse} to short-circuit; return `null` to
 * fall through to the next branch.
 */
export type ExtraHandler = (
  err: unknown,
  requestId: string,
) => NextResponse | null;

/**
 * Shared error → HTTP mapping factory for the same-origin domain proxy
 * routes (console-integration-contract § 2.4.5–2.4.8).
 *
 * <p>All four non-IAM domain proxies (wms / scm / finance / erp) share
 * an identical error-handling skeleton:
 * <ol>
 *   <li>{@code ApiError(401)} → 401 (whole-session re-login signal).</li>
 *   <li>{@code ApiError(403)} → 403 (role-insufficient / not scoped).</li>
 *   <li>{@code ApiError} any other status → passthrough (inline
 *       actionable, no crash).</li>
 *   <li>{@code <Domain>UnavailableError} → {@code warn} log + 503
 *       (ONLY the domain section degrades; the console shell stays
 *       intact).</li>
 *   <li>Unknown → {@code error} log + 503.</li>
 * </ol>
 *
 * <p>Domain-specific extras (e.g. {@code NO_ACTIVE_TENANT} code check for
 * the accounts proxy, or {@code ScmRateLimitedError} for scm) are injected
 * via the optional {@code extras} array. Each handler is called in order
 * between step 2 and step 3 above; the first non-null return wins.
 *
 * @param domain       lower-case domain label for log events and
 *                     unavailable-message strings
 *                     (e.g. {@code 'wms'}, {@code 'scm'}, {@code 'finance'},
 *                     {@code 'erp'}, {@code 'accounts'})
 * @param UnavailableErrorClass  the domain-specific
 *                     {@code *UnavailableError} constructor — checked with
 *                     {@code instanceof}
 * @param extras       optional extra handlers inserted before the generic
 *                     {@code ApiError} passthrough; see {@link ExtraHandler}
 * @returns            a function {@code (err: unknown, requestId: string) => NextResponse}
 *                     suitable for use as the catch handler in a proxy route
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type UnavailableErrorCtor = abstract new (...args: any[]) => UnavailableErrorLike;

export function makeProxyErrorMapper(
  domain: string,
  UnavailableErrorClass: UnavailableErrorCtor,
  extras: ExtraHandler[] = [],
): (err: unknown, requestId: string) => NextResponse {
  return (err: unknown, requestId: string): NextResponse => {
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

    // Domain-specific extras (e.g. NO_ACTIVE_TENANT, ScmRateLimitedError)
    for (const extra of extras) {
      const result = extra(err, requestId);
      if (result !== null) return result;
    }

    if (err instanceof ApiError) {
      // Any other status → inline actionable passthrough (no crash).
      return NextResponse.json(
        { code: err.code, message: err.message },
        { status: err.status },
      );
    }
    if (err instanceof UnavailableErrorClass) {
      logger.warn(`${domain}_proxy_degraded`, {
        requestId,
        reason: (err as UnavailableErrorLike).reason,
      });
      return NextResponse.json(
        {
          code: (err as UnavailableErrorLike).code,
          message: `${domain} unavailable`,
        },
        { status: 503 },
      );
    }
    logger.error(`${domain}_proxy_error`, { requestId });
    return NextResponse.json(
      { code: 'SERVICE_UNAVAILABLE', message: `${domain} unavailable` },
      { status: 503 },
    );
  };
}
