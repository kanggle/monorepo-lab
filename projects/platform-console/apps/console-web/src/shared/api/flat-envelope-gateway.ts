import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError } from '@/shared/api/errors';

/**
 * Shared server-side **FLAT-envelope gateway HTTP core** (TASK-PC-FE-243 —
 * straggler consolidation of the per-feature "hardened call scaffold").
 *
 * A family of console feature clients that each talk to a federated domain
 * gateway returning the FLAT error envelope `{ code, message, details?,
 * timestamp }` (DISTINCT from wms's NESTED `{ error: { code } }`) previously
 * carried a near-verbatim copy of the SAME hardened call scaffold:
 *
 *   - `shared/api/scm-gateway.ts`         (scm procurement / replenishment / config)
 *   - `features/erp-ops/api/erp-client.ts`        (erp masterdata)
 *   - `features/erp-ops/api/approval-call.ts`     (erp approval-service)
 *   - `features/erp-ops/api/delegation-api.ts`    (erp delegation grants)
 *   - `features/finance-ops/api/finance-api.ts`   (finance account-service)
 *   - `features/ledger-ops/api/ledger-client.ts`  (finance ledger-service)
 *
 * This is that scaffold, extracted ONCE. Each feature client is now a thin
 * wrapper that supplies a {@link FlatEnvelopeGatewayProfile} (its degrade
 * error class + log-event prefix + base-URL/timeout selectors + messages +
 * optional 429 / 404-as-empty behaviour) and the endpoint path/method/body.
 * scm-gateway keeps its historical `parseScmError` / `callScmGateway` /
 * `ScmGatewayProfile` surface as re-export shims over this core so existing scm
 * client import paths do NOT change (TASK-PC-FE-189 / TASK-PC-FE-192 lineage).
 *
 * Server-only by construction (same posture as every feature client that used
 * it): imported exclusively from server components and the `runtime = 'nodejs'`
 * route handlers; `getServerEnv()` throws outside the server runtime. The token
 * + any data never reach client JS — client components call the same-origin
 * proxy routes, which attach the HttpOnly credential here server-side.
 *
 * ── INVARIANTS PRESERVED VERBATIM (each consumer's contract; pinned by its
 *    unit test — behaviour is IDENTICAL to the pre-consolidation per-client copy)
 *
 * - **Per-domain credential**: the DOMAIN-FACING IAM OIDC token
 *   (`getDomainFacingToken()` — assumed-when-switched, else base; ADR-MONO-020
 *   D4). NEVER `getOperatorToken()` (the #569 invariant is GAP-domain-scoped).
 * - **Tenant model**: NO `X-Tenant-Id` — each domain resolves the tenant from
 *   the JWT `tenant_id` claim; cross-tenant → `403 TENANT_FORBIDDEN`.
 * - **FLAT error envelope** `{ code, message, details?, timestamp }`; a
 *   missing/non-JSON/wms-nested body degrades to a synthetic code, never throws
 *   ({@link parseFlatEnvelopeError}).
 * - **Resilience**: AbortController hard timeout; `401` → whole-session
 *   re-login `ApiError`; `403`/`400`/`404`(non-empty)/`422`/`409` → inline
 *   `ApiError`; `503`/timeout/network → the profile's section-degrade error.
 * - **Mutation headers**: `Content-Type` is attached ONLY when a `body` is
 *   present; `Idempotency-Key` / `X-Operator-Reason` ONLY when the caller
 *   supplies them. A read-only caller passes none → GET with no mutation
 *   headers.
 * - **Optional 429** (`profile.rateLimit`): ONE bounded backoff honouring
 *   `Retry-After` (capped) then the profile's rate-limited error (scm only).
 *   Absent → a stray `429` falls through the default-error path as a surfaced
 *   `ApiError` (erp / finance / ledger — the honest per-domain difference).
 * - **Optional 404-as-empty** (`profile.notFoundSentinel` + `req.notFoundIsEmpty`):
 *   a seed-lookup `404` returns the typed sentinel instead of throwing.
 * - **Optional idempotency fail-fast guard**
 *   (`profile.requireIdempotencyKeyOnMutation`): a client-side `400` when a
 *   non-GET omits `idempotencyKey`. Default OFF — erp/finance/ledger only
 *   attach the header when present (preserving their exact behaviour; adding
 *   the guard would be an observable change, deliberately out of scope).
 *
 * Logging: structured, server-side only; the token + any domain data are NEVER
 * logged. Event names carry the profile prefix so the per-section operational
 * signals are unchanged. The log `path` field is the request's `logPath`
 * (sanitised route shape) when supplied, else the request `path`.
 */

/** Parses the FLAT error envelope (`{ code, message, details?, timestamp }`).
 *  Defensive: a missing / nested (wms-shaped) / non-JSON body degrades to a
 *  synthetic code rather than throwing (the producer is the authority for the
 *  real code; this never crashes the console on a malformed error body). A
 *  wms-nested parser would MISS the flat `code` — per-domain envelope
 *  correctness pinned by tests. `failLabel` is the synthetic default used only
 *  when the body carries no `message` — the status is appended
 *  (`<label> (<status>)`), reproducing each feature client's original default
 *  verbatim. `details` is preserved for downstream rendering. */
export async function parseFlatEnvelopeError(
  res: Response,
  failLabel: string,
): Promise<{
  code: string;
  message: string;
  details?: unknown;
  timestamp?: string;
}> {
  let code = `HTTP_${res.status}`;
  let message = `${failLabel} (${res.status})`;
  let details: unknown | undefined;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as {
      code?: string;
      message?: string;
      details?: unknown;
      timestamp?: string;
    };
    if (body && typeof body === 'object') {
      if (typeof body.code === 'string') code = body.code;
      if (typeof body.message === 'string') message = body.message;
      if ('details' in body) details = body.details;
      if (typeof body.timestamp === 'string') timestamp = body.timestamp;
    }
  } catch {
    /* keep the synthetic defaults — never throw on a bad error body */
  }
  return { code, message, details, timestamp };
}

/** Parses a `Retry-After` header (seconds), capped at `cap`; defaults to the
 *  contract default (1s) when absent / non-numeric / non-positive. */
export function parseRetryAfterSeconds(res: Response, cap: number): number {
  const raw = res.headers.get('Retry-After');
  const n = raw === null ? NaN : Number(raw);
  if (!Number.isFinite(n) || n <= 0) return 1;
  return Math.min(cap, n);
}

/** A FLAT-envelope gateway request (relative to the profile's base URL). */
export interface FlatEnvelopeGatewayRequest {
  /** Path relative to the profile's base URL (built by the caller, including
   *  any encoded `{id}` + search params — this core never mutates it). */
  path: string;
  /** Sanitised path shape for logging (no record id / no PII). Defaults to
   *  `path` when omitted (scm supplies only `path`; the confidential erp /
   *  finance / ledger clients supply a distinct `{id}`-placeholder shape). */
  logPath?: string;
  /** HTTP method; defaults to `GET` (a read-only caller omits it). */
  method?: string;
  /** Optional mutation body; when present, `Content-Type: application/json` is
   *  attached and the body is JSON-serialised. A read-only caller omits it. */
  body?: unknown;
  /** `Idempotency-Key` header value — attached ONLY when present (unless the
   *  profile's fail-fast guard is on). Reads omit it. */
  idempotencyKey?: string;
  /** `X-Operator-Reason` header value — attached ONLY when present (the erp
   *  approval reasoned transitions; every other caller omits it). */
  operatorReason?: string;
  /** Seed-lookup GETs only: a `404` short-circuits to the profile's
   *  `notFoundSentinel` instead of throwing (config's 404-as-empty-state). */
  notFoundIsEmpty?: boolean;
}

/** Optional bounded-backoff 429 policy (scm only — the other flat domains have
 *  no documented rate-limit and omit this, so a stray 429 surfaces as a plain
 *  `ApiError` through the default-error path). */
export interface FlatEnvelopeRateLimitPolicy {
  /** Cap (seconds) for the single honoured `Retry-After` backoff. */
  maxRetryAfterSeconds: number;
  /** Build the surfaced rate-limited error after a SECOND 429 (no storm). */
  makeError: (retryAfterSeconds: number) => Error;
  /** `instanceof` guard so the `catch` re-throws it unchanged. */
  isRateLimited: (err: unknown) => boolean;
}

/**
 * Per-feature behaviour the shared core is parameterised by — the ONLY thing
 * that differs between the flat-envelope gateway clients.
 */
export interface FlatEnvelopeGatewayProfile {
  /** Log-event prefix (e.g. `scm` | `erp` | `erp_approval` | `finance`). */
  logPrefix: string;
  /** Synthetic default label for {@link parseFlatEnvelopeError} (body-less
   *  errors); the status is appended, e.g. `'erp request failed'` →
   *  `erp request failed (503)`. */
  requestFailedLabel: string;
  /** Resolves the base URL + timeout from the server env (accessed inside the
   *  server-only core, so the feature clients never touch `getServerEnv()`).
   *  Each domain reads a different env var (ERP_*, FINANCE_*, LEDGER_*,
   *  SCM_GATEWAY_BASE_URL / SCM_TIMEOUT_MS). */
  resolveDefaults: (env: ReturnType<typeof getServerEnv>) => {
    baseUrl: string;
    timeoutMs: number;
  };
  /** Build the section-degrade error (`503`/timeout/network) — the feature's
   *  `*UnavailableError` variant so ONLY that console section degrades. */
  makeUnavailable: (
    reason: 'timeout' | 'circuit_open' | 'downstream',
    code: string,
    message: string,
  ) => Error;
  /** `instanceof` guard for the profile's degrade error (catch re-throw). */
  isUnavailable: (err: unknown) => boolean;
  /** Degrade / timeout / network `message` strings (preserved per feature). */
  messages: { degraded: string; timeout: string; network: string };
  /** Optional bounded-backoff 429 policy (scm only). */
  rateLimit?: FlatEnvelopeRateLimitPolicy;
  /** Value returned on a `404` when `req.notFoundIsEmpty` (config seed
   *  lookups). Absent for the read/action clients (their 404 is a normal
   *  `ApiError`). */
  notFoundSentinel?: unknown;
  /** OPTIONAL fail-fast guard: throw a client-side `400 VALIDATION_ERROR` when
   *  a non-GET method omits `idempotencyKey` (the wms posture). Default OFF —
   *  the erp / finance / ledger clients only attach the header when present. */
  requireIdempotencyKeyOnMutation?: boolean;
}

/**
 * The single hardened FLAT-envelope gateway call site (extracted from the
 * feature clients). Resolves the domain-facing IAM OIDC token, applies the
 * timeout, maps the FLAT error envelope to the § 2.5 resilience taxonomy, and —
 * when the profile supplies it — honours a 429 `Retry-After` with ONE bounded
 * backoff. Returns `{ raw, res }` — `raw` is the parsed body (or the profile's
 * `notFoundSentinel` on a 404-as-empty), `res` exposes response headers to the
 * caller (scm-ops reads `X-Cache`). Read-only wrappers take `.raw`.
 */
export async function callFlatEnvelopeGateway<T>(
  req: FlatEnvelopeGatewayRequest,
  parse: (json: unknown) => T,
  profile: FlatEnvelopeGatewayProfile,
): Promise<{ raw: T; res: Response }> {
  const env = getServerEnv();
  const requestId = newRequestId();
  const { logPrefix } = profile;
  const logPath = req.logPath ?? req.path;
  const method = req.method ?? 'GET';

  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn(`${logPrefix}_no_gap_session`, { requestId, path: logPath });
    // No IAM OIDC session ⇒ whole-session re-login (not a per-section degrade).
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // Deliberately NO `X-Tenant-Id` — the domain resolves the tenant from the
    // JWT claim. `Content-Type` / mutation headers are added below only when a
    // body / key / reason is present (read-only callers stay header-free).
  };

  if (profile.requireIdempotencyKeyOnMutation && method !== 'GET') {
    if (!req.idempotencyKey) {
      throw new ApiError(
        400,
        'VALIDATION_ERROR',
        'An idempotency key is required for this action',
      );
    }
  }
  if (req.idempotencyKey !== undefined) {
    headers['Idempotency-Key'] = req.idempotencyKey;
  }
  if (req.operatorReason !== undefined) {
    headers['X-Operator-Reason'] = req.operatorReason;
  }
  if (req.body !== undefined) headers['Content-Type'] = 'application/json';

  const defaults = profile.resolveDefaults(env);
  const timeoutMs = defaults.timeoutMs;

  async function doFetch(): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await fetch(`${defaults.baseUrl}${req.path}`, {
        method,
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

    // Optional 429 → ONE bounded backoff honouring Retry-After, then surface.
    const rl = profile.rateLimit;
    if (rl && res.status === 429) {
      const wait = parseRetryAfterSeconds(res, rl.maxRetryAfterSeconds);
      logger.warn(`${logPrefix}_rate_limited`, {
        requestId,
        path: logPath,
        retryAfterSeconds: wait,
      });
      await new Promise((r) => setTimeout(r, wait * 1000));
      res = await doFetch();
      if (res.status === 429) {
        // A second 429 — DO NOT retry again (no storm).
        throw rl.makeError(parseRetryAfterSeconds(res, rl.maxRetryAfterSeconds));
      }
    }

    // 404-as-empty-state — ONLY for seed-lookup GETs. The seed row is simply
    // not configured yet → a typed sentinel, NOT an error toast.
    if (
      res.status === 404 &&
      req.notFoundIsEmpty &&
      profile.notFoundSentinel !== undefined
    ) {
      logger.info(`${logPrefix}_not_configured`, { requestId, path: logPath });
      // Drain the body to avoid a leaked stream; its content is not needed.
      try {
        await res.json();
      } catch {
        /* ignore */
      }
      return { raw: profile.notFoundSentinel as T, res };
    }

    if (res.status === 401) {
      const e = await parseFlatEnvelopeError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_unauthorized`, {
        requestId,
        status: 401,
        code: e.code,
        path: logPath,
      });
      // IAM OIDC session expired → whole-session re-login (no partial authed
      // state — NOT a per-section degrade).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseFlatEnvelopeError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_forbidden`, {
        requestId,
        status: 403,
        code: e.code,
        path: logPath,
      });
      // Token not domain-scoped / insufficient scope → inline "not permitted".
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseFlatEnvelopeError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_degraded`, {
        requestId,
        status: 503,
        code: e.code,
        path: logPath,
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
      // actionable (no crash). A stray 429 without a rate-limit policy lands
      // HERE (surfaced ApiError, no backoff — the honest per-domain difference).
      const e = await parseFlatEnvelopeError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_request_error`, {
        requestId,
        status: res.status,
        code: e.code,
        path: logPath,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info(`${logPrefix}_ok`, {
      requestId,
      status: res.status,
      path: logPath,
    });
    return { raw: parse(json), res };
  } catch (err) {
    if (
      err instanceof ApiError ||
      profile.rateLimit?.isRateLimited(err) ||
      profile.isUnavailable(err)
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn(`${logPrefix}_timeout`, {
        requestId,
        timeoutMs,
        path: logPath,
      });
      throw profile.makeUnavailable('timeout', 'TIMEOUT', profile.messages.timeout);
    }
    logger.error(`${logPrefix}_error`, { requestId, path: logPath });
    throw profile.makeUnavailable(
      'downstream',
      'NETWORK_ERROR',
      profile.messages.network,
    );
  }
}
