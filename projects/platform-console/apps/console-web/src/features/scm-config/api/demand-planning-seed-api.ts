import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import {
  ApiError,
  ScmReplenishmentUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import {
  ReorderPolicySchema,
  type ReorderPolicy,
  type ReorderPolicyInput,
  SupplierMapSchema,
  type SupplierMap,
  type SupplierMapInput,
  type SeedLookup,
} from './types';

/**
 * Server-side scm `demand-planning-service` **seed/config** client
 * (TASK-PC-FE-080 — the per-SKU reorder-policy + sku-supplier-map inspect/upsert
 * surface; the operator config arm of the ADR-MONO-027 wms→scm replenishment
 * loop). It is the operational fix-path for FE-077's `SKU_SUPPLIER_UNMAPPED`
 * gap: the operator inspects + upserts the seed rows that drive FUTURE reorder
 * evaluation, then returns to 보충 and approves.
 *
 * Server-only by construction (same posture as
 * `scm-replenishment/api/demand-planning-api.ts`): imported exclusively from
 * server components and the `runtime = 'nodejs'` route handlers. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/scm/demand-planning/{policies,sku-supplier-map}/[skuCode]` proxy routes,
 * which attach the HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of the § 2.4.6 / § 2.4.6.1 rule (NOT
 *    re-derived; the EXACT primitive FE-077 uses) ──────────────────────────
 *
 * scm demand-planning REUSES the scm credential rule verbatim: the scm gateway
 * validates a IAM RS256 JWT against IAM JWKS, `tenant_id ∈ { scm, * }` enforced
 * producer-side from the JWT claim. scm has NO token-exchange. This client uses
 * `getDomainFacingToken()` and NEVER `getOperatorToken()` — exactly like
 * `demand-planning-api.ts`. A test pins this (the `getOperatorToken` path MUST
 * be ABSENT for scm, on GET AND PUT). The console sends NO `X-Tenant-Id` (scm
 * resolves the tenant from the JWT claim; cross-tenant → `403 TENANT_FORBIDDEN`
 * producer-side).
 *
 * ── MUTATION DISCIPLINE (the net-new part — § 2.4.6.2; follows what
 *    demand-planning-api.md ACTUALLY defines) ────────────────────────────────
 *
 * PUT is an idempotent **upsert** — the request body IS the FULL row. There is
 * NO `Idempotency-Key` header and NO `X-Operator-Reason` header (the producer
 * defines NEITHER; inventing them is a defect — a test asserts BOTH absent on
 * PUT). The config edit affects FUTURE evaluation only — this client issues NO
 * suggestion / PO / dispatch call (only policies + sku-supplier-map GET/PUT).
 *
 * ── 404-AS-EMPTY-STATE (the net-new resilience nuance) ────────────────────
 *
 * A GET 404 (`POLICY_NOT_FOUND` / `MAPPING_NOT_FOUND`) is NOT an error — it is
 * "not configured yet, create via PUT". `getPolicy` / `getSupplierMap` surface
 * it as a typed `{ found: false }` result, NEVER a thrown error (a test pins
 * that the 404 never propagates as an ApiError). A PUT 404 is not part of the
 * contract (PUT upserts) and is left to the generic inline-error path.
 *
 * Error envelope (§ 2.4.6.2 / § 2.5): the scm FLAT shape
 * `{ code, message, details?, timestamp }` — DISTINCT from wms's NESTED
 * `{ error: { code … } }`. `parseScmError()` reads the scm flat shape (and
 * tolerates an absent/non-JSON body without crashing).
 *
 * Resilience (§ 2.5): AbortController hard timeout; `401` → `ApiError` (forced
 * WHOLE-SESSION re-login); `403` → `ApiError` (inline "not scoped");
 * `422 VALIDATION_ERROR` → `ApiError` (inline field errors);
 * `429 RATE_LIMIT_EXCEEDED` → `ScmRateLimitedError` (ONE bounded backoff
 * honouring `Retry-After`, reused from the § 2.4.6 read surface — NO storm);
 * `503`/timeout/network → `ScmReplenishmentUnavailableError` (ONLY this section
 * degrades). Tokens / PII never logged.
 */

interface CallOptions {
  method: 'GET' | 'PUT';
  /** Path relative to `${SCM_GATEWAY_BASE_URL}`. */
  path: string;
  /** PUT upsert body (the FULL row); `undefined` for a GET. */
  body?: unknown;
  /** When true, a `404` resolves to a typed not-found rather than throwing
   *  (the 404-as-empty-state seed-lookup discipline). */
  notFoundIsEmpty?: boolean;
}

/** The bounded 429 backoff: at most ONE retry honouring `Retry-After` (capped)
 *  — reused from the § 2.4.6 / § 2.4.6.1 scm read surface (the SAME rate-limited
 *  scm gateway). NEVER an unbounded storm. */
const MAX_RETRY_AFTER_SECONDS = 5;

function parseRetryAfter(res: Response): number {
  const raw = res.headers.get('Retry-After');
  const n = raw === null ? NaN : Number(raw);
  if (!Number.isFinite(n) || n <= 0) return 1; // contract default (1s)
  return Math.min(MAX_RETRY_AFTER_SECONDS, n);
}

/**
 * Parses the scm FLAT error envelope (`{ code, message, details?, timestamp }`).
 * Defensive: a missing / nested (wms-shaped) / non-JSON body degrades to a
 * synthetic code rather than throwing. A wms-nested parser would MISS the scm
 * flat `code` — this is the per-domain envelope correctness pinned by tests.
 */
async function parseScmError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `scm demand-planning seed request failed (${res.status})`;
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

/** Sentinel parse return for the 404-as-empty-state path (distinct from a real
 *  parsed row). */
const NOT_FOUND = Symbol('seed-not-found');

/**
 * Single hardened call site. Resolves the domain-facing IAM OIDC token, applies
 * the timeout, maps the scm FLAT error envelope to the § 2.5 resilience
 * taxonomy, honours a 429 `Retry-After` with ONE bounded backoff, and — when
 * `notFoundIsEmpty` — short-circuits a `404` to the {@link NOT_FOUND} sentinel
 * (a "not configured yet" state, NOT an error).
 */
async function callSeed<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T | typeof NOT_FOUND> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential (§ 2.4.6.2 reusing § 2.4.6): scm requires the IAM
  //    OIDC ACCESS token directly. NEVER getOperatorToken(). Same credential
  //    for the GET inspect AND the PUT upsert.
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('scm_config_no_gap_session', { requestId, path: opts.path });
    // No IAM OIDC session ⇒ whole-session re-login (not a per-section degrade).
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // NOTE: deliberately NO `X-Tenant-Id` — scm resolves tenant from the JWT
    // `tenant_id` claim (§ 2.4.6.2 reuse of the § 2.4.6 divergence).
    // NOTE: NO `Idempotency-Key`, NO `X-Operator-Reason` — demand-planning-api
    // defines NEITHER for the seed PUT (the body IS the full row; idempotent
    // upsert). Inventing either is a defect.
  };
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  async function doFetch(): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), env.SCM_TIMEOUT_MS);
    try {
      return await fetch(`${env.SCM_GATEWAY_BASE_URL}${opts.path}`, {
        method: opts.method,
        headers,
        body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
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
      logger.warn('scm_config_rate_limited', {
        requestId,
        path: opts.path,
        retryAfterSeconds: wait,
      });
      await new Promise((r) => setTimeout(r, wait * 1000));
      res = await doFetch();
      if (res.status === 429) {
        // A second 429 — DO NOT retry again (no storm).
        throw new ScmRateLimitedError(
          parseRetryAfter(res),
          'scm gateway rate-limited',
        );
      }
    }

    // 404-as-empty-state — ONLY for the seed-lookup GETs. The seed row simply
    // is not configured yet → a typed not-found, NOT an error toast.
    if (res.status === 404 && opts.notFoundIsEmpty) {
      logger.info('scm_config_not_configured', {
        requestId,
        path: opts.path,
      });
      // Drain the body to avoid a leaked stream; we do not need its content.
      try {
        await res.json();
      } catch {
        /* ignore */
      }
      return NOT_FOUND;
    }

    if (res.status === 401) {
      const e = await parseScmError(res);
      logger.warn('scm_config_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseScmError(res);
      logger.warn('scm_config_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseScmError(res);
      logger.warn('scm_config_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      throw new ScmReplenishmentUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'scm demand-planning unavailable',
      );
    }

    if (!res.ok) {
      // 400/422 VALIDATION_ERROR, a non-seed-lookup 404, etc. → inline
      // actionable (no crash). The caller renders the inline field error.
      const e = await parseScmError(res);
      logger.warn('scm_config_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info('scm_config_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });
    return parse(json);
  } catch (err) {
    if (
      err instanceof ApiError ||
      err instanceof ScmReplenishmentUnavailableError ||
      err instanceof ScmRateLimitedError
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('scm_config_timeout', {
        requestId,
        timeoutMs: env.SCM_TIMEOUT_MS,
        path: opts.path,
      });
      throw new ScmReplenishmentUnavailableError(
        'timeout',
        'TIMEOUT',
        'scm demand-planning seed call timed out',
      );
    }
    logger.error('scm_config_error', { requestId, path: opts.path });
    throw new ScmReplenishmentUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'scm demand-planning seed call failed',
    );
  }
}

function unwrap<T>(value: T | typeof NOT_FOUND, kind: string): T {
  if (value === NOT_FOUND) {
    // A PUT (upsert) must never see a not-found sentinel (notFoundIsEmpty is
    // GET-only); this is a defensive guard, never reached on the happy path.
    throw new ApiError(404, kind, 'unexpected not-found on upsert');
  }
  return value;
}

// ===========================================================================
// reorder policy — GET (inspect; 404 = not configured yet) + PUT (upsert)
// ===========================================================================

/** GET /api/v1/demand-planning/policies/{skuCode}. 404 POLICY_NOT_FOUND is a
 *  typed `{ found: false }` (not configured yet), NOT a thrown error. */
export async function getPolicy(
  skuCode: string,
): Promise<SeedLookup<ReorderPolicy>> {
  const result = await callSeed(
    {
      method: 'GET',
      path: `/api/v1/demand-planning/policies/${encodeURIComponent(skuCode)}`,
      notFoundIsEmpty: true,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return ReorderPolicySchema.parse(env.data ?? json);
    },
  );
  if (result === NOT_FOUND) return { found: false };
  return { found: true, value: result };
}

/** PUT /api/v1/demand-planning/policies/{skuCode} — idempotent upsert. The
 *  body IS the FULL row. NO Idempotency-Key, NO X-Operator-Reason. */
export async function putPolicy(
  skuCode: string,
  body: ReorderPolicyInput,
): Promise<ReorderPolicy> {
  const result = await callSeed(
    {
      method: 'PUT',
      path: `/api/v1/demand-planning/policies/${encodeURIComponent(skuCode)}`,
      body,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return ReorderPolicySchema.parse(env.data ?? json);
    },
  );
  return unwrap(result, 'POLICY_NOT_FOUND');
}

// ===========================================================================
// sku→supplier mapping — GET (inspect; 404 = not configured yet) + PUT (upsert)
// ===========================================================================

/** GET /api/v1/demand-planning/sku-supplier-map/{skuCode}. 404 MAPPING_NOT_FOUND
 *  is a typed `{ found: false }` (not configured yet), NOT a thrown error. */
export async function getSupplierMap(
  skuCode: string,
): Promise<SeedLookup<SupplierMap>> {
  const result = await callSeed(
    {
      method: 'GET',
      path: `/api/v1/demand-planning/sku-supplier-map/${encodeURIComponent(skuCode)}`,
      notFoundIsEmpty: true,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return SupplierMapSchema.parse(env.data ?? json);
    },
  );
  if (result === NOT_FOUND) return { found: false };
  return { found: true, value: result };
}

/** PUT /api/v1/demand-planning/sku-supplier-map/{skuCode} — idempotent upsert.
 *  The body IS the FULL row. `supplierId` is free-text/uuid (no supplier master
 *  in v1). NO Idempotency-Key, NO X-Operator-Reason. */
export async function putSupplierMap(
  skuCode: string,
  body: SupplierMapInput,
): Promise<SupplierMap> {
  const result = await callSeed(
    {
      method: 'PUT',
      path: `/api/v1/demand-planning/sku-supplier-map/${encodeURIComponent(skuCode)}`,
      body,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return SupplierMapSchema.parse(env.data ?? json);
    },
  );
  return unwrap(result, 'MAPPING_NOT_FOUND');
}
