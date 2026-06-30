import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  ApiError,
  ScmUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import { SCM_DEFAULT_PAGE_SIZE, SCM_MAX_PAGE_SIZE } from './types';

/**
 * Server-side scm gateway operations HTTP core (TASK-PC-FE-008 —
 * ADR-MONO-013 Phase 4 slice 2, the SECOND non-IAM federated domain;
 * completes Phase 4). STRICTLY READ-ONLY.
 *
 * ── MODULE SPLIT (TASK-PC-FE-145) ── this leaf holds the hardened
 * `callScm` call site, the scm FLAT error-envelope parser, the 429
 * `Retry-After` / `X-Cache` header readers, and the page-param helper.
 * The endpoint groups (`scm-procurement-api.ts` /
 * `scm-inventory-visibility-api.ts`) import this leaf; the
 * `scm-api.ts` barrel re-exports ONLY the endpoint functions (the public
 * surface — the `scm-api.test.ts` `Object.keys` guard pins exactly the
 * 6 endpoint exports, so this core stays barrel-internal). Pure
 * structural split — 0 behavior / contract / log-event change.
 *
 * Server-only by construction (same posture as `wms-api.ts` /
 * `accounts-api.ts`): imported exclusively from server components and the
 * `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The token + any data never reach client JS — client
 * components call the same-origin `/api/scm/**` proxy routes, which attach
 * the HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of the § 2.4.5 rule (NOT re-derived) ──
 *
 * The normative per-domain credential rule is DEFINED in
 * console-integration-contract § 2.4.5 (each binding declares its own
 * credential against its producer; never blanket-apply one domain's auth
 * to another). scm REUSES it with the SAME outcome as wms: the scm gateway
 * validates a IAM RS256 JWT (ADR-001) against IAM JWKS,
 * `tenant_id ∈ { scm, * }` enforced producer-side from the JWT claim (scm
 * `gateway-public-routes.md` § platform-console operator read consumer —
 * TASK-SCM-BE-015). scm has NO token-exchange.
 *
 * Therefore this client uses `getAccessToken()` (the GAP-session HttpOnly
 * cookie) and NEVER `getOperatorToken()` — exactly like `wms-api.ts`, and
 * the EXACT INVERSE of the IAM `features/{accounts,audit,operators,
 * dashboards}` clients. The #569 trust-boundary invariant is
 * GAP-domain-scoped and does NOT generalise to scm. A test pins this (the
 * `getOperatorToken` path MUST be absent for scm; the cross-domain
 * regression covers GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC).
 *
 * Tenant invariant (§ 2.4.6 / reuse of § 2.4.5): scm resolves the tenant
 * from the JWT `tenant_id` claim (`∈ {scm,*}`) — NOT an `X-Tenant-Id`
 * header. The console therefore does NOT send `X-Tenant-Id` to scm; the
 * tenant rides inside the IAM OIDC token. scm rejects cross-tenant
 * producer-side (`403 TENANT_FORBIDDEN`).
 *
 * READ-ONLY (§ 2.4.6, NORMATIVE): every call is a pure GET. There is NO
 * mutation anywhere — NO `Idempotency-Key`, NO `X-Operator-Reason`, NO
 * body, NO PO write (`/submit|/confirm|/cancel`), NO webhook. Carrying the
 * FE-007 alert-ack OR the IAM § 2.4.1 mutation scaffolding here is a
 * defect (tests assert their absence).
 *
 * Error envelope (§ 2.4.6 / § 2.5): scm uses the FLAT shape
 * `{ code, message, details?, timestamp }` — DISTINCT from wms's NESTED
 * `{ error: { code … } }` and not assumed-identical to GAP's.
 * `parseScmError()` reads the scm flat shape (and tolerates an
 * absent/non-JSON body without crashing).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout
 * (no unbounded default); `401` → `ApiError` (forced WHOLE-SESSION GAP
 * re-login — not a per-section degrade); `403` → `ApiError` (inline "not
 * available / not scoped"); `404`/`400`/`422` → `ApiError` (inline
 * actionable, no crash); `429 RATE_LIMIT_EXCEEDED` → `ScmRateLimitedError`
 * (ONE bounded backoff honouring `Retry-After`, then surface — NO retry
 * storm); `503`/timeout/network → `ScmUnavailableError` (ONLY the scm
 * section degrades — shell + GAP/wms sections intact).
 *
 * Freshness honesty (§ 2.4.6): the per-SKU read's `X-Cache` header
 * (HIT|MISS|UNAVAILABLE) is surfaced on the result (`cache`); the
 * `/staleness` per-node status is surfaced as-is by the UI. The S5
 * `meta.warning` is a REQUIRED, surfaced field of every
 * inventory-visibility view-model (never stripped — enforced in
 * `types.ts`).
 *
 * Logging: structured, server-side only; the IAM access token and any scm
 * data are NEVER logged (redacted) — § 2.6 logging invariant extended.
 */

interface CallOptions {
  /** Path relative to `${SCM_GATEWAY_BASE_URL}` (e.g.
   *  `/api/v1/procurement/po`). */
  path: string;
}

/** The bounded 429 backoff: at most ONE retry honouring `Retry-After`
 *  (capped) — NEVER an unbounded retry storm into the rate-limited
 *  gateway (§ 2.4.6 / task Edge Case). */
const MAX_RETRY_AFTER_SECONDS = 5;

function parseRetryAfter(res: Response): number {
  const raw = res.headers.get('Retry-After');
  const n = raw === null ? NaN : Number(raw);
  if (!Number.isFinite(n) || n <= 0) return 1; // contract default (1s)
  return Math.min(MAX_RETRY_AFTER_SECONDS, n);
}

export function readCacheHeader(
  res: Response,
): 'HIT' | 'MISS' | 'UNAVAILABLE' | null {
  const raw = res.headers.get('X-Cache');
  if (raw === 'HIT' || raw === 'MISS' || raw === 'UNAVAILABLE') return raw;
  return null;
}

/**
 * Parses the scm FLAT error envelope
 * (`{ code, message, details?, timestamp }`). Defensive: a missing /
 * nested (wms-shaped) / non-JSON body degrades to a synthetic code rather
 * than throwing (the producer is the authority for the real code; this
 * never crashes the console on a malformed error body). A wms-nested
 * parser would MISS the scm flat `code` — this is the per-domain envelope
 * correctness pinned by tests.
 */
async function parseScmError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `scm request failed (${res.status})`;
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

/**
 * Single hardened call site. Resolves the IAM OIDC access token, applies
 * the timeout, maps the scm FLAT error envelope to the § 2.5 resilience
 * taxonomy, and honours a 429 `Retry-After` with ONE bounded backoff. The
 * `retried` guard makes the storm impossible (a second 429 is surfaced,
 * not retried again).
 */
export async function callScm<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<{ raw: T; res: Response }> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential (§ 2.4.6 reusing § 2.4.5): scm requires the
  //    IAM OIDC ACCESS token directly. NEVER getOperatorToken() — that is
  //    the GAP-domain (§ 2.6 exchanged) credential; scm would reject it
  //    (wrong issuer/type). The #569 invariant is GAP-domain-scoped.
  //    ── ADR-MONO-020 D4 / § 2.7: the DOMAIN-FACING IAM OIDC token — the
  //    ASSUMED (tenant-scoped) token when the operator has switched, else the
  //    base access token (net-zero). Still NOT the operator token.
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('scm_no_gap_session', { requestId, path: opts.path });
    // No IAM OIDC session ⇒ whole-session re-login (not a per-section
    // degrade — no partial authed state).
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // NOTE: deliberately NO `X-Tenant-Id` — scm resolves tenant from the
    // JWT `tenant_id` claim (§ 2.4.6 reuse of the § 2.4.5 divergence).
    // NOTE: read-only — NO `Idempotency-Key`, NO `X-Operator-Reason`,
    // NO `Content-Type` (no body).
  };

  async function doFetch(): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(
      () => controller.abort(),
      env.SCM_TIMEOUT_MS,
    );
    try {
      return await fetch(`${env.SCM_GATEWAY_BASE_URL}${opts.path}`, {
        method: 'GET',
        headers,
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
      const wait = parseRetryAfter(res);
      logger.warn('scm_rate_limited', {
        requestId,
        path: opts.path,
        retryAfterSeconds: wait,
      });
      await new Promise((r) => setTimeout(r, wait * 1000));
      res = await doFetch();
      if (res.status === 429) {
        // A second 429 — DO NOT retry again (no storm). Surface as a
        // bounded notice; the UI shows an inline "rate-limited" message.
        throw new ScmRateLimitedError(
          parseRetryAfter(res),
          'scm gateway rate-limited',
        );
      }
    }

    if (res.status === 401) {
      const e = await parseScmError(res);
      logger.warn('scm_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      // IAM OIDC session expired → whole-session re-login (no partial
      // authed state — NOT a per-section degrade).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseScmError(res);
      logger.warn('scm_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      // Token not scm-scoped / insufficient scope → inline "not available
      // / not scoped" (no crash, no re-login loop).
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseScmError(res);
      logger.warn('scm_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // ONLY the scm section degrades — shell + GAP/wms sections intact.
      throw new ScmUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'scm gateway unavailable',
      );
    }

    if (!res.ok) {
      // 400/422 VALIDATION_ERROR, 404 PO_NOT_FOUND / NODE_NOT_FOUND,
      // 409 CONFLICT → inline actionable (no crash).
      const e = await parseScmError(res);
      logger.warn('scm_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info('scm_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });
    return { raw: parse(json), res };
  } catch (err) {
    if (
      err instanceof ApiError ||
      err instanceof ScmUnavailableError ||
      err instanceof ScmRateLimitedError
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('scm_timeout', {
        requestId,
        timeoutMs: env.SCM_TIMEOUT_MS,
        path: opts.path,
      });
      throw new ScmUnavailableError(
        'timeout',
        'TIMEOUT',
        'scm gateway call timed out',
      );
    }
    logger.error('scm_error', { requestId, path: opts.path });
    throw new ScmUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'scm gateway call failed',
    );
  }
}

// ---------------------------------------------------------------------------
// pagination helper
// ---------------------------------------------------------------------------

export function pageParams(
  qs: URLSearchParams,
  page?: number,
  size?: number,
): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      clampPageSize(size, SCM_DEFAULT_PAGE_SIZE, SCM_MAX_PAGE_SIZE),
    ),
  );
}
