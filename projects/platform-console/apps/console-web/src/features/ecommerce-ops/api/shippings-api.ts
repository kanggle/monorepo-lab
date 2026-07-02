import { getServerEnv } from '@/shared/config/env';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
import {
  ShippingListSchema,
  type ShippingList,
  ShippingAreaSummarySchema,
  type ShippingAreaSummary,
  type ShippingListParams,
  type UpdateShippingStatusBody,
  UpdateShippingStatusResponseSchema,
  type UpdateShippingStatusResponse,
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

/** Per-slice observability + message label for the shipping surface. */
const SHIPPING_LABEL: EcommerceCallLabel = {
  event: 'shipping',
  errorNoun: 'shipping',
  unavailableLabel: 'shipping-service',
  timedOutLabel: 'shipping-service',
  failedLabel: 'shipping-service',
};

const clampSize = (size?: number): number =>
  clampPageSize(size, SHIPPING_DEFAULT_PAGE_SIZE, SHIPPING_MAX_PAGE_SIZE);

// ===========================================================================
// READS
// ===========================================================================

/** GET /api/shippings/summary — period-based counts (TASK-PC-FE-164).
 *  Returns { today, week, month, total } for the tenant. */
export function getShippingsSummary(): Promise<ShippingAreaSummary> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: '/shippings/summary',
    },
    (j) => ShippingAreaSummarySchema.parse(j),
    SHIPPING_LABEL,
  );
}

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
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/shippings?${qs.toString()}`,
    },
    (j) => ShippingListSchema.parse(j),
    SHIPPING_LABEL,
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
 *
 * Returns the producer's `UpdateShippingStatusResponse` **3-field projection**
 * `{ shippingId, status, updatedAt }` — NOT a full Shipping (TASK-PC-FE-129).
 * Parsing this with the full `ShippingSchema` would throw on the real wire
 * shape (missing `orderId`/`createdAt`) and turn a committed 200 into a false
 * failure. The UI only awaits success then re-fetches the list, so the
 * projection is sufficient.
 */
export function updateShippingStatus(
  id: string,
  body: UpdateShippingStatusBody,
): Promise<UpdateShippingStatusResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PUT',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/shippings/${encodeURIComponent(id)}/status`,
      body,
    },
    (j) => UpdateShippingStatusResponseSchema.parse(j),
    SHIPPING_LABEL,
  );
}

/**
 * POST /api/shippings/{shippingId}/refresh-tracking — operator-triggered carrier sync.
 * Empty body. Best-effort: returns 200 with the (possibly unchanged) shipment.
 * When the carrier mode is mock or carrier is unreachable, the status is
 * unchanged (no error surfaced — best-effort per spec).
 *
 * Returns the same `UpdateShippingStatusResponse` 3-field projection as the
 * status mutation (the producer's `refreshTracking` returns
 * `UpdateShippingStatusResponse`, NOT a full Shipping — TASK-PC-FE-129).
 */
export function refreshTracking(
  id: string,
): Promise<UpdateShippingStatusResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/shippings/${encodeURIComponent(id)}/refresh-tracking`,
      body: {},
    },
    (j) => UpdateShippingStatusResponseSchema.parse(j),
    SHIPPING_LABEL,
  );
}
