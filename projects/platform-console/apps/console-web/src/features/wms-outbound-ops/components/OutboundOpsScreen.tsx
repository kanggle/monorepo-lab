'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useOutboundOrders, useOrderDrill } from '../hooks/use-outbound-ops';
import { useOutboundActionDialog } from '../hooks/use-outbound-action-dialog';
import { useOutboundCancelDialog } from '../hooks/use-outbound-cancel-dialog';
import { useOutboundRetryDialog } from '../hooks/use-outbound-retry-dialog';
import {
  OUTBOUND_DEFAULT_PAGE_SIZE,
  canPick,
  canPack,
  canShip,
  canCancel,
  canRetryDispatch,
  type OutboundOrderPage,
  type OutboundListParams,
} from '../api/types';
import { OutboundActionDialog } from './OutboundActionDialog';
import { OutboundCancelDialog } from './OutboundCancelDialog';
import { OutboundOpsHeader } from './OutboundOpsHeader';
import { OutboundOrdersTable } from './OutboundOrdersTable';
import { OutboundOrderDrill } from './OutboundOrderDrill';
import { ACTION_COPY } from './outbound-ops-helpers';

/**
 * wms outbound operations section (TASK-PC-FE-057 — ADR-MONO-022 § D7 operator
 * leg). The SECOND wms surface federated by the console.
 *
 * Server-rendered initial order page is passed in; client re-query handles
 * status-filter / pagination changes and the order drill (detail + saga).
 *
 * Three CONFIRM-GATED lifecycle-advance actions, each enabled ONLY for the
 * valid current status/saga (the producer is the final authority — a server
 * 422 STATE_TRANSITION_INVALID / 409 CONFLICT is still handled inline):
 *   - Pick   : status PICKING + saga RESERVED  (confirm-as-planned)
 *   - Pack   : status PICKED / PACKING         (create-unit + seal, server-side)
 *   - Ship   : status PACKED
 *
 * Each action is REASON-FREE (the wms outbound surface has no
 * `X-Operator-Reason`; the confirm dialog is the security gate). On
 * `409 CONFLICT` the drill refetches and a "state changed — review and retry"
 * prompt is surfaced (no silent auto-retry).
 *
 * Resilience (§ 2.5): 401 is handled by the server route (whole-session
 * re-login); 403/404/422/409 → inline actionable; 503/timeout → this section
 * degrades only (the console shell stays intact).
 *
 * ── MODULE SPLIT (TASK-PC-FE-101) ── this container owns ALL state and the
 * status/saga gating; the orders-table region and the order-drill region are
 * rendered by the prop-driven `OutboundOrdersTable` / `OutboundOrderDrill`
 * presentational children, and the pure copy/error-map helpers live in
 * `outbound-ops-helpers.ts`.
 *
 * ── SPLIT (TASK-PC-FE-198) ── the heading band moved to the presentational
 * `OutboundOpsHeader`.
 *
 * ── SPLIT (TASK-PC-FE-214) ── the three mutation-dialog lifecycles (pick/pack/
 * ship, cancel, TMS-retry — state + idempotency-key / reason-capture / conflict
 * handling) moved to the `useOutboundActionDialog` / `useOutboundCancelDialog` /
 * `useOutboundRetryDialog` hooks; this container keeps the orders/drill queries,
 * the status/saga gating, and the assembly.
 */

export interface OutboundOpsScreenProps {
  orders: OutboundOrderPage;
}

export function OutboundOpsScreen({ orders }: OutboundOpsScreenProps) {
  const statusFid = useId();

  const [statusFilter, setStatusFilter] = useState('');
  const [query, setQuery] = useState<OutboundListParams>({
    page: 0,
    size: orders.page.size || OUTBOUND_DEFAULT_PAGE_SIZE,
  });

  const seeded =
    (query.page ?? 0) === 0 && !query.status && !query.warehouseId && !query.orderNo;

  const ordersQ = useOutboundOrders(query, seeded ? orders : undefined);
  const ordersData = ordersQ.data ?? orders;

  const ordersApiError =
    ordersQ.error instanceof ApiError ? (ordersQ.error as ApiError) : null;
  const ordersForbidden = ordersApiError?.status === 403;
  const ordersDegraded =
    ordersQ.isError &&
    (!ordersApiError || ordersApiError.status >= 500) &&
    !ordersForbidden;

  // --- order drill ---------------------------------------------------------
  const [drillOrderId, setDrillOrderId] = useState<string | null>(null);
  const drill = useOrderDrill(drillOrderId);
  const drillApiError =
    drill.error instanceof ApiError ? (drill.error as ApiError) : null;
  const drillForbidden = drillApiError?.status === 403;
  const drillDegraded =
    drill.isError && (!drillApiError || drillApiError.status >= 500) && !drillForbidden;

  // --- mutation-dialog lifecycles (state + idempotency-key / conflict) ------
  const refetchDrill = () => {
    drill.refetch();
  };
  const actionDialog = useOutboundActionDialog(refetchDrill);
  const cancelDialog = useOutboundCancelDialog(refetchDrill);
  const retryDialog = useOutboundRetryDialog();

  function submitStatusFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter || undefined,
      page: 0,
      size: orders.page.size || OUTBOUND_DEFAULT_PAGE_SIZE,
    });
  }

  const drillDetail = drill.data?.detail ?? null;
  const drillSaga = drill.data?.saga ?? null;
  const drillStatus = drillDetail?.status;
  const drillSagaState = drillSaga?.state ?? null;

  const pickEnabled = useMemo(
    () => canPick(drillStatus, drillSagaState),
    [drillStatus, drillSagaState],
  );
  const packEnabled = useMemo(() => canPack(drillStatus), [drillStatus]);
  const shipEnabled = useMemo(() => canShip(drillStatus), [drillStatus]);
  const cancelVisible = useMemo(() => canCancel(drillStatus), [drillStatus]);
  // Dispatch retry: surfaced for a SHIPPED order (a carrier dispatch exists only
  // after shipping). The "does a retry apply?" signal is the logistics dispatch
  // status, resolved server-side at action time — NOT the wms saga state.
  const retryVisible = useMemo(
    () => canRetryDispatch(drillStatus),
    [drillStatus],
  );
  // Async-cancel hint: order CANCELLED but the saga still releasing inventory.
  const cancelPending = useMemo(
    () =>
      drillStatus === 'CANCELLED' &&
      drillSagaState === 'CANCELLATION_REQUESTED',
    [drillStatus, drillSagaState],
  );

  const { action } = actionDialog;
  const { cancelTarget } = cancelDialog;
  const { retryTarget } = retryDialog;

  return (
    <section aria-labelledby="wms-outbound-heading">
      <OutboundOpsHeader />

      <OutboundOrdersTable
        statusFid={statusFid}
        statusFilter={statusFilter}
        onStatusFilterChange={setStatusFilter}
        onSubmitFilter={submitStatusFilter}
        ordersForbidden={ordersForbidden}
        ordersDegraded={ordersDegraded}
        ordersData={ordersData}
        query={query}
        onPrevPage={() =>
          setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
        }
        onNextPage={() => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))}
        onDrill={setDrillOrderId}
      />

      {/* ── Order drill (lines + saga + actions) ─────────────────────────── */}
      {drillOrderId !== null && (
        <OutboundOrderDrill
          loading={drill.isLoading}
          forbidden={drillForbidden}
          degraded={drillDegraded}
          detail={drillDetail}
          saga={drillSaga}
          status={drillStatus}
          pickEnabled={pickEnabled}
          packEnabled={packEnabled}
          shipEnabled={shipEnabled}
          cancelVisible={cancelVisible}
          retryVisible={retryVisible}
          cancelPending={cancelPending}
          actionPending={actionDialog.actionPending}
          cancelMutationPending={cancelDialog.cancelPending}
          retryMutationPending={retryDialog.retryPending}
          onClose={() => setDrillOrderId(null)}
          onAction={actionDialog.openAction}
          onCancel={cancelDialog.openCancel}
          onRetry={retryDialog.openRetry}
        />
      )}

      <OutboundActionDialog
        open={action !== null}
        title={action ? ACTION_COPY[action.kind].title : ''}
        description={action ? ACTION_COPY[action.kind].description : ''}
        confirmLabel={action ? ACTION_COPY[action.kind].confirmLabel : ''}
        pending={actionDialog.activeMutationPending}
        errorMessage={actionDialog.actionError}
        conflict={actionDialog.actionConflict}
        onConfirm={actionDialog.confirmAction}
        onCancel={actionDialog.closeAction}
      />

      <OutboundCancelDialog
        open={cancelTarget !== null}
        orderLabel={cancelTarget?.orderLabel ?? ''}
        needsAdmin={cancelTarget?.needsAdmin ?? false}
        pending={cancelDialog.cancelPending}
        errorMessage={cancelDialog.cancelError}
        conflict={cancelDialog.cancelConflict}
        onConfirm={cancelDialog.confirmCancel}
        onCancel={cancelDialog.closeCancel}
      />

      {/* Dispatch retry confirm — reason-free (reuse the generic action dialog). */}
      <OutboundActionDialog
        open={retryTarget !== null}
        title="발송을 다시 시도할까요?"
        description="택배사 발송(dispatch)을 다시 시도합니다. 재고는 이미 차감되어 있고, 택배사 발송만 재실행됩니다. 이미 발송된 경우 중복 없이 무시됩니다."
        confirmLabel="발송 재시도"
        pending={retryDialog.retryPending}
        errorMessage={retryDialog.retryError}
        onConfirm={retryDialog.confirmRetry}
        onCancel={retryDialog.closeRetry}
      />
    </section>
  );
}
