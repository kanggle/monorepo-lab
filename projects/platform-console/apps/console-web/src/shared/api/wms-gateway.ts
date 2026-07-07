import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError } from '@/shared/api/errors';

/**
 * Shared server-side **wms gateway HTTP core** (TASK-PC-FE-192 — dedup of the
 * per-feature wms client scaffold). The two wms console feature clients —
 * `wms-ops/api/wms-client.ts` (`callWmsAdmin`, the admin read-model at
 * `WMS_ADMIN_BASE_URL`, surfaces the read-model-lag hint) and
 * `wms-outbound-ops/api/outbound-client.ts` (`callOutbound`, the outbound
 * service at `WMS_OUTBOUND_BASE_URL` with per-call baseUrl/timeout overrides) —
 * previously each carried a near-verbatim copy of the SAME hardened call
 * scaffold. This is that scaffold, extracted ONCE; each feature client is now a
 * thin wrapper that supplies a {@link WmsGatewayProfile}.
 *
 * Server-only by construction: imported exclusively from server components and
 * the `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The token + any data never reach client JS — client
 * components call the same-origin `/api/wms/**` proxy routes.
 *
 * ── INVARIANTS PRESERVED VERBATIM (pinned by
 *    `tests/unit/{wms-api,outbound-api}.test.ts`)
 *
 * - **Per-domain credential** (console-integration-contract § 2.4.5): the
 *   DOMAIN-FACING IAM OIDC token (`getDomainFacingToken()` — assumed-when-
 *   switched, else base; ADR-MONO-020 D4). NEVER `getOperatorToken()`.
 * - **Tenant model**: NO `X-Tenant-Id` — wms resolves the tenant from the JWT
 *   `tenant_id` claim.
 * - **Mutation discipline**: a non-GET method REQUIRES a caller-supplied
 *   `Idempotency-Key` (else `400 VALIDATION_ERROR`); reads carry none. NO
 *   `X-Operator-Reason` (the wms surfaces do not define it). `Content-Type` is
 *   attached only when a body is present.
 * - **wms NESTED error envelope** `{ error: { code, message, timestamp } }`
 *   ({@link parseWmsError}); a missing/flat/non-JSON body degrades to a
 *   synthetic code, never throws.
 * - **Resilience** (§ 2.5): AbortController hard timeout; `401` → whole-session
 *   re-login `ApiError`; `403`/`400`/`404`/`422`/`409` → inline `ApiError`;
 *   `503`/timeout/network → the profile's section-degrade error.
 * - **Read-model-lag honesty**: the `X-Read-Model-Lag-Seconds` header is read
 *   and surfaced on the result (`lagSeconds`) — a NON-blocking hint. (Outbound
 *   responses omit it → `null`; the outbound wrapper discards it.)
 *
 * Logging: structured, server-side only; the IAM token + any wms data are NEVER
 * logged. Event names carry the profile prefix (`wms_*` / `wms_outbound_*`).
 */

/**
 * Parses the wms NESTED error envelope (`{ error: { code, message, timestamp } }`).
 * Defensive: a missing / flat / non-JSON body degrades to a synthetic code
 * rather than throwing. `failLabel` is the synthetic default used only when the
 * body carries no `message` — the status is appended (`<label> (<status>)`),
 * reproducing each feature client's original default verbatim.
 */
export async function parseWmsError(
  res: Response,
  failLabel: string,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `${failLabel} (${res.status})`;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as {
      error?: { code?: string; message?: string; timestamp?: string };
    };
    if (body && typeof body === 'object' && body.error) {
      code = body.error.code ?? code;
      message = body.error.message ?? message;
      timestamp = body.error.timestamp;
    }
  } catch {
    /* keep the synthetic defaults — never throw on a bad error body */
  }
  return { code, message, timestamp };
}

/** The `X-Read-Model-Lag-Seconds` hint (producer sets it on > 5 s projection
 *  lag); `null` when absent / non-numeric. */
export function readWmsLagHeader(res: Response): number | null {
  const raw = res.headers.get('X-Read-Model-Lag-Seconds');
  if (raw === null) return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

/** A wms gateway request (relative to the profile's / override base URL). */
export interface WmsGatewayRequest {
  method: string;
  path: string;
  /** Caller-supplied stable key — REQUIRED for a non-GET method. */
  idempotencyKey?: string;
  /** Typed mutation body; `undefined` for reads (⇒ no `Content-Type`). */
  body?: unknown;
  /** Base URL override (else the profile default). */
  baseUrl?: string;
  /** Timeout override in ms (else the profile default). */
  timeoutMs?: number;
}

/**
 * Per-feature behaviour the shared core is parameterised by — the ONLY thing
 * that differs between the two wms clients.
 */
export interface WmsGatewayProfile {
  /** Log-event prefix: `wms` | `wms_outbound`. */
  logPrefix: string;
  /** Synthetic default label for {@link parseWmsError} (body-less errors). */
  requestFailedLabel: string;
  /** Resolves the default base URL + timeout from the server env (accessed
   *  inside the server-only core, so the feature clients never touch
   *  `getServerEnv()`). A request may still override either per-call. */
  resolveDefaults: (env: ReturnType<typeof getServerEnv>) => {
    baseUrl: string;
    timeoutMs: number;
  };
  /** Build the section-degrade error (`503`/timeout/network) — the feature's
   *  `Wms*UnavailableError` variant so ONLY that console section degrades. */
  makeUnavailable: (
    reason: 'timeout' | 'circuit_open' | 'downstream',
    code: string,
    message: string,
  ) => Error;
  /** `instanceof` guard for the profile's degrade error (catch re-throw). */
  isUnavailable: (err: unknown) => boolean;
  /** Degrade / timeout / network `message` strings (preserved per feature). */
  messages: { degraded: string; timeout: string; network: string };
}

/**
 * The single hardened wms gateway call site (extracted from the two feature
 * clients). Resolves the domain-facing IAM OIDC token, applies the timeout, maps
 * the wms NESTED error envelope to the § 2.5 resilience taxonomy, and surfaces
 * the read-model-lag hint. Returns `{ data, lagSeconds }` — the admin wrapper
 * returns it as-is (`WmsResult`), the outbound wrapper takes `.data`.
 */
export async function callWmsGateway<T>(
  req: WmsGatewayRequest,
  parse: (json: unknown) => T,
  profile: WmsGatewayProfile,
): Promise<{ data: T; lagSeconds: number | null }> {
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
    // wms gateway echoes/generates X-Request-Id; X-Actor-Id is set by the
    // wms gateway from the JWT — the console does NOT forge it.
    'X-Request-Id': requestId,
    // Deliberately NO `X-Tenant-Id` — wms resolves tenant from the JWT claim.
  };

  if (req.method !== 'GET') {
    if (!req.idempotencyKey) {
      throw new ApiError(
        400,
        'VALIDATION_ERROR',
        'An idempotency key is required for this action',
      );
    }
    headers['Idempotency-Key'] = req.idempotencyKey;
    // NO `X-Operator-Reason` — the wms surfaces do not define it.
  }
  if (req.body !== undefined) headers['Content-Type'] = 'application/json';

  const defaults = profile.resolveDefaults(env);
  const baseUrl = req.baseUrl ?? defaults.baseUrl;
  const timeoutMs = req.timeoutMs ?? defaults.timeoutMs;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const res = await fetch(`${baseUrl}${req.path}`, {
      method: req.method,
      headers,
      body: req.body === undefined ? undefined : JSON.stringify(req.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseWmsError(res, profile.requestFailedLabel);
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
      const e = await parseWmsError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_forbidden`, {
        requestId,
        status: 403,
        code: e.code,
        path: req.path,
      });
      // Role-insufficient → inline, no crash, no re-login loop.
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseWmsError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_degraded`, {
        requestId,
        status: 503,
        code: e.code,
        path: req.path,
      });
      // ONLY this wms section degrades — shell + other sections intact.
      throw profile.makeUnavailable(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        profile.messages.degraded,
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR, 404 *_NOT_FOUND, 422 STATE_TRANSITION_INVALID,
      // 409 CONFLICT / DUPLICATE_REQUEST → inline actionable (no crash).
      const e = await parseWmsError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_request_error`, {
        requestId,
        status: res.status,
        code: e.code,
        path: req.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const lagSeconds = readWmsLagHeader(res);
    const json = await res.json();
    logger.info(`${logPrefix}_ok`, {
      requestId,
      status: res.status,
      path: req.path,
      lagSeconds,
    });
    return { data: parse(json), lagSeconds };
  } catch (err) {
    if (err instanceof ApiError || profile.isUnavailable(err)) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn(`${logPrefix}_timeout`, {
        requestId,
        timeoutMs,
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
  } finally {
    clearTimeout(timer);
  }
}
