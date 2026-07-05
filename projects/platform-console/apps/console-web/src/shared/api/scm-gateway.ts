import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, ScmRateLimitedError } from '@/shared/api/errors';

/**
 * Shared server-side **scm gateway HTTP core** (TASK-PC-FE-189 — dedup of the
 * per-feature scm client scaffold). The three scm console feature clients —
 * `scm-ops/api/scm-client.ts` (개요: procurement + inventory-visibility, GET
 * read-only), `scm-replenishment/api/demand-planning-api.ts` (보충: suggestions
 * read + approve/dismiss), `scm-config/api/demand-planning-seed-api.ts` (설정:
 * per-SKU reorder-policy + sku-supplier-map GET/PUT) — previously each carried a
 * near-verbatim copy of the SAME hardened call scaffold. This is that scaffold,
 * extracted ONCE; each feature client is now a thin wrapper that supplies a
 * {@link ScmGatewayProfile} (its degrade error class + log-event prefix +
 * messages) and the endpoint path/method/body.
 *
 * Server-only by construction (same posture as every feature client that used
 * it): imported exclusively from server components and the `runtime = 'nodejs'`
 * route handlers; `getServerEnv()` throws outside the server runtime. The token
 * + any data never reach client JS — client components call the same-origin
 * `/api/scm/**` proxy routes, which attach the HttpOnly credential here
 * server-side.
 *
 * ── INVARIANTS PRESERVED VERBATIM (the three clients' contract; pinned by
 *    `tests/unit/{scm-api,demand-planning-api,demand-planning-seed-api}.test.ts`)
 *
 * - **Per-domain credential** (console-integration-contract § 2.4.5/2.4.6): the
 *   DOMAIN-FACING IAM OIDC token (`getDomainFacingToken()` — assumed-when-
 *   switched, else base; ADR-MONO-020 D4). NEVER `getOperatorToken()` (that is
 *   the GAP-exchanged credential; scm has no token-exchange, would reject it).
 * - **Tenant model**: NO `X-Tenant-Id` — scm resolves the tenant from the JWT
 *   `tenant_id` claim (`∈ {scm,*}`); cross-tenant → `403 TENANT_FORBIDDEN`.
 * - **scm FLAT error envelope** `{ code, message, details?, timestamp }`
 *   (DISTINCT from wms NESTED `{ error: { code } }`); a missing/non-JSON body
 *   degrades to a synthetic code, never throws ({@link parseScmError}).
 * - **Resilience** (§ 2.5): AbortController hard timeout (`SCM_TIMEOUT_MS`);
 *   `401`/`403`/`404`(non-empty)/`400`/`422`/`409` → {@link ApiError} (inline
 *   actionable / whole-session re-login for 401); `429` → ONE bounded backoff
 *   honouring `Retry-After` (cap 5s) then {@link ScmRateLimitedError} (NO retry
 *   storm); `503`/timeout/network → the profile's section-degrade error.
 * - **Read-only vs mutation**: `Content-Type` is attached ONLY when a `body` is
 *   present. A read-only caller (scm-ops) passes no method/body → GET, no
 *   `Content-Type`, no `Idempotency-Key`, no `X-Operator-Reason` (pinned).
 *
 * Logging: structured, server-side only; the IAM token + any scm data are NEVER
 * logged. Event names carry the profile prefix (`scm_*` / `scm_replenishment_*`
 * / `scm_config_*`) so the per-section operational signals are unchanged.
 */

/** The bounded 429 backoff: at most ONE retry honouring `Retry-After` (capped)
 *  — NEVER an unbounded retry storm into the rate-limited gateway. */
export const MAX_SCM_RETRY_AFTER_SECONDS = 5;

export function parseScmRetryAfter(res: Response): number {
  const raw = res.headers.get('Retry-After');
  const n = raw === null ? NaN : Number(raw);
  if (!Number.isFinite(n) || n <= 0) return 1; // contract default (1s)
  return Math.min(MAX_SCM_RETRY_AFTER_SECONDS, n);
}

/**
 * Parses the scm FLAT error envelope (`{ code, message, details?, timestamp }`).
 * Defensive: a missing / nested (wms-shaped) / non-JSON body degrades to a
 * synthetic code rather than throwing (the producer is the authority for the
 * real code; this never crashes the console on a malformed error body). A
 * wms-nested parser would MISS the scm flat `code` — the per-domain envelope
 * correctness pinned by tests. `failLabel` is the synthetic default used only
 * when the body carries no `message` — the status is appended (`<label> (<status>)`),
 * reproducing each feature client's original default verbatim.
 */
export async function parseScmError(
  res: Response,
  failLabel: string,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `${failLabel} (${res.status})`;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as {
      code?: string;
      message?: string;
      timestamp?: string;
    };
    if (body && typeof body === 'object') {
      if (typeof body.code === 'string') code = body.code;
      if (typeof body.message === 'string') message = body.message;
      if (typeof body.timestamp === 'string') timestamp = body.timestamp;
    }
  } catch {
    /* keep the synthetic defaults — never throw on a bad error body */
  }
  return { code, message, timestamp };
}

/** A scm gateway request (relative to `${SCM_GATEWAY_BASE_URL}`). */
export interface ScmGatewayRequest {
  /** Path relative to `${SCM_GATEWAY_BASE_URL}` (e.g. `/api/v1/procurement/po`). */
  path: string;
  /** HTTP method; defaults to `GET` (a read-only caller omits it). */
  method?: string;
  /** Optional mutation body; when present, `Content-Type: application/json` is
   *  attached and the body is JSON-serialised. A read-only caller omits it (no
   *  `Content-Type` — pinned by the scm-ops read-only test). */
  body?: unknown;
  /** Seed-lookup GETs only: a `404` short-circuits to the profile's
   *  `notFoundSentinel` instead of throwing (config's 404-as-empty-state). */
  notFoundIsEmpty?: boolean;
}

/**
 * Per-feature behaviour the shared core is parameterised by — the ONLY thing
 * that differs between the three scm clients.
 */
export interface ScmGatewayProfile {
  /** Log-event prefix: `scm` | `scm_replenishment` | `scm_config`. */
  logPrefix: string;
  /** Synthetic default label for {@link parseScmError} (body-less errors); the
   *  status is appended, e.g. `'scm request failed'` → `scm request failed (503)`. */
  requestFailedLabel: string;
  /** Build the section-degrade error (`503`/timeout/network) — the feature's
   *  `Scm*UnavailableError` variant so ONLY that console section degrades. */
  makeUnavailable: (
    reason: 'timeout' | 'circuit_open' | 'downstream',
    code: string,
    message: string,
  ) => Error;
  /** `instanceof` guard for the profile's degrade error, so the `catch` re-throws
   *  it unchanged instead of re-wrapping (matches each client's original guard). */
  isUnavailable: (err: unknown) => boolean;
  /** Degrade / timeout / network `message` strings (preserved per feature). */
  messages: { degraded: string; timeout: string; network: string };
  /** Value returned on a `404` when `req.notFoundIsEmpty` (config seed lookups).
   *  Absent for the read/action clients (their 404 is a normal `ApiError`). */
  notFoundSentinel?: unknown;
}

/**
 * The single hardened scm gateway call site (extracted from the three feature
 * clients). Resolves the domain-facing IAM OIDC token, applies the timeout,
 * maps the scm FLAT error envelope to the § 2.5 resilience taxonomy, and honours
 * a 429 `Retry-After` with ONE bounded backoff. Returns `{ raw, res }` — `raw`
 * is the parsed body (or the profile's `notFoundSentinel` on a 404-as-empty),
 * `res` exposes response headers to the caller (scm-ops reads `X-Cache`).
 */
export async function callScmGateway<T>(
  req: ScmGatewayRequest,
  parse: (json: unknown) => T,
  profile: ScmGatewayProfile,
): Promise<{ raw: T; res: Response }> {
  const env = getServerEnv();
  const requestId = newRequestId();
  const { logPrefix } = profile;

  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn(`${logPrefix}_no_gap_session`, { requestId, path: req.path });
    // No IAM OIDC session ⇒ whole-session re-login (not a per-section degrade).
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // Deliberately NO `X-Tenant-Id` — scm resolves the tenant from the JWT
    // claim. `Content-Type` is added ONLY when a body is present (read-only
    // callers stay Content-Type-free — pinned).
  };
  if (req.body !== undefined) headers['Content-Type'] = 'application/json';

  async function doFetch(): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), env.SCM_TIMEOUT_MS);
    try {
      return await fetch(`${env.SCM_GATEWAY_BASE_URL}${req.path}`, {
        method: req.method ?? 'GET',
        headers,
        body: req.body === undefined ? undefined : JSON.stringify(req.body),
        cache: 'no-store',
        signal: controller.signal,
      });
    } finally {
      clearTimeout(timer);
    }
  }

  try {
    let res = await doFetch();

    // 429 → ONE bounded backoff honouring Retry-After, then surface.
    if (res.status === 429) {
      const wait = parseScmRetryAfter(res);
      logger.warn(`${logPrefix}_rate_limited`, {
        requestId,
        path: req.path,
        retryAfterSeconds: wait,
      });
      await new Promise((r) => setTimeout(r, wait * 1000));
      res = await doFetch();
      if (res.status === 429) {
        // A second 429 — DO NOT retry again (no storm).
        throw new ScmRateLimitedError(
          parseScmRetryAfter(res),
          'scm gateway rate-limited',
        );
      }
    }

    // 404-as-empty-state — ONLY for seed-lookup GETs (config). The seed row is
    // simply not configured yet → a typed sentinel, NOT an error toast.
    if (
      res.status === 404 &&
      req.notFoundIsEmpty &&
      profile.notFoundSentinel !== undefined
    ) {
      logger.info(`${logPrefix}_not_configured`, { requestId, path: req.path });
      // Drain the body to avoid a leaked stream; its content is not needed.
      try {
        await res.json();
      } catch {
        /* ignore */
      }
      return { raw: profile.notFoundSentinel as T, res };
    }

    if (res.status === 401) {
      const e = await parseScmError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_unauthorized`, {
        requestId,
        status: 401,
        code: e.code,
        path: req.path,
      });
      // IAM OIDC session expired → whole-session re-login (no partial authed
      // state — NOT a per-section degrade).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseScmError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_forbidden`, {
        requestId,
        status: 403,
        code: e.code,
        path: req.path,
      });
      // Token not scm-scoped / insufficient scope → inline "not scoped".
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseScmError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_degraded`, {
        requestId,
        status: 503,
        code: e.code,
        path: req.path,
      });
      // ONLY this section degrades — shell + other sections intact.
      throw profile.makeUnavailable(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        profile.messages.degraded,
      );
    }

    if (!res.ok) {
      // 400/422 VALIDATION_ERROR, 404 (non-empty), 409 CONFLICT, etc. → inline
      // actionable (no crash). The caller maps any idempotent paths to success.
      const e = await parseScmError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_request_error`, {
        requestId,
        status: res.status,
        code: e.code,
        path: req.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info(`${logPrefix}_ok`, {
      requestId,
      status: res.status,
      path: req.path,
    });
    return { raw: parse(json), res };
  } catch (err) {
    if (
      err instanceof ApiError ||
      err instanceof ScmRateLimitedError ||
      profile.isUnavailable(err)
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn(`${logPrefix}_timeout`, {
        requestId,
        timeoutMs: env.SCM_TIMEOUT_MS,
        path: req.path,
      });
      throw profile.makeUnavailable('timeout', 'TIMEOUT', profile.messages.timeout);
    }
    logger.error(`${logPrefix}_error`, { requestId, path: req.path });
    throw profile.makeUnavailable(
      'downstream',
      'NETWORK_ERROR',
      profile.messages.network,
    );
  }
}
