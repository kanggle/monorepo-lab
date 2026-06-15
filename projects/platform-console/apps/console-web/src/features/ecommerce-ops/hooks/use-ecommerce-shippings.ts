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
  ShippingListSchema,
  type ShippingList,
  ShippingSchema,
  type ShippingListParams,
  type UpdateShippingStatusBody,
  SHIPPING_DEFAULT_PAGE_SIZE,
  SHIPPING_MAX_PAGE_SIZE,
} from '../api/shipping-types';

/**
 * Client-side ecommerce-ops shipping hooks (TASK-PC-FE-088 — ADR-031 Phase 4b).
 * Every call goes to the same-origin `/api/ecommerce/shippings/**` proxy (the
 * typed API client's single backend entry point); the proxy attaches the HttpOnly
 * **domain-facing IAM OIDC token** server-side — the browser never reads a token
 * or calls the ecommerce gateway directly (contract § 2.3 / § 2.4.10.3).
 *
 * Mirrors `use-ecommerce-promotions.ts` exactly: same query-key structure,
 * same seed conventions, same staleTime logic.
 *
 * Mutation discipline (§ 2.4.10): NO `Idempotency-Key` (the producer defines
 * none) — confirm-gate (in the screen) + producer state guards (400/409/422)
 * are the double-submit / conflict defence.
 */

const SHIPPINGS_KEY = 'ecommerce-shippings';

const clampSize = (size?: number): number =>
  clampPageSize(size, SHIPPING_DEFAULT_PAGE_SIZE, SHIPPING_MAX_PAGE_SIZE);

// --- list -----------------------------------------------------------------

export function shippingsKey(params: ShippingListParams) {
  return [
    SHIPPINGS_KEY,
    'list',
    params.status ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildShippingsQs(params: ShippingListParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchShippings(params: ShippingListParams): Promise<ShippingList> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/shippings?${buildShippingsQs(params)}`,
  );
  return ShippingListSchema.parse(raw);
}

export function useShippings(
  params: ShippingListParams,
  initial?: ShippingList,
) {
  const seeded =
    initial !== undefined && (params.page ?? 0) === 0 && !params.status;
  return useQuery({
    queryKey: shippingsKey(params),
    queryFn: () => fetchShippings(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- mutations ------------------------------------------------------------

/** Invalidate the shippings list after a mutation. */
function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [SHIPPINGS_KEY, 'list'] });
}

/**
 * PUT /api/ecommerce/shippings/{id}/status — linear status transition.
 * Body: `{ status, trackingNumber?, carrier? }`.
 * For SHIPPED: carrier + trackingNumber are required (ShipFormDialog enforces).
 */
export function useUpdateShippingStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      body,
    }: {
      id: string;
      body: UpdateShippingStatusBody;
    }) => {
      const raw = await apiClient.put<unknown>(
        `/api/ecommerce/shippings/${encodeURIComponent(id)}/status`,
        body,
      );
      return ShippingSchema.parse(raw);
    },
    onSuccess: () => invalidate(qc),
  });
}

/**
 * POST /api/ecommerce/shippings/{id}/refresh-tracking — operator-triggered
 * carrier sync (empty body, best-effort). Returns the updated Shipping.
 */
export function useRefreshTracking() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const raw = await apiClient.post<unknown>(
        `/api/ecommerce/shippings/${encodeURIComponent(id)}/refresh-tracking`,
        {},
      );
      return ShippingSchema.parse(raw);
    },
    onSuccess: () => invalidate(qc),
  });
}
