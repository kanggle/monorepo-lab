'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  SellerListSchema,
  type SellerList,
  SellerDetailSchema,
  type SellerDetail,
  type SellerListParams,
  type RegisterSellerBody,
  SELLER_DEFAULT_PAGE_SIZE,
  SELLER_MAX_PAGE_SIZE,
} from '../api/seller-types';

/**
 * Client-side ecommerce-ops seller hooks (TASK-PC-FE-090 — ADR-MONO-031
 * § 2.4.10 7th area). Every call goes to the same-origin
 * `/api/ecommerce/sellers/**` proxy (the typed API client's single backend
 * entry point); the proxy attaches the HttpOnly **domain-facing IAM OIDC
 * token** server-side — the browser never reads a token or calls the
 * ecommerce gateway directly (contract § 2.3 / § 2.4.10).
 *
 * Mutation discipline: NO `Idempotency-Key` (the producer defines none) —
 * confirm-gate is the double-submit defence.
 * status=ACTIVE only (v1). NO update/delete hooks (producer defines none).
 */

const SELLERS_KEY = 'ecommerce-sellers';

const clampSize = (size?: number): number =>
  clampPageSize(size, SELLER_DEFAULT_PAGE_SIZE, SELLER_MAX_PAGE_SIZE);

// --- list -----------------------------------------------------------------

export function sellersKey(params: SellerListParams) {
  return [
    SELLERS_KEY,
    'list',
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildSellersQs(params: SellerListParams): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchSellers(params: SellerListParams): Promise<SellerList> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/sellers?${buildSellersQs(params)}`,
  );
  return SellerListSchema.parse(raw);
}

export function useSellers(params: SellerListParams, initial?: SellerList) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: sellersKey(params),
    queryFn: () => fetchSellers(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- detail ----------------------------------------------------------------

async function fetchSeller(id: string): Promise<SellerDetail> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/sellers/${encodeURIComponent(id)}`,
  );
  return SellerDetailSchema.parse(raw);
}

export function useSeller(id: string | null, initial?: SellerDetail) {
  return useQuery({
    queryKey: [SELLERS_KEY, 'detail', id] as const,
    queryFn: () => fetchSeller(id as string),
    enabled: id !== null,
    initialData: initial,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

// --- mutations ------------------------------------------------------------

/** Invalidate the list after a successful register. */
function invalidateList(qc: ReturnType<typeof useQueryClient>) {
  // Refetch a mounted list (no flash) AND drop the inactive seeded cache so a
  // remount after a cross-page register re-seeds from the fresh SSR render — an
  // inactive seed-only query is not refetched (TASK-PC-FE-126).
  qc.invalidateQueries({ queryKey: [SELLERS_KEY, 'list'] });
  qc.removeQueries({ queryKey: [SELLERS_KEY, 'list'], type: 'inactive' });
}

export function useRegisterSeller() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RegisterSellerBody) =>
      apiClient.post<{ sellerId: string }>(
        '/api/ecommerce/sellers',
        body,
      ),
    onSuccess: () => invalidateList(qc),
  });
}
