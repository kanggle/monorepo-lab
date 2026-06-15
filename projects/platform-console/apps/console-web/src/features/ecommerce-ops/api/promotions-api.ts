import { getServerEnv } from '@/shared/config/env';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
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

/** Per-slice observability + message label for the promotion surface. */
const PROMOTION_LABEL: EcommerceCallLabel = {
  event: 'promotion',
  errorNoun: 'promotion',
  unavailableLabel: 'promotion-service',
  timedOutLabel: 'promotion-service',
  failedLabel: 'promotion-service',
};

const clampSize = (size?: number): number =>
  clampPageSize(size, PROMOTION_DEFAULT_PAGE_SIZE, PROMOTION_MAX_PAGE_SIZE);

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
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions?${qs.toString()}`,
    },
    (j) => PromotionListSchema.parse(j),
    PROMOTION_LABEL,
  );
}

/** GET /api/promotions/{id} (full detail with description, maxDiscountAmount, createdAt, updatedAt). */
export function getPromotion(id: string): Promise<PromotionDetail> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions/${encodeURIComponent(id)}`,
    },
    (j) => PromotionDetailSchema.parse(j),
    PROMOTION_LABEL,
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
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: '/promotions',
      body,
    },
    (j) => PromotionMutationResponseSchema.parse(j),
    PROMOTION_LABEL,
  );
}

/** PUT /api/promotions/{id} (full replace). Returns { promotionId }.
 *  NOTE: this is PUT (full replace), NOT PATCH — the producer contract uses PUT. */
export function updatePromotion(
  id: string,
  body: UpdatePromotionBody,
): Promise<PromotionMutationResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PUT',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions/${encodeURIComponent(id)}`,
      body,
    },
    (j) => PromotionMutationResponseSchema.parse(j),
    PROMOTION_LABEL,
  );
}

/** DELETE /api/promotions/{id} (204 No Content). */
export function deletePromotion(id: string): Promise<void> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'DELETE',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions/${encodeURIComponent(id)}`,
    },
    undefined,
    PROMOTION_LABEL,
  );
}

/** POST /api/promotions/{id}/coupons/issue (issue coupons to user list). Returns { issuedCount }. */
export function issueCoupons(
  id: string,
  body: IssueCouponBody,
): Promise<IssueCouponResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/promotions/${encodeURIComponent(id)}/coupons/issue`,
      body,
    },
    (j) => IssueCouponResponseSchema.parse(j),
    PROMOTION_LABEL,
  );
}
