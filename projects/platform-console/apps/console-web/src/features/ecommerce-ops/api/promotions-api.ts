import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import {
  PromotionListSchema,
  type PromotionList,
  PromotionDetailSchema,
  type PromotionDetail,
  PromotionMutationResponseSchema,
  type PromotionMutationResponse,
  IssueCouponResponseSchema,
  type IssueCouponResponse,
  type PromotionListParams,
  type CreatePromotionBody,
  type UpdatePromotionBody,
  type IssueCouponBody,
  PROMOTION_DEFAULT_PAGE_SIZE,
  PROMOTION_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side ecommerce `promotion-service` operations client (TASK-PC-FE-086 —
 * ADR-MONO-031 Phase 3b). Drives the in-console promotion operator surface:
 * list / detail / create / update (PUT full replace) / delete + coupon issue.
 *
 * ── BASE URL RESOLUTION (promotion-service path divergence) ─────────────────
 *
 * promotion-service exposes endpoints at `/api/promotions/**` — NOT under the
 * `/api/admin/**` subtree used by product-service. Therefore this client uses
 * `ECOMMERCE_PUBLIC_BASE_URL` (defaults to `http://ecommerce.local/api`) with
 * path `/promotions/**`, yielding the correct gateway URL:
 *   `http://ecommerce.local/api/promotions`
 *
 * Contrast with products-api, which uses `ECOMMERCE_ADMIN_BASE_URL`
 * (`http://ecommerce.local/api/admin`) + `/products` for the admin subtree.
 * Promotions never hit `/api/admin/**`; the `ECOMMERCE_PUBLIC_BASE_URL` base
 * is the right root for a non-admin `ecommerce.local/api/**` path.
 *
 * ── AUTH MODEL (identical to products-api — § 2.4.10) ────────────────────────
 *
 * Uses `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC token or
 * the base access token — net-zero; ADR-MONO-020 D4). NEVER `getOperatorToken()`
 * (that is the IAM-domain credential — wrong issuer/type for ecommerce).
 * Tenant rides in the JWT `tenant_id` claim — the console sends NO `X-Tenant-Id`.
 * NO `Idempotency-Key` (producer defines none — § 2.4.10).
 *
 * ── ERROR ENVELOPE (flat { code, message, timestamp } — same as products) ────
 *
 * Producer codes: 400 VALIDATION_ERROR, 403 ACCESS_DENIED, 404 PROMOTION_NOT_FOUND,
 * 422 PROMOTION_ALREADY_ENDED / PROMOTION_HAS_ISSUED_COUPONS / PROMOTION_NOT_ACTIVE
 *       / COUPON_LIMIT_EXCEEDED.
 */

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

interface CallOptions {
  method: Method;
  /** Absolute base. Promotions use ECOMMERCE_PUBLIC_BASE_URL (see above). */
  base: string;
  /** Path relative to `base` (e.g. `/promotions`, `/promotions/{id}`). */
  path: string;
  /** Typed mutation body; `undefined` for reads + DELETE. */
  body?: unknown;
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }`).
 * Defensive: a missing / non-JSON body degrades to a synthetic code rather
 * than throwing.
 */
async function parseEcommerceError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce promotion request failed (${res.status})`;
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
async function callEcommercePromotion<T>(
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
    logger.warn('ecommerce_promotion_no_gap_session', {
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
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_promotion_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_promotion_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_promotion_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ecommerce promotion-service unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR, 404 PROMOTION_NOT_FOUND, 422 PROMOTION_ALREADY_ENDED
      // / PROMOTION_HAS_ISSUED_COUPONS / PROMOTION_NOT_ACTIVE / COUPON_LIMIT_EXCEEDED
      // → inline actionable (no crash).
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_promotion_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info('ecommerce_promotion_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });

    // 204 No Content (DELETE) — nothing to parse.
    if (res.status === 204 || parse === undefined) {
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
      logger.warn('ecommerce_promotion_timeout', {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        'ecommerce promotion-service call timed out',
      );
    }
    logger.error('ecommerce_promotion_error', { requestId, path: opts.path });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ecommerce promotion-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

function clampSize(size?: number): number {
  return Math.min(
    PROMOTION_MAX_PAGE_SIZE,
    Math.max(1, size ?? PROMOTION_DEFAULT_PAGE_SIZE),
  );
}

// ===========================================================================
// READS
// ===========================================================================

/** GET /api/promotions?status&page&size (paginated summaries). */
export function listPromotions(
  params: PromotionListParams = {},
): Promise<PromotionList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommercePromotion(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions?${qs.toString()}`,
    },
    (j) => PromotionListSchema.parse(j),
  );
}

/** GET /api/promotions/{id} (full detail with description, maxDiscountAmount, createdAt, updatedAt). */
export function getPromotion(id: string): Promise<PromotionDetail> {
  const env = getServerEnv();
  return callEcommercePromotion(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions/${encodeURIComponent(id)}`,
    },
    (j) => PromotionDetailSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (confirm-gated in the UI; NO Idempotency-Key; state-guard-dependent)
// ===========================================================================

/** POST /api/promotions (create). Returns { promotionId }. */
export function createPromotion(
  body: CreatePromotionBody,
): Promise<PromotionMutationResponse> {
  const env = getServerEnv();
  return callEcommercePromotion(
    {
      method: 'POST',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: '/promotions',
      body,
    },
    (j) => PromotionMutationResponseSchema.parse(j),
  );
}

/** PUT /api/promotions/{id} (full replace). Returns { promotionId }.
 *  NOTE: this is PUT (full replace), NOT PATCH — the producer contract uses PUT. */
export function updatePromotion(
  id: string,
  body: UpdatePromotionBody,
): Promise<PromotionMutationResponse> {
  const env = getServerEnv();
  return callEcommercePromotion(
    {
      method: 'PUT',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions/${encodeURIComponent(id)}`,
      body,
    },
    (j) => PromotionMutationResponseSchema.parse(j),
  );
}

/** DELETE /api/promotions/{id} (204 No Content). */
export function deletePromotion(id: string): Promise<void> {
  const env = getServerEnv();
  return callEcommercePromotion({
    method: 'DELETE',
    base: env.ECOMMERCE_PUBLIC_BASE_URL,
    path: `/promotions/${encodeURIComponent(id)}`,
  });
}

/** POST /api/promotions/{id}/coupons/issue (issue coupons to user list). Returns { issuedCount }. */
export function issueCoupons(
  id: string,
  body: IssueCouponBody,
): Promise<IssueCouponResponse> {
  const env = getServerEnv();
  return callEcommercePromotion(
    {
      method: 'POST',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions/${encodeURIComponent(id)}/coupons/issue`,
      body,
    },
    (j) => IssueCouponResponseSchema.parse(j),
  );
}
