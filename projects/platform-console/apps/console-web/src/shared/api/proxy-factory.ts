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
      // TASK-PC-FE-318 — the producer's `details` passes through when present
      // (absent → the body is byte-identical to before).
      return NextResponse.json(
        err.details === undefined
          ? { code: err.code, message: err.message }
          : { code: err.code, message: err.message, details: err.details },
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

/**
 * Error → HTTP mapping shared by the IAM-family proxies (tenants / operators /
 * groups / org-nodes / partnerships / subscriptions / domain-permission-sets).
 * TASK-PC-FE-385 — extracted verbatim from the hand-written per-file `mapError`.
 *
 * 🔴 Behaviour differs from {@link makeProxyErrorMapper}: 403 passes through
 * with the server's own code + message, `details` is dropped, and the final
 * fallback code is `DOWNSTREAM_ERROR`.
 */
export function makeIamProxyErrorMapper(opts: {
  logPrefix: string;
  UnavailableErrorClass: UnavailableErrorCtor;
  unavailableMessage: string;
}): (err: unknown, requestId: string) => NextResponse {
  const { logPrefix, UnavailableErrorClass, unavailableMessage } = opts;
  return (err: unknown, requestId: string): NextResponse => {
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
    if (err instanceof ApiError) {
      return NextResponse.json(
        { code: err.code, message: err.message },
        { status: err.status },
      );
    }
    if (err instanceof UnavailableErrorClass) {
      logger.warn(`${logPrefix}_proxy_degraded`, {
        requestId,
        reason: err.reason,
      });
      return NextResponse.json(
        { code: err.code, message: unavailableMessage },
        { status: 503 },
      );
    }
    logger.error(`${logPrefix}_proxy_error`, { requestId });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: unavailableMessage },
      { status: 503 },
    );
  };
}

/** 422 body-validation failure — one copy for every `_proxy.ts`. */
export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}
