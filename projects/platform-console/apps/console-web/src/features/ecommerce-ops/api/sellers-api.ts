import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import {
  SellerListSchema,
  type SellerList,
  SellerDetailSchema,
  type SellerDetail,
  RegisterSellerResponseSchema,
  type RegisterSellerResponse,
  type RegisterSellerBody,
  type SellerListParams,
  SELLER_DEFAULT_PAGE_SIZE,
  SELLER_MAX_PAGE_SIZE,
} from './seller-types';

/**
 * Server-side ecommerce `product-service` seller operations client
 * (TASK-PC-FE-090 — ADR-MONO-031 § 2.4.10 7th area / ADR-MONO-030 Step 4 facet f).
 * Drives the in-console seller list, detail, and register screens.
 *
 * ── BASE URL RESOLUTION (admin subtree — distinct from promotions/notifications) ──
 *
 * Seller endpoints live under `/api/admin/sellers` (the admin path — same as
 * products/orders/users), NOT the `/api/**` public path that promotions/
 * notifications/shippings use. Therefore this client uses
 * `ECOMMERCE_ADMIN_BASE_URL` (`http://ecommerce.local/api/admin`) + `/sellers`.
 *
 * CRITICAL: using ECOMMERCE_PUBLIC_BASE_URL here would hit a 404 — the
 * `AdminSellerController` is registered under `/api/admin`, not `/api`.
 *
 * ── AUTH MODEL (§ 2.4.10 — identical to products-api / users-api) ────────────
 *
 * Uses `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC token or
 * the base access token — net-zero; ADR-MONO-020 D4). NEVER `getOperatorToken()`
 * (that is the IAM-domain credential — wrong issuer/type for ecommerce).
 * Tenant rides in the JWT `tenant_id` claim — the console sends NO `X-Tenant-Id`.
 * NO `Idempotency-Key` (producer defines none — § 2.4.10).
 *
 * ── ERROR ENVELOPE (flat { code, message, timestamp } — same as products) ────
 *
 * Producer codes: 400 VALIDATION_ERROR, 403 ACCESS_DENIED, 404 SELLER_NOT_FOUND,
 * 409 CONFLICT (duplicate sellerId within tenant).
 */

type Method = 'GET' | 'POST';

interface CallOptions {
  method: Method;
  /** Absolute base. Sellers use ECOMMERCE_ADMIN_BASE_URL (admin subtree). */
  base: string;
  /** Path relative to `base` (e.g. `/sellers`, `/sellers/{sellerId}`). */
  path: string;
  /** Typed mutation body; `undefined` for reads. */
  body?: unknown;
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }`).
 * Defensive: a missing / non-JSON body degrades to a synthetic code rather
 * than throwing.
 */
async function parseSellerError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce seller request failed (${res.status})`;
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
 * Single hardened call site for seller-service. Resolves the domain-facing
 * IAM OIDC token, applies the timeout, and maps the ecommerce flat error
 * envelope to the § 2.5 resilience taxonomy.
 */
async function callSeller<T>(
  opts: CallOptions,
  parse?: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Per-domain credential selection (§ 2.4.10): NEVER getOperatorToken().
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('ecommerce_seller_no_gap_session', {
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
  // NOTE: deliberately NO `X-Tenant-Id` — ecommerce resolves tenant from the
  // JWT `tenant_id` claim (gateway-injected; § 2.4.10 tenant invariant).
  // NOTE: NO `Idempotency-Key` — producer defines none (§ 2.4.10).
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
      const e = await parseSellerError(res);
      logger.warn('ecommerce_seller_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseSellerError(res);
      logger.warn('ecommerce_seller_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseSellerError(res);
      logger.warn('ecommerce_seller_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ecommerce seller-service unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR, 404 SELLER_NOT_FOUND, 409 CONFLICT → inline actionable.
      const e = await parseSellerError(res);
      logger.warn('ecommerce_seller_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info('ecommerce_seller_ok', {
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
    if (err instanceof ApiError || err instanceof EcommerceUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('ecommerce_seller_timeout', {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        'ecommerce seller-service call timed out',
      );
    }
    logger.error('ecommerce_seller_error', { requestId, path: opts.path });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ecommerce seller-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

function clampSize(size?: number): number {
  return Math.min(
    SELLER_MAX_PAGE_SIZE,
    Math.max(1, size ?? SELLER_DEFAULT_PAGE_SIZE),
  );
}

// ===========================================================================
// READS
// ===========================================================================

/** GET /api/admin/sellers?page=&size= (paginated seller summaries). */
export function listSellers(params: SellerListParams = {}): Promise<SellerList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callSeller(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/sellers?${qs.toString()}`,
    },
    (j) => SellerListSchema.parse(j),
  );
}

/** GET /api/admin/sellers/{sellerId} (seller detail). */
export function getSeller(sellerId: string): Promise<SellerDetail> {
  const env = getServerEnv();
  return callSeller(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/sellers/${encodeURIComponent(sellerId)}`,
    },
    (j) => SellerDetailSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (confirm-gated in the UI; NO Idempotency-Key)
// ===========================================================================

/** POST /api/admin/sellers (register). Returns { sellerId }. */
export function registerSeller(
  body: RegisterSellerBody,
): Promise<RegisterSellerResponse> {
  const env = getServerEnv();
  return callSeller(
    {
      method: 'POST',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: '/sellers',
      body,
    },
    (j) => RegisterSellerResponseSchema.parse(j),
  );
}
