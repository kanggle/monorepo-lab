import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';

/**
 * Shared, feature-internal call core for the ecommerce-ops slices
 * (TASK-PC-FE-094 Unit 1 — Reduce Duplication). The eight `*-api.ts` slices
 * (products / orders / users / promotions / shippings / notifications /
 * sellers / images) were mirror-built and each carried a ~95%-identical
 * `call<Slice>()` + `parse<Slice>Error()`. This module hoists the one true copy
 * of that hardened call site so every slice becomes a thin typed wrapper.
 *
 * Behavior is byte-identical to the per-slice copies it replaced:
 *   - credential = `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC
 *     token, else the base access token — net-zero; ADR-MONO-020 D4), NEVER
 *     `getOperatorToken()` (§ 2.4.10);
 *   - headers = Accept / Authorization / X-Request-Id (+ Content-Type only when
 *     a body is present) — deliberately NO `X-Tenant-Id` (gateway resolves the
 *     tenant from the JWT claim) and NO `Idempotency-Key` (producer defines
 *     none — § 2.4.10);
 *   - `AbortController` hard timeout on `ECOMMERCE_TIMEOUT_MS`; `cache:'no-store'`;
 *   - status branch: 401 → `ApiError(401)`, 403 → `ApiError(403)`,
 *     503 → `EcommerceUnavailableError` (circuit_open|downstream),
 *     `!res.ok` → `ApiError(status, code, message, timestamp)`,
 *     success → optional `parse(json)` (or `undefined as T` for a void/204);
 *   - catch: rethrow `ApiError`/`EcommerceUnavailableError`, `AbortError` →
 *     timeout `EcommerceUnavailableError`, else → network
 *     `EcommerceUnavailableError`; finally `clearTimeout`.
 *
 * The per-slice variation is captured by {@link EcommerceCallLabel}: the
 * observability event prefix and the synthetic-message wording. Passing the
 * label keeps the emitted log-event strings (`ecommerce_<event>_ok` etc.) and
 * the `EcommerceUnavailableError` argument strings byte-identical per slice.
 */

/**
 * Per-slice observability + message label. Drives the structured log-event
 * names and the synthetic-message wording so each slice's emitted strings are
 * preserved verbatim.
 *
 * `event` is the log-event infix: most slices use their singular name
 * (`order`, `user`, `seller`, …) yielding `ecommerce_<event>_ok`; the products
 * slice uses the EMPTY string, yielding the bare `ecommerce_ok` /
 * `ecommerce_unauthorized` / … (no infix) — preserved exactly.
 */
export interface EcommerceCallLabel {
  /** Log-event infix (e.g. `order`, `seller`); EMPTY for the products slice. */
  event: string;
  /** Synthetic default for the flat-envelope parser:
   *  `ecommerce <noun> request failed (<status>)`. */
  errorNoun: string;
  /** 503 degrade message: `ecommerce <serviceLabel> unavailable`. */
  unavailableLabel: string;
  /** Timeout message: `ecommerce <callLabel> call timed out`. */
  timedOutLabel: string;
  /** Network-failure message: `ecommerce <callLabel> call failed`. */
  failedLabel: string;
}

/** `ecommerce_ok` (products) vs `ecommerce_<event>_ok` (every other slice). */
function eventName(label: EcommerceCallLabel, suffix: string): string {
  return label.event ? `ecommerce_${label.event}_${suffix}` : `ecommerce_${suffix}`;
}

export interface EcommerceCallOptions {
  method: string;
  /** Absolute base (admin or public subtree, resolved by the caller). */
  base: string;
  /** Path relative to `base`. */
  path: string;
  /** Typed mutation body; `undefined` for reads / DELETE. */
  body?: unknown;
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }`).
 * Defensive: a missing / non-JSON body degrades to a synthetic code rather
 * than throwing (the producer is the authority for the real code; this never
 * crashes the console on a malformed error body).
 */
export async function parseEcommerceError(
  res: Response,
  label: EcommerceCallLabel,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce ${label.errorNoun} request failed (${res.status})`;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as {
      code?: string;
      message?: string;
      timestamp?: string;
    };
    if (body && typeof body === 'object') {
      code = body.code ?? code;
      message = body.message ?? message;
      timestamp = body.timestamp;
    }
  } catch {
    /* keep the synthetic defaults — never throw on a bad error body */
  }
  return { code, message, timestamp };
}

/**
 * Single hardened call site shared by every ecommerce-ops slice. Resolves the
 * domain-facing IAM OIDC token, applies the timeout, and maps the ecommerce
 * flat error envelope to the § 2.5 resilience taxonomy. `parse` is `undefined`
 * for a void mutation / 204 (DELETE).
 */
export async function callEcommerce<T>(
  opts: EcommerceCallOptions,
  parse: ((json: unknown) => T) | undefined,
  label: EcommerceCallLabel,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Per-domain credential selection (§ 2.4.10): the ecommerce gateway requires
  // the IAM OIDC token (account_type=OPERATOR). NEVER getOperatorToken() — that
  // is the IAM-domain (§ 2.6 exchanged) credential; ecommerce would reject it.
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn(eventName(label, 'no_gap_session'), {
      requestId,
      path: opts.path,
    });
    // No IAM OIDC session ⇒ whole-session re-login (no partial authed state).
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
  };
  // NOTE: deliberately NO `X-Tenant-Id` — ecommerce resolves tenant from the
  // JWT `tenant_id` claim (gateway-injected; § 2.4.10 tenant invariant).
  // NOTE: NO `Idempotency-Key` — the producer defines none (§ 2.4.10).
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ECOMMERCE_TIMEOUT_MS);

  try {
    const res = await fetch(`${opts.base}${opts.path}`, {
      method: opts.method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseEcommerceError(res, label);
      logger.warn(eventName(label, 'unauthorized'), {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseEcommerceError(res, label);
      logger.warn(eventName(label, 'forbidden'), {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseEcommerceError(res, label);
      logger.warn(eventName(label, 'degraded'), {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // ONLY the ecommerce section degrades — shell + other sections intact.
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        `ecommerce ${label.unavailableLabel} unavailable`,
      );
    }

    if (!res.ok) {
      // 4xx producer codes → inline actionable (no crash).
      const e = await parseEcommerceError(res, label);
      logger.warn(eventName(label, 'request_error'), {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info(eventName(label, 'ok'), {
      requestId,
      status: res.status,
      path: opts.path,
    });

    // 204 No Content (DELETE) / void mutation — nothing to parse.
    if (res.status === 204 || parse === undefined) {
      return undefined as T;
    }
    const json = await res.json();
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || err instanceof EcommerceUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn(eventName(label, 'timeout'), {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        `ecommerce ${label.timedOutLabel} call timed out`,
      );
    }
    logger.error(eventName(label, 'error'), { requestId, path: opts.path });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      `ecommerce ${label.failedLabel} call failed`,
    );
  } finally {
    clearTimeout(timer);
  }
}
