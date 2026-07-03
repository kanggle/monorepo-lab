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
  InventoryPageSchema,
  type InventoryPage,
  type InventoryQueryParams,
  InventoryRowSchema,
  type InventoryRow,
  AlertPageSchema,
  type AlertPage,
  type AlertQueryParams,
  ShipmentPageSchema,
  type ShipmentPage,
  type ShipmentQueryParams,
  AckResultSchema,
  type AckResult,
  WMS_DEFAULT_PAGE_SIZE,
  WMS_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side wms-ops hooks (architecture.md § Server vs Client Components —
 * React Query is client-only). Every call goes to the same-origin
 * `/api/wms/**` proxy (the typed API client's single backend entry point);
 * the proxy attaches the HttpOnly **IAM OIDC access token** server-side —
 * the browser never reads a token or calls wms directly (contract § 2.3).
 *
 * Read-model lag honesty (§ 2.4.5): the read-model is eventually consistent
 * by design — lag is SURFACED (a banner), NOT polled-around. There is NO
 * tight auto-refetch loop / refetchInterval / refetchOnWindowFocus; a
 * re-query is a filter/page change (a new queryKey) or an explicit user
 * retry.
 *
 * Idempotency-Key (§ 2.4.5 / alert-ack): generated ONCE per a single
 * user-confirmed action via `crypto.randomUUID()` and supplied by the
 * confirm-dialog when it fires the mutation; reused only if that exact
 * confirmed action is retried; a fresh confirmed action generates a new
 * key. The hook never fabricates a reason — wms's alert-ack is reason-free
 * (NO `X-Operator-Reason`; confirm-gated in the UI instead).
 */

const WMS_KEY = 'wms-ops';

const clampSize = (size?: number): number =>
  clampPageSize(size, WMS_DEFAULT_PAGE_SIZE, WMS_MAX_PAGE_SIZE);

// --- inventory snapshot read ---------------------------------------------

export function inventoryKey(params: InventoryQueryParams) {
  return [
    WMS_KEY,
    'inventory',
    params.warehouseId ?? null,
    params.locationId ?? null,
    params.skuId ?? null,
    params.lotId ?? null,
    params.lowStockOnly ?? false,
    params.minOnHand ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildInventoryQs(params: InventoryQueryParams): string {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.locationId) qs.set('locationId', params.locationId);
  if (params.skuId) qs.set('skuId', params.skuId);
  if (params.lotId) qs.set('lotId', params.lotId);
  if (params.lowStockOnly) qs.set('lowStockOnly', 'true');
  if (params.minOnHand !== undefined) {
    qs.set('minOnHand', String(params.minOnHand));
  }
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchInventory(
  params: InventoryQueryParams,
): Promise<InventoryPage> {
  const raw = await apiClient.get<unknown>(
    `/api/wms/inventory?${buildInventoryQs(params)}`,
  );
  return InventoryPageSchema.parse(raw);
}

export function useWmsInventory(
  params: InventoryQueryParams,
  initial?: InventoryPage,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.warehouseId &&
    !params.skuId &&
    !params.lotId &&
    !params.locationId &&
    !params.lowStockOnly &&
    params.minOnHand === undefined;
  return useQuery({
    queryKey: inventoryKey(params),
    queryFn: () => fetchInventory(params),
    initialData: seeded ? initial : undefined,
    // Seeded from the server render ⇒ that page is fresh. A filter / page
    // change is a new queryKey → one fresh proxy call. NO refetch interval
    // / NO refetchOnWindowFocus — the read-model lag is surfaced, not
    // polled-around (§ 2.4.5).
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- inventory by-key detail read (TASK-PC-FE-172) -----------------------

/** Composite key — location+sku+lot (no single `[id]`, § the by-key route). */
export interface InventoryByKey {
  locationId: string;
  skuId: string;
  lotId?: string;
}

export function inventoryByKeyKey(key: InventoryByKey | null) {
  return [
    WMS_KEY,
    'inventory-by-key',
    key?.locationId ?? null,
    key?.skuId ?? null,
    key?.lotId ?? null,
  ] as const;
}

async function fetchInventoryByKey(key: InventoryByKey): Promise<InventoryRow> {
  const qs = new URLSearchParams();
  qs.set('locationId', key.locationId);
  qs.set('skuId', key.skuId);
  if (key.lotId) qs.set('lotId', key.lotId);
  const raw = await apiClient.get<unknown>(
    `/api/wms/inventory/by-key?${qs.toString()}`,
  );
  return InventoryRowSchema.parse(raw);
}

/**
 * TASK-PC-FE-172 — the `/wms/inventory` row "상세" panel. `enabled`-gated on
 * a selected composite key (`null` ⇒ no fetch). A `404` (zero stock at that
 * key) surfaces as an `ApiError(404, …)` on `.error` — the screen
 * distinguishes it from a degrade and renders "재고 없음" (no retry storm —
 * `retry: false` inherited from the app's QueryClient defaults; this hook
 * adds none of its own).
 */
export function useWmsInventoryByKey(key: InventoryByKey | null) {
  return useQuery({
    queryKey: inventoryByKeyKey(key),
    queryFn: () => fetchInventoryByKey(key as InventoryByKey),
    enabled: key !== null,
    retry: false,
  });
}

// --- alerts read ----------------------------------------------------------

export function alertsKey(params: AlertQueryParams) {
  return [
    WMS_KEY,
    'alerts',
    params.alertType ?? null,
    params.warehouseId ?? null,
    params.acknowledged ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildAlertsQs(params: AlertQueryParams): string {
  const qs = new URLSearchParams();
  if (params.alertType) qs.set('alertType', params.alertType);
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.acknowledged !== undefined) {
    qs.set('acknowledged', String(params.acknowledged));
  }
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchAlerts(params: AlertQueryParams): Promise<AlertPage> {
  const raw = await apiClient.get<unknown>(
    `/api/wms/alerts?${buildAlertsQs(params)}`,
  );
  return AlertPageSchema.parse(raw);
}

export function useWmsAlerts(
  params: AlertQueryParams,
  initial?: AlertPage,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.alertType &&
    !params.warehouseId &&
    params.acknowledged === undefined;
  return useQuery({
    queryKey: alertsKey(params),
    queryFn: () => fetchAlerts(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- shipments read (carrier code / tracking no) -------------------------

export function shipmentsKey(params: ShipmentQueryParams) {
  return [
    WMS_KEY,
    'shipments',
    params.warehouseId ?? null,
    params.carrierCode ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildShipmentsQs(params: ShipmentQueryParams): string {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.carrierCode) qs.set('carrierCode', params.carrierCode);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchShipments(
  params: ShipmentQueryParams,
): Promise<ShipmentPage> {
  const raw = await apiClient.get<unknown>(
    `/api/wms/shipments?${buildShipmentsQs(params)}`,
  );
  return ShipmentPageSchema.parse(raw);
}

export function useWmsShipments(
  params: ShipmentQueryParams,
  initial?: ShipmentPage,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.warehouseId &&
    !params.carrierCode;
  return useQuery({
    queryKey: shipmentsKey(params),
    queryFn: () => fetchShipments(params),
    initialData: seeded ? initial : undefined,
    // Seeded page-0 ⇒ fresh; a filter/page change is a new queryKey → one
    // fresh proxy call. NO refetch interval / window-focus — the read-model
    // lag is surfaced, not polled-around (§ 2.4.5).
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- alert acknowledge (the ONLY mutation) -------------------------------

interface AckArgs {
  alertId: string;
  /** Stable per the confirmed action (the confirm-dialog generates it via
   *  `crypto.randomUUID()`); fresh per a new confirmed attempt. */
  idempotencyKey: string;
}

export function useAcknowledgeAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ alertId, idempotencyKey }: AckArgs) => {
      // EMPTY body; the proxy adds the Idempotency-Key server-side. NO
      // reason is sent (wms surface does not define X-Operator-Reason).
      const raw = await apiClient.post<unknown>(
        `/api/wms/alerts/${encodeURIComponent(alertId)}/acknowledge`,
        { idempotencyKey },
      );
      return AckResultSchema.parse(raw) as AckResult;
    },
    onSuccess: () => {
      // Refetch the alerts list so the acknowledged row reflects state.
      qc.invalidateQueries({ queryKey: [WMS_KEY, 'alerts'] });
    },
  });
}
