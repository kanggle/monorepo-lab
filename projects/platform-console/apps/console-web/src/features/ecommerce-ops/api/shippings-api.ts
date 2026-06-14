import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import {
  ShippingListSchema,
  type ShippingList,
  ShippingSchema,
  type Shipping,
  type ShippingListParams,
  type UpdateShippingStatusBody,
  SHIPPING_DEFAULT_PAGE_SIZE,
  SHIPPING_MAX_PAGE_SIZE,
} from './shipping-types';

/**
 * Server-side ecommerce `shipping-service` operations client (TASK-PC-FE-088 —
 * ADR-MONO-031 Phase 4b). Drives the in-console shipping operator surface:
 * list / status-change / refresh-tracking.
 *
 * Server-only by construction: imported exclusively from server components and
 * the `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The token + any data never reach client JS — client components
 * call the same-origin `/api/ecommerce/shippings/**` proxy routes, which attach
 * the HttpOnly credential here server-side.
 *
 * ── BASE URL RESOLUTION (shipping-service path divergence) ─────────────────
 *
 * shipping-service exposes endpoints at `/api/shippings/**` — the **non-admin**
 * path (same model as promotions, NOT the `/api/admin/**` subtree). Therefore
 * this client uses `ECOMMERCE_PUBLIC_BASE_URL` (defaults to
 * `http://ecommerce.local/api`) with path `/shippings/**`, yielding:
 *   `http://ecommerce.local/api/shippings`
 *
 * Contrast with orders-api, which uses `ECOMMERCE_ADMIN_BASE_URL`
 * (`http://ecommerce.local/api/admin`) + `/orders` for the admin subtree.
 * Shippings NEVER hit `/api/admin/**`; the `ECOMMERCE_PUBLIC_BASE_URL` base
 * is the correct root (matches `console-integration-contract.md` § 2.4.10.3).
 *
 * ── AUTH MODEL (identical to promotions-api — § 2.4.10) ─────────────────────
 *
 * Uses `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC token or
 * the base access token — net-zero; ADR-MONO-020 D4). NEVER `getOperatorToken()`
 * (that is the IAM-domain credential — wrong issuer/type for ecommerce).
 * Tenant rides in the JWT `tenant_id` claim — the console sends NO `X-Tenant-Id`.
 * NO `Idempotency-Key` (producer defines none — § 2.4.10).
 *
 * ── ERROR ENVELOPE (flat { code, message, timestamp } — same as promotions) ──
 *
 * Producer codes: 400 InvalidShipping (SHIPPED without carrier/tracking),
 * 400 INVALID_STATUS (illegal transition), 404 SHIPPING_NOT_FOUND,
 * 409/422 INVALID_TRANSITION (non-linear jump attempt).
 *
 * ── RESILIENCE (§ 2.5) ───────────────────────────────────────────────────────
 *   - `401` → `ApiError(401)` (whole-session re-login).
 *   - `403` → `ApiError(403)` (inline "not available to your role").
 *   - `404`/`400`/`422`/`409` → `ApiError` (inline actionable, no crash).
 *   - `503`/timeout/network → `EcommerceUnavailableError` (section degrades only).
 */

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

interface CallOptions {
  method: Method;
  base: string;
  path: string;
  body?: unknown;
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }`).
 * Defensive: a missing / non-JSON body degrades to a synthetic code rather
 * than throwing (the producer is the authority for the real code).
 */
async function parseShippingError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce shipping request failed (${res.status})`;
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
 * Single hardened call site. Resolves the domain-facing IAM OIDC token,
 * applies the timeout, and maps the ecommerce flat error envelope to the
 * § 2.5 resilience taxonomy.
 */
async function callShipping<T>(
  opts: CallOptions,
  parse?: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Per-domain credential selection (§ 2.4.10): use getDomainFacingToken(),
  // NEVER getOperatorToken() (the ecommerce gateway requires the IAM OIDC
  // token; the #569 invariant is GAP-domain-scoped).
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('ecommerce_shipping_no_gap_session', {
      requestId,
      path: opts.path,
    });
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
  };
  // NO `X-Tenant-Id` — ecommerce resolves tenant from the JWT `tenant_id`
  // claim (gateway-injected; § 2.4.10 tenant invariant).
  // NO `Idempotency-Key` — the producer defines none (§ 2.4.10).
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.ECOMMERCE_TIMEOUT_MS,
  );

  try {
    const res = await fetch(`${opts.base}${opts.path}`, {
      method: opts.method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseShippingError(res);
      logger.warn('ecommerce_shipping_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseShippingError(res);
      logger.warn('ecommerce_shipping_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseShippingError(res);
      logger.warn('ecommerce_shipping_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ecommerce shipping-service unavailable',
      );
    }

    if (!res.ok) {
      // 400 InvalidShipping (SHIPPED without carrier/tracking),
      // 400 INVALID_STATUS (illegal transition attempt),
      // 404 SHIPPING_NOT_FOUND,
      // 409/422 INVALID_TRANSITION
      // → inline actionable (no crash).
      const e = await parseShippingError(res);
      logger.warn('ecommerce_shipping_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info('ecommerce_shipping_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });

    if (parse === undefined) {
      return undefined as T;
    }
    const json = await res.json();
    return parse(json);
  } catch (err) {
    if (
      err instanceof ApiError ||
      err instanceof EcommerceUnavailableError
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('ecommerce_shipping_timeout', {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        'ecommerce shipping-service call timed out',
      );
    }
    logger.error('ecommerce_shipping_error', { requestId, path: opts.path });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ecommerce shipping-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

function clampSize(size?: number): number {
  return Math.min(
    SHIPPING_MAX_PAGE_SIZE,
    Math.max(1, size ?? SHIPPING_DEFAULT_PAGE_SIZE),
  );
}

// ===========================================================================
// READS
// ===========================================================================

/**
 * GET /api/shippings?page=&size=&status= (paginated list, optional status filter).
 * Operator surface — requires IAM OIDC token with OPERATOR role claim.
 */
export function listShippings(
  params: ShippingListParams = {},
): Promise<ShippingList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callShipping(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/shippings?${qs.toString()}`,
    },
    (j) => ShippingListSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (confirm-gated in the UI; NO Idempotency-Key; state-guard-dependent)
// ===========================================================================

/**
 * PUT /api/shippings/{shippingId}/status — linear status transition.
 * Body: `{ status, trackingNumber?, carrier? }`.
 * `trackingNumber` + `carrier` are REQUIRED when `status=SHIPPED`.
 * Producer rejects SHIPPED without them (400 InvalidShipping).
 * Returns the updated Shipping resource.
 */
export function updateShippingStatus(
  id: string,
  body: UpdateShippingStatusBody,
): Promise<Shipping> {
  const env = getServerEnv();
  return callShipping(
    {
      method: 'PUT',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/shippings/${encodeURIComponent(id)}/status`,
      body,
    },
    (j) => ShippingSchema.parse(j),
  );
}

/**
 * POST /api/shippings/{shippingId}/refresh-tracking — operator-triggered carrier sync.
 * Empty body. Best-effort: returns 200 with the (possibly unchanged) shipment.
 * When the carrier mode is mock or carrier is unreachable, the status is
 * unchanged (no error surfaced — best-effort per spec).
 */
export function refreshTracking(id: string): Promise<Shipping> {
  const env = getServerEnv();
  return callShipping(
    {
      method: 'POST',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/shippings/${encodeURIComponent(id)}/refresh-tracking`,
      body: {},
    },
    (j) => ShippingSchema.parse(j),
  );
}
