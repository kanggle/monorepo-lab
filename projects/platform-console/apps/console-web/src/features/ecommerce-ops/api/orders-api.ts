import { getServerEnv } from '@/shared/config/env';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
import {
  OrderListSchema,
  type OrderList,
  OrderDetailSchema,
  type OrderDetail,
  OrderStatusChangeResponseSchema,
  type OrderStatusChangeResponse,
  OrderAreaSummarySchema,
  type OrderAreaSummary,
  OrderInsightsSchema,
  type OrderInsights,
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

/** Per-slice observability + message label for the order surface. */
const ORDER_LABEL: EcommerceCallLabel = {
  event: 'order',
  errorNoun: 'order',
  unavailableLabel: 'order-service',
  timedOutLabel: 'order-service',
  failedLabel: 'order-service',
};

const clampSize = (size?: number): number =>
  clampPageSize(size, ORDER_DEFAULT_PAGE_SIZE, ORDER_MAX_PAGE_SIZE);

// ===========================================================================
// READS
// ===========================================================================

/** GET /admin/orders/summary — period-based counts (TASK-PC-FE-164).
 *  Returns { today, week, month, total } for the tenant. */
export function getOrdersSummary(): Promise<OrderAreaSummary> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: '/orders/summary',
    },
    (j) => OrderAreaSummarySchema.parse(j),
    ORDER_LABEL,
  );
}

/** GET /admin/orders/insights — top-5 product/seller rankings (TASK-PC-FE-170). */
export function getOrderInsights(): Promise<OrderInsights> {
  const env = getServerEnv();
  return callEcommerce(
    { method: 'GET', base: env.ECOMMERCE_ADMIN_BASE_URL, path: '/orders/insights' },
    (j) => OrderInsightsSchema.parse(j),
    ORDER_LABEL,
  );
}

/** #15 — GET /admin/orders?status&page&size (paginated order summaries). */
export function listOrders(params: OrderListParams = {}): Promise<OrderList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/orders?${qs.toString()}`,
    },
    (j) => OrderListSchema.parse(j),
    ORDER_LABEL,
  );
}

/** #16 — GET /admin/orders/{id} (order detail with items + shipping address). */
export function getOrder(id: string): Promise<OrderDetail> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/orders/${encodeURIComponent(id)}`,
    },
    (j) => OrderDetailSchema.parse(j),
    ORDER_LABEL,
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
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/orders/${encodeURIComponent(id)}/status`,
      body,
    },
    (j) => OrderStatusChangeResponseSchema.parse(j),
    ORDER_LABEL,
  );
}
