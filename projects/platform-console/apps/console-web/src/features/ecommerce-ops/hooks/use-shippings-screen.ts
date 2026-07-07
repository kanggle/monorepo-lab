'use client';

import { useId, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useShippings,
  useUpdateShippingStatus,
  useRefreshTracking,
} from './use-ecommerce-shippings';
import {
  SHIPPING_DEFAULT_PAGE_SIZE,
  type ShippingList,
  type ShippingListParams,
} from '../api/shipping-types';
import { nextStatusLabel } from '../components/shipping-labels';

/**
 * State + query + mutations for {@link ShippingsScreen}
 * (TASK-PC-FE-140 — extracted from the former fat container, behavior-preserving).
 *
 * Owns the status-filter / pagination query, the seeded server-render fallback,
 * the forbidden/degraded/loading derivation, and the three operator mutations
 * (status transition, ship-with-tracking, refresh tracking) with their confirm
 * dialogs and inline error mapping. Render-only concerns stay in the component.
 */
export function useShippingsScreen(shippings: ShippingList) {
  const statusFid = useId();

  const [statusFilter, setStatusFilter] = useState('');
  const [query, setQuery] = useState<ShippingListParams>({
    page: 0,
    size: shippings.size || SHIPPING_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0 && !query.status;
  const listQ = useShippings(query, seeded ? shippings : undefined);
  // Only the seeded (page 0, no filter) query may fall back to the server-rendered
  // `shippings` seed. For a filtered/paginated query, falling back to the seed would
  // flash the full unfiltered list while the new query is still in flight — instead
  // we render a loading placeholder until the real result lands.
  const data = seeded ? listQ.data ?? shippings : listQ.data;
  const loading = data === undefined;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  // --- status transition --------------------------------------------------
  const updateStatus = useUpdateShippingStatus();
  const refreshTracking = useRefreshTracking();

  // Shared pending guard — disables all row actions while any mutation is inflight.
  const isAnyPending = updateStatus.isPending || refreshTracking.isPending;

  // Confirm dialog (for non-SHIPPED transitions — just confirm-gated)
  const [pendingTransition, setPendingTransition] = useState<{
    id: string;
    nextStatus: string;
  } | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);

  // ShipFormDialog (for PREPARING → SHIPPED — needs carrier + trackingNumber).
  // Carries the row's `wmsRouted` so the dialog can gate the WMS-deduct toggle
  // (ADR-MONO-022 D4 v2(c)).
  const [shipDialog, setShipDialog] = useState<{
    id: string;
    wmsRouted: boolean;
  } | null>(null);
  const [shipError, setShipError] = useState<string | null>(null);

  function openTransition(id: string, nextStatus: string, wmsRouted: boolean) {
    if (nextStatus === 'SHIPPED') {
      setShipError(null);
      setShipDialog({ id, wmsRouted });
    } else {
      setTransitionError(null);
      setPendingTransition({ id, nextStatus });
    }
  }

  function confirmTransition() {
    if (!pendingTransition) return;
    setTransitionError(null);
    updateStatus.mutate(
      { id: pendingTransition.id, body: { status: pendingTransition.nextStatus } },
      {
        onSuccess: () => setPendingTransition(null),
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setTransitionError(
            messageForCode(code, '상태를 변경하지 못했습니다.'),
          );
        },
      },
    );
  }

  function confirmShip(payload: {
    carrier: string;
    trackingNumber: string;
    deductWmsInventory: boolean;
  }) {
    if (!shipDialog) return;
    setShipError(null);
    updateStatus.mutate(
      {
        id: shipDialog.id,
        body: {
          status: 'SHIPPED',
          carrier: payload.carrier,
          trackingNumber: payload.trackingNumber,
          // Only send the flag when set (true) — keep a `false` off the wire to
          // match the existing minimal-body convention; the producer defaults it.
          ...(payload.deductWmsInventory
            ? { deductWmsInventory: true }
            : {}),
        },
      },
      {
        onSuccess: () => setShipDialog(null),
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setShipError(messageForCode(code, '배송 시작에 실패했습니다.'));
        },
      },
    );
  }

  function triggerRefresh(id: string) {
    refreshTracking.mutate(id);
  }

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter || undefined,
      page: 0,
      size: shippings.size || SHIPPING_DEFAULT_PAGE_SIZE,
    });
  }

  const rows = data?.content ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

  const pagination = {
    prevDisabled: (query.page ?? 0) <= 0,
    nextDisabled: (data?.page ?? 0) + 1 >= totalPages,
    pageInfo: `${(data?.page ?? 0) + 1} / ${totalPages} 페이지 · 총 ${data?.totalElements ?? 0}건`,
    onPrev: () =>
      setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) })),
    onNext: () => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 })),
  };

  return {
    statusFid,
    statusFilter,
    setStatusFilter,
    submitFilter,
    forbidden,
    degraded,
    loading,
    rows,
    isAnyPending,
    openTransition,
    triggerRefresh,
    pagination,
    // non-SHIPPED transition confirm dialog
    pendingTransition,
    transitionError,
    confirmTransition,
    cancelTransition: () => {
      setPendingTransition(null);
      setTransitionError(null);
    },
    transitionConfirmLabel: pendingTransition
      ? nextStatusLabel(pendingTransition.nextStatus)
      : '확인',
    transitionDescription: pendingTransition
      ? `배송 #${pendingTransition.id} 상태를 "${nextStatusLabel(pendingTransition.nextStatus)}"(으)로 변경합니다.`
      : '',
    updateStatusPending: updateStatus.isPending,
    // PREPARING → SHIPPED dialog
    shipDialog,
    shipError,
    confirmShip,
    cancelShip: () => {
      setShipDialog(null);
      setShipError(null);
    },
  };
}
