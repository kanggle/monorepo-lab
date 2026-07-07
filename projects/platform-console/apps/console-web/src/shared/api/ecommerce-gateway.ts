import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError } from '@/shared/api/errors';

/**
 * Shared server-side **ecommerce gateway HTTP core** (TASK-PC-FE-213 — promotion
 * of the ecommerce-ops feature-internal call core to `shared/api`, completing
 * the scm/wms/iam alignment). The eight ecommerce-ops `*-api.ts` slices
 * (products / orders / users / promotions / shippings / notifications / sellers
 * / images) were mirror-built and each carried a ~95%-identical `call<Slice>()`
 * + `parse<Slice>Error()`; TASK-PC-FE-094 hoisted the one true copy into the
 * feature-internal `ecommerce-ops/api/ecommerce-client.ts`. This module promotes
 * that hardened call site into the SHARED `shared/api/**` directory (the same
 * location + naming as {@link import('./scm-gateway')} / `wms-gateway`), and
 * aligns the abstraction to the `*GatewayProfile` shape: the section-degrade
 * error class is now INJECTED via {@link EcommerceGatewayProfile.makeUnavailable}
 * / {@link EcommerceGatewayProfile.isUnavailable} (SCM/WMS-homomorphic) rather
 * than hard-coded in the core.
 *
 * Server-only by construction (same posture as `scm-gateway.ts` and every
 * feature client that used it): imported exclusively from server components and
 * the `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The token + any data never reach client JS — client
 * components call the same-origin `/api/ecommerce/**` proxy routes, which attach
 * the HttpOnly credential here server-side.
 *
 * ── INVARIANTS PRESERVED VERBATIM (the eight slices' contract; pinned by
 *    `tests/unit/ecommerce-{products,orders,users,promotions,shippings,
 *    notifications,sellers,images}-api.test.ts`) ────────────────────────────
 *
 * - **Per-domain credential** (console-integration-contract § 2.4.10): the
 *   DOMAIN-FACING IAM OIDC token (`getDomainFacingToken()` — assumed-when-
 *   switched, else base; ADR-MONO-020 D4). NEVER `getOperatorToken()` (that is
 *   the IAM `/api/admin/**` exchanged credential; the ecommerce gateway would
 *   reject it — the #569 invariant is GAP-domain-scoped).
 * - **Tenant model**: NO `X-Tenant-Id` — ecommerce resolves the tenant from the
 *   JWT `tenant_id ∈ {ecommerce,*}` claim (the gateway `TenantClaimValidator`
 *   injects the trusted header). **NO `Idempotency-Key`** (the producer defines
 *   none — § 2.4.10).
 * - **ecommerce FLAT error envelope** `{ code, message, timestamp }` (the shared
 *   `ErrorResponse.of` — DISTINCT from wms's NESTED `{ error: { code } }`); a
 *   missing / non-JSON body degrades to a synthetic code, never throws
 *   ({@link parseEcommerceError}).
 * - **Resilience** (§ 2.5): AbortController hard timeout (`ECOMMERCE_TIMEOUT_MS`);
 *   `401`/`403` → {@link ApiError} (whole-session re-login for 401 / inline for
 *   403); `!ok` → {@link ApiError} (400/404/422/409 inline actionable); `503` /
 *   timeout / network → the profile's section-degrade error. **NO 429 backoff
 *   and NO 404-as-empty sentinel** — those are scm-only features (§ 2.4.6); the
 *   ecommerce producer defines neither, so the core carries neither.
 * - **Read-only vs mutation**: `Content-Type` is attached ONLY when a `body` is
 *   present. A void mutation / `204 No Content` (DELETE, seller lifecycle) is a
 *   `parse === undefined` call → `undefined as T`.
 *
 * Logging: structured, server-side only; the IAM token + any ecommerce data are
 * NEVER logged. Event names carry the profile prefix — the products slice uses
 * the bare `ecommerce_*` prefix (EMPTY infix), every other slice uses
 * `ecommerce_<event>_*` — so the per-section operational signals are unchanged.
 */

/** An ecommerce gateway request. */
export interface EcommerceGatewayRequest {
  /** HTTP method (every slice passes it explicitly — reads GET, mutations
   *  POST/PATCH/PUT/DELETE). */
  method: string;
  /** Absolute base (admin `ECOMMERCE_ADMIN_BASE_URL` or public
   *  `ECOMMERCE_PUBLIC_BASE_URL` subtree, resolved by the caller PER CALL —
   *  the ecommerce-only divergence from scm's single env base). */
  base: string;
  /** Path relative to `base`. */
  path: string;
  /** Typed mutation body; when present, `Content-Type: application/json` is
   *  attached and the body is JSON-serialised. Reads / DELETE omit it (no
   *  `Content-Type` — pinned). */
  body?: unknown;
}

/**
 * Per-slice behaviour the shared core is parameterised by — the ONLY thing that
 * differs between the eight ecommerce slices. Structurally homomorphic to
 * {@link import('./scm-gateway').ScmGatewayProfile} (`makeUnavailable` /
 * `isUnavailable` factory injection); the ecommerce-only per-call `base` rides
 * in {@link EcommerceGatewayRequest} instead of a single env base.
 */
export interface EcommerceGatewayProfile {
  /** Log-event prefix. Reproduces the ecommerce event-infix nuance: the
   *  products slice uses `ecommerce` (bare — `ecommerce_ok` /
   *  `ecommerce_unauthorized` / …), every other slice uses `ecommerce_<event>`
   *  (`ecommerce_order_ok` / …). The core emits `${logPrefix}_<suffix>`. */
  logPrefix: string;
  /** Synthetic default label for {@link parseEcommerceError} (body-less errors);
   *  the status is appended, e.g. `'ecommerce product request failed'` →
   *  `ecommerce product request failed (503)`. */
  requestFailedLabel: string;
  /** Build the section-degrade error (`503` / timeout / network) — the
   *  {@link ApiError}-sibling `EcommerceUnavailableError` so ONLY the ecommerce
   *  console section degrades. */
  makeUnavailable: (
    reason: 'timeout' | 'circuit_open' | 'downstream',
    code: string,
    message: string,
  ) => Error;
  /** `instanceof` guard for the profile's degrade error, so the `catch`
   *  re-throws it unchanged instead of re-wrapping (matches the original core's
   *  `err instanceof EcommerceUnavailableError` guard). */
  isUnavailable: (err: unknown) => boolean;
  /** Degrade / timeout / network `message` strings (preserved per slice). */
  messages: { degraded: string; timeout: string; network: string };
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }`).
 * Defensive: a missing / non-JSON body degrades to a synthetic code rather
 * than throwing (the producer is the authority for the real code; this never
 * crashes the console on a malformed error body). `failLabel` is the synthetic
 * default used only when the body carries no `message` — the status is appended
 * (`<label> (<status>)`), reproducing each slice's original default verbatim.
 */
export async function parseEcommerceError(
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
 * The single hardened ecommerce gateway call site (promoted from the eight
 * feature slices). Resolves the domain-facing IAM OIDC token, applies the
 * timeout, and maps the ecommerce flat error envelope to the § 2.5 resilience
 * taxonomy. `parse` is `undefined` for a void mutation / `204 No Content`
 * (DELETE) — the core returns `undefined as T`.
 */
export async function callEcommerceGateway<T>(
  req: EcommerceGatewayRequest,
  parse: ((json: unknown) => T) | undefined,
  profile: EcommerceGatewayProfile,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();
  const { logPrefix } = profile;

  // Per-domain credential selection (§ 2.4.10): the ecommerce gateway requires
  // the IAM OIDC token (account_type=OPERATOR). NEVER getOperatorToken() — that
  // is the IAM (§ 2.6 exchanged) credential; ecommerce would reject it.
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn(`${logPrefix}_no_gap_session`, {
      requestId,
      path: req.path,
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
  if (req.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ECOMMERCE_TIMEOUT_MS);

  try {
    const res = await fetch(`${req.base}${req.path}`, {
      method: req.method,
      headers,
      body: req.body === undefined ? undefined : JSON.stringify(req.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseEcommerceError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_unauthorized`, {
        requestId,
        status: 401,
        code: e.code,
        path: req.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseEcommerceError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_forbidden`, {
        requestId,
        status: 403,
        code: e.code,
        path: req.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseEcommerceError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_degraded`, {
        requestId,
        status: 503,
        code: e.code,
        path: req.path,
      });
      // ONLY the ecommerce section degrades — shell + other sections intact.
      throw profile.makeUnavailable(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        profile.messages.degraded,
      );
    }

    if (!res.ok) {
      // 4xx producer codes → inline actionable (no crash).
      const e = await parseEcommerceError(res, profile.requestFailedLabel);
      logger.warn(`${logPrefix}_request_error`, {
        requestId,
        status: res.status,
        code: e.code,
        path: req.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info(`${logPrefix}_ok`, {
      requestId,
      status: res.status,
      path: req.path,
    });

    // 204 No Content (DELETE) / void mutation — nothing to parse.
    if (res.status === 204 || parse === undefined) {
      return undefined as T;
    }
    const json = await res.json();
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || profile.isUnavailable(err)) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn(`${logPrefix}_timeout`, {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: req.path,
      });
      throw profile.makeUnavailable(
        'timeout',
        'TIMEOUT',
        profile.messages.timeout,
      );
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
