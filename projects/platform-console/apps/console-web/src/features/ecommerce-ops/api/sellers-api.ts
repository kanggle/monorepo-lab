import { getServerEnv } from '@/shared/config/env';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
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

/** Per-slice observability + message label for the seller surface. */
const SELLER_LABEL: EcommerceCallLabel = {
  event: 'seller',
  errorNoun: 'seller',
  unavailableLabel: 'seller-service',
  timedOutLabel: 'seller-service',
  failedLabel: 'seller-service',
};

const clampSize = (size?: number): number =>
  clampPageSize(size, SELLER_DEFAULT_PAGE_SIZE, SELLER_MAX_PAGE_SIZE);

// ===========================================================================
// READS
// ===========================================================================

/** GET /api/admin/sellers?page=&size= (paginated seller summaries). */
export function listSellers(params: SellerListParams = {}): Promise<SellerList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/sellers?${qs.toString()}`,
    },
    (j) => SellerListSchema.parse(j),
    SELLER_LABEL,
  );
}

/** GET /api/admin/sellers/{sellerId} (seller detail). */
export function getSeller(sellerId: string): Promise<SellerDetail> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/sellers/${encodeURIComponent(sellerId)}`,
    },
    (j) => SellerDetailSchema.parse(j),
    SELLER_LABEL,
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
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: '/sellers',
      body,
    },
    (j) => RegisterSellerResponseSchema.parse(j),
    SELLER_LABEL,
  );
}
