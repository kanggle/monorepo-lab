import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import {
  ProductListSchema,
  type ProductList,
  ProductDetailSchema,
  type ProductDetail,
  VariantSchema,
  type Variant,
  RegisterProductResponseSchema,
  type RegisterProductResponse,
  AdjustStockResponseSchema,
  type AdjustStockResponse,
  type ProductListParams,
  type RegisterProductBody,
  type UpdateProductBody,
  type AddVariantBody,
  type UpdateVariantBody,
  type AdjustStockBody,
  PRODUCT_DEFAULT_PAGE_SIZE,
  PRODUCT_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side ecommerce `product-service` operations client (TASK-PC-FE-081 —
 * the FIRST ecommerce **write** surface, ADR-MONO-031 Phase 1b). Drives the
 * in-console product catalog operator surface: list / detail / register /
 * update / delete + variant inline CRUD + stock adjust.
 *
 * Server-only by construction (same posture as `outbound-api.ts`): imported
 * exclusively from server components and the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/ecommerce/products/**` proxy routes, which attach the HttpOnly
 * credential here server-side.
 *
 * ── THE AUTH MODEL (console-integration-contract § 2.4.10, inheriting the
 *    non-IAM § 2.4.5 per-domain rules) ──────────────────────────────────────
 *
 * Per ADR-MONO-017 D2.A this surface is console-web → ecommerce gateway
 * DIRECT (no console-bff write leg). The ecommerce gateway requires
 * `account_type=OPERATOR` on the IAM OIDC token (BE-366 removed the producer
 * `X-User-Role` gate). Therefore this client uses `getDomainFacingToken()`
 * (the assumed tenant-scoped IAM OIDC token when the operator switched to a
 * customer, else the base access token — net-zero; ADR-MONO-020 D4) and NEVER
 * `getOperatorToken()` (that is the IAM `/api/admin/**` exchanged credential —
 * wrong issuer/type here; the #569 invariant is GAP-domain-scoped). A test
 * pins that `getOperatorToken` is never called.
 *
 * Tenant invariant (§ 2.4.10): ecommerce resolves the tenant from the JWT
 * `tenant_id ∈ {ecommerce,*}` claim (gateway `TenantClaimValidator` injects
 * the trusted `X-Tenant-Id`; the repository `WHERE tenant_id` chokepoint
 * isolates) — the console therefore does NOT send `X-Tenant-Id`.
 *
 * Mutation discipline (§ 2.4.10): the ecommerce product admin API defines NO
 * `Idempotency-Key` / `version` / ETag. The console does NOT fabricate one
 * (carrying a key the producer ignores is a defect) — confirm-gate (UI) +
 * producer state guards (the `409 CONFLICT` optimistic-lock + the
 * `422 VALIDATION_ERROR` family) are the double-submit / conflict defence.
 * Mutations are reason-free (the surface defines no `X-Operator-Reason`); the
 * one exception is the stock-adjust producer body, which carries a `reason`
 * field IN THE BODY (AdjustStockRequest), not a header.
 *
 * Error envelope (§ 2.4.10 / § 2.5): ecommerce uses the FLAT shape
 * `{ code, message, timestamp }` (the shared `ErrorResponse.of` — DISTINCT
 * from wms's nested `{ error: { code } }`). `parseEcommerceError()` reads the
 * flat shape and tolerates an absent / non-JSON body without crashing.
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * `401` → `ApiError` (whole-session re-login); `403` → `ApiError` (inline
 * "not available to your role"); `404`/`400`/`422`/`409` → `ApiError` (inline
 * actionable — the `409 CONFLICT` path drives a refetch + retry-prompt, never
 * a silent auto-retry); `503`/timeout/network → `EcommerceUnavailableError`
 * (ONLY this section degrades).
 *
 * Logging: structured, server-side only; the IAM access token and any product
 * data are NEVER logged (redacted).
 */

type Method = 'GET' | 'POST' | 'PATCH' | 'DELETE';

interface CallOptions {
  method: Method;
  /** Absolute base — admin (`ECOMMERCE_ADMIN_BASE_URL`) for the CRUD subtree,
   *  public (`ECOMMERCE_PUBLIC_BASE_URL`) for the detail read (#2). */
  base: string;
  /** Path relative to `base` (e.g. `/products`, `/products/{id}/variants`). */
  path: string;
  /** Typed mutation body; `undefined` for reads + DELETE. */
  body?: unknown;
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }` —
 * the shared `ErrorResponse`). Defensive: a missing / non-JSON body degrades
 * to a synthetic code rather than throwing (the producer is the authority for
 * the real code; this never crashes the console on a malformed error body).
 */
async function parseEcommerceError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce product request failed (${res.status})`;
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
 * § 2.5 resilience taxonomy. `parse` is `undefined` for a 204 (DELETE).
 */
async function callEcommerce<T>(
  opts: CallOptions,
  parse?: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential selection (§ 2.4.10, inherited from § 2.4.5):
  //    the ecommerce gateway requires the IAM OIDC token (account_type=
  //    OPERATOR). NEVER getOperatorToken() — that is the IAM-domain (§ 2.6
  //    exchanged) credential; ecommerce would reject it. The credential is the
  //    DOMAIN-FACING token (assumed-when-switched, else the base access
  //    token — net-zero; ADR-MONO-020 D4).
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('ecommerce_no_gap_session', { requestId, path: opts.path });
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
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // ONLY the ecommerce section degrades — shell + other sections intact.
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ecommerce product-service unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR / INVALID_CATEGORY / INSUFFICIENT_STOCK,
      // 404 PRODUCT_NOT_FOUND / VARIANT_NOT_FOUND, 422 VALIDATION_ERROR,
      // 409 CONFLICT (optimistic lock) → inline actionable (no crash).
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info('ecommerce_ok', {
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
    if (err instanceof ApiError || err instanceof EcommerceUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('ecommerce_timeout', {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        'ecommerce product-service call timed out',
      );
    }
    logger.error('ecommerce_error', { requestId, path: opts.path });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ecommerce product-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

function clampSize(size?: number): number {
  return Math.min(
    PRODUCT_MAX_PAGE_SIZE,
    Math.max(1, size ?? PRODUCT_DEFAULT_PAGE_SIZE),
  );
}

// ===========================================================================
// READS (no mutation artifacts)
// ===========================================================================

/** 1 — GET /admin/products (paginated summaries). */
export function listProducts(
  params: ProductListParams = {},
): Promise<ProductList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.categoryId) qs.set('categoryId', params.categoryId);
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products?${qs.toString()}`,
    },
    (j) => ProductListSchema.parse(j),
  );
}

/** 2 — GET /products/{id} (public detail read path — admin controller has no
 *  GET /{id}; contract row #2). Carries variants[] + images[]. */
export function getProduct(id: string): Promise<ProductDetail> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/products/${encodeURIComponent(id)}`,
    },
    (j) => ProductDetailSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (confirm-gated in the UI; NO Idempotency-Key — producer defines
// none; state-guard-dependent — 409/422 surfaced inline)
// ===========================================================================

/** 3 — POST /admin/products (register). */
export function registerProduct(
  body: RegisterProductBody,
): Promise<RegisterProductResponse> {
  const env = getServerEnv();
  return callEcommerce(
    { method: 'POST', base: env.ECOMMERCE_ADMIN_BASE_URL, path: '/products', body },
    (j) => RegisterProductResponseSchema.parse(j),
  );
}

/** 4 — PATCH /admin/products/{id} (update; partial). */
export function updateProduct(
  id: string,
  body: UpdateProductBody,
): Promise<RegisterProductResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PATCH',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(id)}`,
      body,
    },
    (j) => RegisterProductResponseSchema.parse(j),
  );
}

/** 5 — DELETE /admin/products/{id} (204 No Content). */
export function deleteProduct(id: string): Promise<void> {
  const env = getServerEnv();
  return callEcommerce({
    method: 'DELETE',
    base: env.ECOMMERCE_ADMIN_BASE_URL,
    path: `/products/${encodeURIComponent(id)}`,
  });
}

/** 6 — POST /admin/products/{id}/variants (add variant → VariantDetail). */
export function addVariant(
  productId: string,
  body: AddVariantBody,
): Promise<Variant> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(productId)}/variants`,
      body,
    },
    (j) => VariantSchema.parse(j),
  );
}

/** 7 — PATCH /admin/products/{id}/variants/{variantId} (update variant). */
export function updateVariant(
  productId: string,
  variantId: string,
  body: UpdateVariantBody,
): Promise<Variant> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PATCH',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
      body,
    },
    (j) => VariantSchema.parse(j),
  );
}

/** 8 — DELETE /admin/products/{id}/variants/{variantId} (204 No Content). */
export function deleteVariant(
  productId: string,
  variantId: string,
): Promise<void> {
  const env = getServerEnv();
  return callEcommerce({
    method: 'DELETE',
    base: env.ECOMMERCE_ADMIN_BASE_URL,
    path: `/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
  });
}

/** 9 — PATCH /admin/products/{id}/stock (adjust stock; body carries the signed
 *  `quantity` delta + `reason`). Confirm-gated. */
export function adjustStock(
  productId: string,
  body: AdjustStockBody,
): Promise<AdjustStockResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PATCH',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(productId)}/stock`,
      body,
    },
    (j) => AdjustStockResponseSchema.parse(j),
  );
}
