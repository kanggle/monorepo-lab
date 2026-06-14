import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import {
  OrderListSchema,
  type OrderList,
  OrderDetailSchema,
  type OrderDetail,
  OrderStatusChangeResponseSchema,
  type OrderStatusChangeResponse,
  type OrderListParams,
  type OrderStatusChangeBody,
  ORDER_DEFAULT_PAGE_SIZE,
  ORDER_MAX_PAGE_SIZE,
} from './order-types';

/**
 * Server-side ecommerce `order-service` operations client (TASK-PC-FE-083 —
 * the orders facet of ADR-MONO-031 Phase 1b). Drives the in-console order
 * operator surface: list / detail / status change.
 *
 * Server-only by construction (same posture as `products-api.ts`): imported
 * exclusively from server components and the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/ecommerce/orders/**` proxy routes, which attach the HttpOnly
 * credential here server-side.
 *
 * ── THE AUTH MODEL (same as products-api.ts — § 2.4.10) ───────────────────
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
 * Mutation discipline (§ 2.4.10): the ecommerce order admin API defines NO
 * `Idempotency-Key`. The console does NOT fabricate one — confirm-gate (UI) +
 * producer state guards (the `400 INVALID_ORDER` / `409 CONFLICT` / `422
 * ORDER_CANNOT_BE_CANCELLED`) are the double-submit / conflict defence.
 *
 * Error envelope (§ 2.4.10 / § 2.5): ecommerce uses the FLAT shape
 * `{ code, message, timestamp }` (the shared `ErrorResponse.of` — DISTINCT
 * from wms's nested `{ error: { code } }`). `parseOrderError()` reads the
 * flat shape and tolerates an absent / non-JSON body without crashing.
 *
 * NOTE: `callOrder`/`parseOrderError` are inline re-implementations of the
 * same pattern in `products-api.ts`. This is intentional — the task spec
 * explicitly states that modifying `products-api.ts` is forbidden and a small
 * amount of duplication is preferred over touching the existing file.
 *
 * Resilience (§ 2.5):
 *   - `401` → `ApiError(401)` (whole-session re-login).
 *   - `403` → `ApiError(403)` (inline "not available to your role").
 *   - `404`/`400`/`422`/`409` → `ApiError` (inline actionable, no crash).
 *     400 = InvalidOrder (wrong forward transition) or InvalidOrderStatus
 *     422 = OrderCannotBeCancelled (ship/deliver → cancel attempt)
 *     409 = optimistic lock conflict → refetch + retry-prompt
 *   - `503`/timeout/network → `EcommerceUnavailableError` (section degrades).
 */

type Method = 'GET' | 'POST' | 'PATCH' | 'DELETE';

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
async function parseOrderError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce order request failed (${res.status})`;
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
 * Single hardened call site for order-service. Resolves the domain-facing
 * IAM OIDC token, applies the timeout, and maps the ecommerce flat error
 * envelope to the § 2.5 resilience taxonomy.
 */
async function callOrder<T>(
  opts: CallOptions,
  parse?: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Per-domain credential selection (§ 2.4.10): the ecommerce gateway requires
  // the IAM OIDC token (account_type=OPERATOR). NEVER getOperatorToken() —
  // that is the IAM-domain (§ 2.6 exchanged) credential; ecommerce rejects it.
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('ecommerce_order_no_gap_session', {
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
      const e = await parseOrderError(res);
      logger.warn('ecommerce_order_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseOrderError(res);
      logger.warn('ecommerce_order_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseOrderError(res);
      logger.warn('ecommerce_order_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // ONLY the ecommerce section degrades — shell + other sections intact.
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ecommerce order-service unavailable',
      );
    }

    if (!res.ok) {
      // 400 INVALID_ORDER (invalid forward transition) / INVALID_ORDER_STATUS
      // 404 ORDER_NOT_FOUND
      // 422 ORDER_CANNOT_BE_CANCELLED (shipped/delivered → cancel attempt)
      // 409 CONFLICT (optimistic lock) → inline actionable (no crash).
      const e = await parseOrderError(res);
      logger.warn('ecommerce_order_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info('ecommerce_order_ok', {
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
      logger.warn('ecommerce_order_timeout', {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        'ecommerce order-service call timed out',
      );
    }
    logger.error('ecommerce_order_error', { requestId, path: opts.path });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ecommerce order-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

function clampSize(size?: number): number {
  return Math.min(
    ORDER_MAX_PAGE_SIZE,
    Math.max(1, size ?? ORDER_DEFAULT_PAGE_SIZE),
  );
}

// ===========================================================================
// READS
// ===========================================================================

/** #15 — GET /admin/orders?status&page&size (paginated order summaries). */
export function listOrders(params: OrderListParams = {}): Promise<OrderList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callOrder(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/orders?${qs.toString()}`,
    },
    (j) => OrderListSchema.parse(j),
  );
}

/** #16 — GET /admin/orders/{id} (order detail with items + shipping address). */
export function getOrder(id: string): Promise<OrderDetail> {
  const env = getServerEnv();
  return callOrder(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/orders/${encodeURIComponent(id)}`,
    },
    (j) => OrderDetailSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (confirm-gated in the UI; NO Idempotency-Key; state-guard-dependent)
// ===========================================================================

/** #17 — POST /admin/orders/{id}/status (change order status). */
export function changeOrderStatus(
  id: string,
  body: OrderStatusChangeBody,
): Promise<OrderStatusChangeResponse> {
  const env = getServerEnv();
  return callOrder(
    {
      method: 'POST',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/orders/${encodeURIComponent(id)}/status`,
      body,
    },
    (j) => OrderStatusChangeResponseSchema.parse(j),
  );
}
