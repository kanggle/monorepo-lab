import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import {
  ApiError,
  ScmReplenishmentUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import {
  SuggestionPageSchema,
  type SuggestionPage,
  SuggestionSchema,
  type Suggestion,
  ApproveResultSchema,
  type ApproveResult,
  DismissResultSchema,
  type DismissResult,
  type SuggestionQueryParams,
  REPL_DEFAULT_PAGE_SIZE,
  REPL_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side scm `demand-planning-service` replenishment operator client
 * (TASK-PC-FE-077 — the FIRST scm operator-MUTATION surface, the human gate
 * of the ADR-MONO-027 wms→scm replenishment loop). Read (suggestions
 * list/detail) + two operator ACTIONS (approve / dismiss).
 *
 * Server-only by construction (same posture as `scm-ops/api/scm-api.ts`):
 * imported exclusively from server components and the `runtime = 'nodejs'`
 * route handlers; `getServerEnv()` throws outside the server runtime. The
 * token + any data never reach client JS — client components call the
 * same-origin `/api/scm/demand-planning/**` proxy routes, which attach the
 * HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of the § 2.4.5 / § 2.4.6 rule (NOT
 *    re-derived) ──────────────────────────────────────────────────────────
 *
 * scm demand-planning REUSES the § 2.4.6 scm credential rule verbatim: the
 * scm gateway validates a IAM RS256 JWT (ADR-001) against IAM JWKS,
 * `tenant_id ∈ { scm, * }` enforced producer-side from the JWT claim. scm has
 * NO token-exchange. This client therefore uses `getDomainFacingToken()` (the
 * assumed tenant-scoped IAM OIDC token when the operator switched, else the
 * base access token — net-zero; ADR-MONO-020 D4) and NEVER `getOperatorToken()`
 * — exactly like `scm-api.ts`, and the EXACT INVERSE of the IAM
 * `features/{accounts,audit,operators,dashboards}` clients. The #569
 * trust-boundary invariant is GAP-domain-scoped and does NOT generalise to scm.
 * A test pins this (the `getOperatorToken` path MUST be absent for scm).
 *
 * Tenant invariant (§ 2.4.6.1 / reuse of § 2.4.5/§ 2.4.6): scm resolves the
 * tenant from the JWT `tenant_id` claim (`∈ {scm,*}`) — NOT an `X-Tenant-Id`
 * header. The console does NOT send `X-Tenant-Id`; scm rejects cross-tenant
 * producer-side (`403 TENANT_FORBIDDEN`).
 *
 * ── MUTATION DISCIPLINE (the net-new part — § 2.4.6.1; follows what
 *    demand-planning-api.md ACTUALLY defines, NOT a cargo-cult of IAM
 *    § 2.4.1 mutation scaffolding) ──────────────────────────────────────────
 *
 * approve / dismiss are `POST` with an OPTIONAL JSON body (`{ note }` /
 * `{ reason }`). The producer is **server-side idempotent by suggestion
 * state** (re-approve returns the existing `poId`; re-dismiss is a no-op) — so
 * a client `Idempotency-Key` header is NOT required and is NOT attached, and
 * the reason rides in the BODY, NOT an `X-Operator-Reason` header (the
 * producer defines neither header — inventing them is a defect; a test asserts
 * BOTH absent). approve materialises a DRAFT PO only (ADR-MONO-027 D5); this
 * client NEVER issues a procurement submit/confirm/cancel.
 *
 * Error envelope (§ 2.4.6.1 / § 2.5): scm uses the FLAT shape
 * `{ code, message, details?, timestamp }` — DISTINCT from wms's NESTED
 * `{ error: { code … } }`. `parseScmError()` reads the scm flat shape (and
 * tolerates an absent/non-JSON body without crashing).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * `401` → `ApiError` (forced WHOLE-SESSION re-login); `403` → `ApiError`
 * (inline "not scoped"); `404`/`400`/`422`/`409` → `ApiError` (inline
 * actionable — the idempotent approve path is handled as success by the caller,
 * a hard `409 SUGGESTION_ALREADY_MATERIALIZED` as a benign notice);
 * `429 RATE_LIMIT_EXCEEDED` → `ScmRateLimitedError` (ONE bounded backoff
 * honouring `Retry-After`, reused from the § 2.4.6 read surface — NO storm);
 * `503`/timeout/network → `ScmReplenishmentUnavailableError` (ONLY this section
 * degrades).
 *
 * Logging: structured, server-side only; the IAM access token and any scm data
 * (incl. the note/reason) are NEVER logged (redacted).
 */

interface CallOptions {
  method: 'GET' | 'POST';
  /** Path relative to `${SCM_GATEWAY_BASE_URL}` (e.g.
   *  `/api/v1/demand-planning/suggestions`). */
  path: string;
  /** Optional mutation body (`{ note }` / `{ reason }`); `undefined` for
   *  reads AND for a no-note/no-reason action (the body is OPTIONAL). */
  body?: unknown;
}

/** The bounded 429 backoff: at most ONE retry honouring `Retry-After`
 *  (capped) — NEVER an unbounded retry storm into the rate-limited gateway
 *  (§ 2.4.6.1 reusing § 2.4.6 / task Edge Case). */
const MAX_RETRY_AFTER_SECONDS = 5;

function parseRetryAfter(res: Response): number {
  const raw = res.headers.get('Retry-After');
  const n = raw === null ? NaN : Number(raw);
  if (!Number.isFinite(n) || n <= 0) return 1; // contract default (1s)
  return Math.min(MAX_RETRY_AFTER_SECONDS, n);
}

/**
 * Parses the scm FLAT error envelope
 * (`{ code, message, details?, timestamp }`). Defensive: a missing / nested
 * (wms-shaped) / non-JSON body degrades to a synthetic code rather than
 * throwing (the producer is the authority for the real code; this never
 * crashes the console on a malformed error body). A wms-nested parser would
 * MISS the scm flat `code` — this is the per-domain envelope correctness
 * pinned by tests.
 */
async function parseScmError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `scm demand-planning request failed (${res.status})`;
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
 * Single hardened call site. Resolves the domain-facing IAM OIDC token,
 * applies the timeout, maps the scm FLAT error envelope to the § 2.5
 * resilience taxonomy, and honours a 429 `Retry-After` with ONE bounded
 * backoff. The `retried` guard makes the storm impossible.
 */
async function callDemandPlanning<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential (§ 2.4.6.1 reusing § 2.4.5/§ 2.4.6): scm requires
  //    the IAM OIDC ACCESS token directly. NEVER getOperatorToken() — that is
  //    the GAP-domain (§ 2.6 exchanged) credential; scm would reject it. The
  //    DOMAIN-FACING token (assumed-when-switched, else base — net-zero;
  //    ADR-MONO-020 D4). Same credential for the reads AND the two actions.
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('scm_replenishment_no_gap_session', {
      requestId,
      path: opts.path,
    });
    // No IAM OIDC session ⇒ whole-session re-login (not a per-section degrade).
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // NOTE: deliberately NO `X-Tenant-Id` — scm resolves tenant from the JWT
    // `tenant_id` claim (§ 2.4.6.1 reuse of the § 2.4.5/§ 2.4.6 divergence).
    // NOTE: NO `Idempotency-Key`, NO `X-Operator-Reason` — demand-planning-api
    // defines NEITHER (idempotency is server-side by suggestion state; the
    // reason rides in the OPTIONAL body). Inventing either is a defect.
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
      logger.warn('scm_replenishment_rate_limited', {
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

    if (res.status === 401) {
      const e = await parseScmError(res);
      logger.warn('scm_replenishment_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      // IAM OIDC session expired → whole-session re-login (no partial authed
      // state — NOT a per-section degrade).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseScmError(res);
      logger.warn('scm_replenishment_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      // Token not scm-scoped / insufficient scope → inline "not scoped".
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseScmError(res);
      logger.warn('scm_replenishment_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // ONLY the replenishment section degrades — shell + other sections intact.
      throw new ScmReplenishmentUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'scm demand-planning unavailable',
      );
    }

    if (!res.ok) {
      // 400/422 VALIDATION_ERROR, 422 SKU_SUPPLIER_UNMAPPED /
      // INVALID_SUGGESTION_STATE, 404 SUGGESTION_NOT_FOUND,
      // 409 SUGGESTION_ALREADY_MATERIALIZED → inline actionable (no crash).
      // The caller maps the idempotent paths (re-approve, already-materialized)
      // to a success / benign notice.
      const e = await parseScmError(res);
      logger.warn('scm_replenishment_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info('scm_replenishment_ok', {
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
      logger.warn('scm_replenishment_timeout', {
        requestId,
        timeoutMs: env.SCM_TIMEOUT_MS,
        path: opts.path,
      });
      throw new ScmReplenishmentUnavailableError(
        'timeout',
        'TIMEOUT',
        'scm demand-planning call timed out',
      );
    }
    logger.error('scm_replenishment_error', { requestId, path: opts.path });
    throw new ScmReplenishmentUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'scm demand-planning call failed',
    );
  }
}

// ---------------------------------------------------------------------------
// pagination helper
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(REPL_MAX_PAGE_SIZE, Math.max(1, size ?? REPL_DEFAULT_PAGE_SIZE)),
    ),
  );
}

// ===========================================================================
// READS (operator read — no mutation artifacts)
// ===========================================================================

/** GET /api/v1/demand-planning/suggestions — list, filter by status/skuCode,
 *  paginated. Envelope = { data: [...], meta: { page, size, totalElements,
 *  totalPages } } → normalised to a `{ content, page, size, totalElements,
 *  totalPages }` view-model. */
export async function listSuggestions(
  params: SuggestionQueryParams = {},
): Promise<SuggestionPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.skuCode) qs.set('skuCode', params.skuCode);
  pageParams(qs, params.page, params.size);
  return callDemandPlanning(
    {
      method: 'GET',
      path: `/api/v1/demand-planning/suggestions?${qs.toString()}`,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown; meta?: unknown };
      const content = Array.isArray(env.data) ? env.data : [];
      const meta = (env.meta ?? {}) as {
        page?: number;
        size?: number;
        totalElements?: number;
        totalPages?: number;
      };
      return SuggestionPageSchema.parse({
        content,
        page: typeof meta.page === 'number' ? meta.page : 0,
        size:
          typeof meta.size === 'number' && meta.size > 0
            ? meta.size
            : REPL_DEFAULT_PAGE_SIZE,
        totalElements:
          typeof meta.totalElements === 'number'
            ? meta.totalElements
            : content.length,
        totalPages:
          typeof meta.totalPages === 'number' ? meta.totalPages : undefined,
      });
    },
  );
}

/** GET /api/v1/demand-planning/suggestions/{id} — single suggestion. */
export async function getSuggestion(id: string): Promise<Suggestion> {
  return callDemandPlanning(
    {
      method: 'GET',
      path: `/api/v1/demand-planning/suggestions/${encodeURIComponent(id)}`,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return SuggestionSchema.parse(env.data ?? json);
    },
  );
}

// ===========================================================================
// OPERATOR ACTIONS (POST, optional body, server-side idempotent by state)
//   NO Idempotency-Key, NO X-Operator-Reason — the reason rides in the body.
// ===========================================================================

/** POST /api/v1/demand-planning/suggestions/{id}/approve — resolves
 *  sku_supplier_map → procurement creates a DRAFT PO → suggestion
 *  `MATERIALIZED` with `materializedPoId`. Idempotent: re-approving (or an
 *  already-`MATERIALIZED` suggestion) returns the existing `poId` — no
 *  duplicate PO. `note` is OPTIONAL and rides in the BODY (no header).
 *  The materialised PO is DRAFT ONLY — this client NEVER submits it. */
export async function approveSuggestion(
  id: string,
  note?: string,
): Promise<ApproveResult> {
  const trimmed = note?.trim();
  return callDemandPlanning(
    {
      method: 'POST',
      path: `/api/v1/demand-planning/suggestions/${encodeURIComponent(id)}/approve`,
      // OPTIONAL body — omitted entirely when there is no note (the producer
      // accepts an empty/absent body). NO Idempotency-Key, NO X-Operator-Reason.
      body: trimmed ? { note: trimmed } : undefined,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return ApproveResultSchema.parse(env.data ?? json);
    },
  );
}

/** POST /api/v1/demand-planning/suggestions/{id}/dismiss — `* → DISMISSED`,
 *  releases the open-suggestion guard. Idempotent (re-dismiss = no-op).
 *  `reason` is OPTIONAL and rides in the BODY (no header). */
export async function dismissSuggestion(
  id: string,
  reason?: string,
): Promise<DismissResult> {
  const trimmed = reason?.trim();
  return callDemandPlanning(
    {
      method: 'POST',
      path: `/api/v1/demand-planning/suggestions/${encodeURIComponent(id)}/dismiss`,
      body: trimmed ? { reason: trimmed } : undefined,
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return DismissResultSchema.parse(env.data ?? json);
    },
  );
}
