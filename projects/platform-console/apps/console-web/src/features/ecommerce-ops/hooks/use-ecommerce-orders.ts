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
  OrderListSchema,
  type OrderList,
  OrderDetailSchema,
  type OrderDetail,
  type OrderListParams,
  type OrderStatusChangeBody,
  ORDER_DEFAULT_PAGE_SIZE,
  ORDER_MAX_PAGE_SIZE,
} from '../api/order-types';

/**
 * Client-side ecommerce-ops order hooks (TASK-PC-FE-083 — architecture.md
 * § Server vs Client Components — React Query is client-only). Every call goes
 * to the same-origin `/api/ecommerce/orders/**` proxy (the typed API client's
 * single backend entry point); the proxy attaches the HttpOnly domain-facing
 * IAM OIDC token server-side — the browser never reads a token or calls the
 * ecommerce gateway directly (contract § 2.3 / § 2.4.10).
 *
 * Mirrors `use-ecommerce-products.ts` exactly: same query-key structure, same
 * seed conventions, same staleTime logic.
 *
 * Mutation discipline (§ 2.4.10): NO `Idempotency-Key` (the producer defines
 * none) — confirm-gate (in the screen) + producer state guards (400/422/409)
 * are the double-submit / conflict defence.
 */

const ECOMMERCE_ORDERS_KEY = 'ecommerce-orders';

const clampSize = (size?: number): number =>
  clampPageSize(size, ORDER_DEFAULT_PAGE_SIZE, ORDER_MAX_PAGE_SIZE);

// --- list ------------------------------------------------------------------

export function ordersKey(params: OrderListParams) {
  return [
    ECOMMERCE_ORDERS_KEY,
    'list',
    params.status ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildOrdersQs(params: OrderListParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchOrders(params: OrderListParams): Promise<OrderList> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/orders?${buildOrdersQs(params)}`,
  );
  return OrderListSchema.parse(raw);
}

export function useOrders(params: OrderListParams, initial?: OrderList) {
  const seeded =
    initial !== undefined && (params.page ?? 0) === 0 && !params.status;
  return useQuery({
    queryKey: ordersKey(params),
    queryFn: () => fetchOrders(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- detail ----------------------------------------------------------------

async function fetchOrder(id: string): Promise<OrderDetail> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/orders/${encodeURIComponent(id)}`,
  );
  return OrderDetailSchema.parse(raw);
}

export function useOrder(id: string | null, initial?: OrderDetail) {
  return useQuery({
    queryKey: [ECOMMERCE_ORDERS_KEY, 'detail', id] as const,
    queryFn: () => fetchOrder(id as string),
    enabled: id !== null,
    initialData: initial,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

// --- mutations -------------------------------------------------------------

/** Invalidate the list + (optionally) one order's detail after a mutation. */
function invalidate(
  qc: ReturnType<typeof useQueryClient>,
  orderId?: string,
) {
  // Refetch a mounted list (no flash) AND drop the inactive seeded cache so a
  // remount re-seeds from the fresh SSR render — an inactive seed-only query is
  // not refetched (TASK-PC-FE-126).
  qc.invalidateQueries({ queryKey: [ECOMMERCE_ORDERS_KEY, 'list'] });
  qc.removeQueries({ queryKey: [ECOMMERCE_ORDERS_KEY, 'list'], type: 'inactive' });
  if (orderId) {
    qc.invalidateQueries({
      queryKey: [ECOMMERCE_ORDERS_KEY, 'detail', orderId],
    });
  }
}

export function useChangeOrderStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: OrderStatusChangeBody }) =>
      apiClient.post<{ orderId: string; status: string }>(
        `/api/ecommerce/orders/${encodeURIComponent(id)}/status`,
        body,
      ),
    onSuccess: (_d, { id }) => invalidate(qc, id),
  });
}
