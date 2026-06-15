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
  OutboundOrderPageSchema,
  type OutboundOrderPage,
  OutboundOrderDetailSchema,
  type OutboundOrderDetail,
  OutboundSagaSchema,
  type OutboundSaga,
  type OutboundListParams,
  OUTBOUND_DEFAULT_PAGE_SIZE,
  OUTBOUND_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side wms-outbound-ops hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/wms/outbound/**` proxy (the typed API client's single
 * backend entry point); the proxy attaches the HttpOnly **domain-facing IAM
 * OIDC token** server-side — the browser never reads a token or calls wms
 * directly (contract § 2.3 / § 2.4.5.1).
 *
 * No tight auto-refetch loop (no `refetchInterval` / `refetchOnWindowFocus`);
 * a re-query is a filter/page change (a new queryKey) or an explicit user
 * action (a mutation success invalidation, or an order drill).
 *
 * Idempotency-Key (§ 2.4.5.1): generated ONCE per a single user-confirmed
 * action via `crypto.randomUUID()` (in the screen, when the confirm dialog
 * fires) and forwarded to the proxy; the COMPOUND Pack action's two upstream
 * calls each get their own stable key server-side. The hooks never fabricate
 * a reason — the wms outbound surface is reason-free (NO `X-Operator-Reason`).
 */

const OUTBOUND_KEY = 'wms-outbound-ops';

const clampSize = (size?: number): number =>
  clampPageSize(size, OUTBOUND_DEFAULT_PAGE_SIZE, OUTBOUND_MAX_PAGE_SIZE);

// --- list orders ----------------------------------------------------------

export function ordersKey(params: OutboundListParams) {
  return [
    OUTBOUND_KEY,
    'orders',
    params.status ?? null,
    params.warehouseId ?? null,
    params.orderNo ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildOrdersQs(params: OutboundListParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.orderNo) qs.set('orderNo', params.orderNo);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchOrders(
  params: OutboundListParams,
): Promise<OutboundOrderPage> {
  const raw = await apiClient.get<unknown>(
    `/api/wms/outbound?${buildOrdersQs(params)}`,
  );
  return OutboundOrderPageSchema.parse(raw);
}

export function useOutboundOrders(
  params: OutboundListParams,
  initial?: OutboundOrderPage,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.status &&
    !params.warehouseId &&
    !params.orderNo;
  return useQuery({
    queryKey: ordersKey(params),
    queryFn: () => fetchOrders(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- order drill (detail + saga) -----------------------------------------

export interface OrderDrill {
  detail: OutboundOrderDetail;
  saga: OutboundSaga;
}

async function fetchOrderDrill(orderId: string): Promise<OrderDrill> {
  // The detail proxy composes the §1.2 detail + §5.1 saga server-side into a
  // single `{ detail, saga }` envelope (one round-trip from the browser).
  const raw = (await apiClient.get<unknown>(
    `/api/wms/outbound/${encodeURIComponent(orderId)}`,
  )) as { detail: unknown; saga: unknown };
  return {
    detail: OutboundOrderDetailSchema.parse(raw.detail),
    saga: OutboundSagaSchema.parse(raw.saga),
  };
}

export function useOrderDrill(orderId: string | null) {
  return useQuery({
    queryKey: [OUTBOUND_KEY, 'drill', orderId] as const,
    queryFn: () => fetchOrderDrill(orderId as string),
    enabled: orderId !== null,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

// --- mutations (compound Pick/Pack/Ship orchestrated in the proxy) -------

interface ActionArgs {
  orderId: string;
  /** Stable per the confirmed action (the screen generates it via
   *  `crypto.randomUUID()`); fresh per a new confirmed attempt. The proxy
   *  derives per-call keys from it for the compound Pack action. */
  idempotencyKey: string;
}

function useOutboundAction(action: 'pick' | 'pack' | 'ship') {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ orderId, idempotencyKey }: ActionArgs) => {
      // EMPTY-ish body — only the idempotency key. The proxy reads the
      // planned/detail data server-side and builds the producer body (Pick
      // = confirm-as-planned; Pack = create-then-seal). NO reason is sent.
      const raw = await apiClient.post<unknown>(
        `/api/wms/outbound/${encodeURIComponent(orderId)}/${action}`,
        { idempotencyKey },
      );
      return raw;
    },
    onSuccess: (_data, { orderId }) => {
      // Refetch the orders list + the drilled order so the advanced status /
      // saga state reflects.
      qc.invalidateQueries({ queryKey: [OUTBOUND_KEY, 'orders'] });
      qc.invalidateQueries({ queryKey: [OUTBOUND_KEY, 'drill', orderId] });
    },
  });
}

export function usePickAction() {
  return useOutboundAction('pick');
}
export function usePackAction() {
  return useOutboundAction('pack');
}
export function useShipAction() {
  return useOutboundAction('ship');
}

// --- cancel (TASK-PC-FE-085 — reason-required, NOT the reason-free forward
//     ActionArgs). The proxy reads the order version server-side. -----------

interface CancelArgs {
  orderId: string;
  /** REQUIRED operator reason (3..500) — validated in the cancel dialog. */
  reason: string;
  /** Stable per the confirmed cancel; fresh per a new confirmed attempt. */
  idempotencyKey: string;
}

export function useCancelOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ orderId, reason, idempotencyKey }: CancelArgs) => {
      // The reason rides in the body (the proxy adds the Idempotency-Key +
      // resolves the order version server-side). NO X-Operator-Reason header.
      const raw = await apiClient.post<unknown>(
        `/api/wms/outbound/${encodeURIComponent(orderId)}/cancel`,
        { reason, idempotencyKey },
      );
      return raw;
    },
    onSuccess: (_data, { orderId }) => {
      // Refetch the orders list + the drilled order so the CANCELLED status +
      // (possibly async) saga state reflect.
      qc.invalidateQueries({ queryKey: [OUTBOUND_KEY, 'orders'] });
      qc.invalidateQueries({ queryKey: [OUTBOUND_KEY, 'drill', orderId] });
    },
  });
}

// --- TMS retry (TASK-PC-FE-087 — reason-free admin action). The proxy
//     resolves the shipmentId server-side from the admin read-model. ---------

interface RetryTmsArgs {
  orderId: string;
  /** Stable per the confirmed retry; fresh per a new confirmed attempt. */
  idempotencyKey: string;
}

export function useRetryTms() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ orderId, idempotencyKey }: RetryTmsArgs) => {
      // Reason-free (re-notify only). The proxy resolves the shipmentId from
      // the admin read-model, then POSTs the retry with the Idempotency-Key.
      const raw = await apiClient.post<unknown>(
        `/api/wms/outbound/${encodeURIComponent(orderId)}/retry-tms`,
        { idempotencyKey },
      );
      return raw;
    },
    onSuccess: (_data, { orderId }) => {
      // Refetch the orders list + the drilled order so the recovered saga
      // (SHIPPED_NOT_NOTIFIED → COMPLETED) + tmsStatus reflect.
      qc.invalidateQueries({ queryKey: [OUTBOUND_KEY, 'orders'] });
      qc.invalidateQueries({ queryKey: [OUTBOUND_KEY, 'drill', orderId] });
    },
  });
}
