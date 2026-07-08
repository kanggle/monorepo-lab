'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
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
} from '../api/settlement-types';

/**
 * Client-side ecommerce-ops settlement hooks (TASK-PC-FE-221 Phase A). Every
 * call goes to the same-origin `/api/ecommerce/settlements/**` proxy (the typed
 * API client's single backend entry point); the proxy attaches the HttpOnly
 * domain-facing IAM OIDC token server-side — the browser never reads a token or
 * calls the ecommerce gateway directly (contract § 2.3 / § 2.4.10).
 *
 * READ-ONLY — Phase A has no mutations. Seeded page-0 queries reuse the server
 * render as `initialData`; a paginated / lookup query fetches fresh.
 */

const SETTLEMENTS_KEY = 'ecommerce-settlements';

const clampSize = (size?: number): number =>
  clampPageSize(size, SETTLEMENT_DEFAULT_PAGE_SIZE, SETTLEMENT_MAX_PAGE_SIZE);

// --- accruals --------------------------------------------------------------

export function accrualsKey(params: AccrualsListParams) {
  return [
    SETTLEMENTS_KEY,
    'accruals',
    params.sellerId ?? '',
    params.orderId ?? '',
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildAccrualsQs(params: AccrualsListParams): string {
  const qs = new URLSearchParams();
  if (params.sellerId) qs.set('sellerId', params.sellerId);
  if (params.orderId) qs.set('orderId', params.orderId);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchAccruals(
  params: AccrualsListParams,
): Promise<AccrualsResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/settlements/accruals?${buildAccrualsQs(params)}`,
  );
  return AccrualsResponseSchema.parse(raw);
}

/** The seeded page-0 query has NO filter (sellerId/orderId empty) — a filtered
 *  or paginated query re-fetches. */
export function useAccruals(
  params: AccrualsListParams,
  initial?: AccrualsResponse,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.sellerId &&
    !params.orderId;
  return useQuery({
    queryKey: accrualsKey(params),
    queryFn: () => fetchAccruals(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- seller balance (id-driven lookup) -------------------------------------

async function fetchSellerBalance(sellerId: string): Promise<SellerBalance> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/settlements/sellers/${encodeURIComponent(sellerId)}/balance`,
  );
  return SellerBalanceSchema.parse(raw);
}

export function useSellerBalance(sellerId: string | null) {
  return useQuery({
    queryKey: [SETTLEMENTS_KEY, 'balance', sellerId] as const,
    queryFn: () => fetchSellerBalance(sellerId as string),
    enabled: sellerId !== null,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

// --- commission rate (id-driven lookup) ------------------------------------

async function fetchCommissionRate(sellerId: string): Promise<CommissionRate> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/settlements/commission-rates/${encodeURIComponent(sellerId)}`,
  );
  return CommissionRateSchema.parse(raw);
}

export function useCommissionRate(sellerId: string | null) {
  return useQuery({
    queryKey: [SETTLEMENTS_KEY, 'commission-rate', sellerId] as const,
    queryFn: () => fetchCommissionRate(sellerId as string),
    enabled: sellerId !== null,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

// --- periods ---------------------------------------------------------------

export function periodsKey(params: PeriodsListParams) {
  return [
    SETTLEMENTS_KEY,
    'periods',
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildPeriodsQs(params: PeriodsListParams): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchPeriods(params: PeriodsListParams): Promise<PeriodsResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/settlements/periods?${buildPeriodsQs(params)}`,
  );
  return PeriodsResponseSchema.parse(raw);
}

export function usePeriods(params: PeriodsListParams, initial?: PeriodsResponse) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: periodsKey(params),
    queryFn: () => fetchPeriods(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- period payouts --------------------------------------------------------

export function payoutsKey(periodId: string, params: PayoutsListParams) {
  return [
    SETTLEMENTS_KEY,
    'payouts',
    periodId,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildPayoutsQs(params: PayoutsListParams): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchPayouts(
  periodId: string,
  params: PayoutsListParams,
): Promise<PayoutsResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/settlements/periods/${encodeURIComponent(periodId)}/payouts?${buildPayoutsQs(params)}`,
  );
  return PayoutsResponseSchema.parse(raw);
}

export function usePeriodPayouts(
  periodId: string,
  params: PayoutsListParams,
  initial?: PayoutsResponse,
) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: payoutsKey(periodId, params),
    queryFn: () => fetchPayouts(periodId, params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}
