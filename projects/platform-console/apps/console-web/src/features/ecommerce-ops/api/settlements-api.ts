import { getServerEnv } from '@/shared/config/env';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
import {
  AccrualsResponseSchema,
  type AccrualsResponse,
  type AccrualsListParams,
  SellerBalanceSchema,
  type SellerBalance,
  CommissionRateSchema,
  type CommissionRate,
  PeriodsResponseSchema,
  type PeriodsResponse,
  type PeriodsListParams,
  PayoutsResponseSchema,
  type PayoutsResponse,
  type PayoutsListParams,
  SETTLEMENT_DEFAULT_PAGE_SIZE,
  SETTLEMENT_MAX_PAGE_SIZE,
} from './settlement-types';

/**
 * Server-side ecommerce `settlement-service` operator reads client
 * (TASK-PC-FE-221 Phase A — the 8th ecommerce-ops facet). Drives the in-console
 * settlement screens (accruals / seller balance / commission rate / periods /
 * payouts).
 *
 * ── BASE URL RESOLUTION (admin subtree — same as sellers/products) ──
 *
 * Settlement endpoints live under `/api/admin/settlements` (the gateway route
 * `Path=/api/admin/settlements/**`), so this client uses ECOMMERCE_ADMIN_BASE_URL
 * (`http://ecommerce.local/api/admin`) + `/settlements`. Using
 * ECOMMERCE_PUBLIC_BASE_URL would 404 (admin subtree only).
 *
 * ── AUTH MODEL (§ 2.4.10 — identical to sellers-api / products-api) ──
 *
 * Consumes the shared `callEcommerce` wrapper (→ `callEcommerceGateway` core,
 * PC-FE-213): credential = `getDomainFacingToken()` (NEVER `getOperatorToken()`);
 * NO `X-Tenant-Id` (tenant rides in the JWT `tenant_id` claim); NO
 * `Idempotency-Key`. READ-ONLY — mutations (rate PUT, period open/close, payout
 * execute) are Phase B.
 *
 * ── ERROR ENVELOPE (flat { code, message, timestamp }) ──
 *
 * Producer codes: 403 TENANT_FORBIDDEN, 404 SETTLEMENT_NOT_FOUND (cross-tenant /
 * cross-seller / unknown id). 503/timeout → EcommerceUnavailableError (degrade).
 */

/** Per-slice observability + message label for the settlement surface. */
const SETTLEMENT_LABEL: EcommerceCallLabel = {
  event: 'settlement',
  errorNoun: 'settlement',
  unavailableLabel: 'settlement-service',
  timedOutLabel: 'settlement-service',
  failedLabel: 'settlement-service',
};

const clampSize = (size?: number): number =>
  clampPageSize(size, SETTLEMENT_DEFAULT_PAGE_SIZE, SETTLEMENT_MAX_PAGE_SIZE);

// ===========================================================================
// READS (Phase A — no mutations)
// ===========================================================================

/** GET /settlements/accruals?sellerId=&orderId=&page=&size= (append-only ledger). */
export function listAccruals(
  params: AccrualsListParams = {},
): Promise<AccrualsResponse> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.sellerId) qs.set('sellerId', params.sellerId);
  if (params.orderId) qs.set('orderId', params.orderId);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/settlements/accruals?${qs.toString()}`,
    },
    (j) => AccrualsResponseSchema.parse(j),
    SETTLEMENT_LABEL,
  );
}

/** GET /settlements/sellers/{sellerId}/balance (seller settlement rollup). */
export function getSellerBalance(sellerId: string): Promise<SellerBalance> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/settlements/sellers/${encodeURIComponent(sellerId)}/balance`,
    },
    (j) => SellerBalanceSchema.parse(j),
    SETTLEMENT_LABEL,
  );
}

/** GET /settlements/commission-rates/{sellerId} (effective rate + source). */
export function getCommissionRate(sellerId: string): Promise<CommissionRate> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/settlements/commission-rates/${encodeURIComponent(sellerId)}`,
    },
    (j) => CommissionRateSchema.parse(j),
    SETTLEMENT_LABEL,
  );
}

/** GET /settlements/periods?page=&size= (paginated settlement periods). */
export function listPeriods(
  params: PeriodsListParams = {},
): Promise<PeriodsResponse> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/settlements/periods?${qs.toString()}`,
    },
    (j) => PeriodsResponseSchema.parse(j),
    SETTLEMENT_LABEL,
  );
}

/** GET /settlements/periods/{periodId}/payouts?page=&size= (per-seller payouts). */
export function listPayouts(
  periodId: string,
  params: PayoutsListParams = {},
): Promise<PayoutsResponse> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/settlements/periods/${encodeURIComponent(periodId)}/payouts?${qs.toString()}`,
    },
    (j) => PayoutsResponseSchema.parse(j),
    SETTLEMENT_LABEL,
  );
}
