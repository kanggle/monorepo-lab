'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useOutboundOrders,
  useOrderDrill,
  usePickAction,
  usePackAction,
  useShipAction,
  useCancelOrder,
  useRetryTms,
} from '../hooks/use-outbound-ops';
import {
  OUTBOUND_DEFAULT_PAGE_SIZE,
  canPick,
  canPack,
  canShip,
  canCancel,
  cancelNeedsAdmin,
  canRetryTms,
  type OutboundOrderPage,
  type OutboundListParams,
} from '../api/types';
import { OutboundActionDialog } from './OutboundActionDialog';
import { OutboundCancelDialog } from './OutboundCancelDialog';
import { OutboundOrdersTable } from './OutboundOrdersTable';
import { OutboundOrderDrill } from './OutboundOrderDrill';
import {
  type ActionKind,
  ACTION_COPY,
  cancelErrorMessage,
  retryTmsErrorMessage,
} from './outbound-ops-helpers';

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
 * ── MODULE SPLIT (TASK-PC-FE-101) ── this container owns ALL state, mutations,
 * and the status/saga gating; the orders-table region and the order-drill
 * region are rendered by the prop-driven `OutboundOrdersTable` /
 * `OutboundOrderDrill` presentational children, and the pure copy/error-map
 * helpers live in `outbound-ops-helpers.ts`.
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

  // --- action dialog -------------------------------------------------------
  const pick = usePickAction();
  const pack = usePackAction();
  const ship = useShipAction();
  const [action, setAction] = useState<{
    kind: ActionKind;
    orderId: string;
    idempotencyKey: string;
  } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionConflict, setActionConflict] = useState(false);

  // --- cancel dialog (reason-required, role-escalating, async-saga) --------
  const cancel = useCancelOrder();
  const [cancelTarget, setCancelTarget] = useState<{
    orderId: string;
    orderLabel: string;
    needsAdmin: boolean;
    idempotencyKey: string;
  } | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [cancelConflict, setCancelConflict] = useState(false);

  // --- TMS retry dialog (reason-free admin action; shipment-id resolved
  //     server-side from the admin read-model) -------------------------------
  const retry = useRetryTms();
  const [retryTarget, setRetryTarget] = useState<{
    orderId: string;
    idempotencyKey: string;
  } | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);

  const activeMutation =
    action?.kind === 'pick' ? pick : action?.kind === 'pack' ? pack : ship;
  const actionPending = pick.isPending || pack.isPending || ship.isPending;

  function openAction(kind: ActionKind, orderId: string) {
    setActionError(null);
    setActionConflict(false);
    // A fresh confirmed attempt → a fresh Idempotency-Key (§ 2.4.5.1
    // stable-per-action / fresh-per-attempt).
    setAction({ kind, orderId, idempotencyKey: crypto.randomUUID() });
  }

  function confirmAction() {
    if (!action) return;
    const m =
      action.kind === 'pick' ? pick : action.kind === 'pack' ? pack : ship;
    m.mutate(
      { orderId: action.orderId, idempotencyKey: action.idempotencyKey },
      {
        onSuccess: () => {
          setAction(null);
          setActionError(null);
          setActionConflict(false);
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          const status = e instanceof ApiError ? e.status : 0;
          if (status === 409 && code === 'CONFLICT') {
            // Optimistic-lock stale version: refetch the order, prompt retry —
            // NEVER silently auto-retry with a bumped version.
            drill.refetch();
            setActionConflict(true);
            setActionError(messageForCode('CONFLICT'));
            return;
          }
          setActionConflict(false);
          setActionError(
            messageForCode(code, '작업을 처리하지 못했습니다.'),
          );
        },
      },
    );
  }

  function openCancel(
    orderId: string,
    orderLabel: string,
    status: string | undefined,
  ) {
    setCancelError(null);
    setCancelConflict(false);
    // A fresh confirmed attempt → a fresh Idempotency-Key.
    setCancelTarget({
      orderId,
      orderLabel,
      needsAdmin: cancelNeedsAdmin(status),
      idempotencyKey: crypto.randomUUID(),
    });
  }

  function confirmCancel(reason: string) {
    if (!cancelTarget) return;
    cancel.mutate(
      {
        orderId: cancelTarget.orderId,
        reason,
        idempotencyKey: cancelTarget.idempotencyKey,
      },
      {
        onSuccess: () => {
          setCancelTarget(null);
          setCancelError(null);
          setCancelConflict(false);
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          const status = e instanceof ApiError ? e.status : 0;
          if (status === 409 && code === 'CONFLICT') {
            // Optimistic-lock stale version → refetch + prompt retry (never a
            // silent auto-retry).
            drill.refetch();
            setCancelConflict(true);
            setCancelError(messageForCode('CONFLICT'));
            return;
          }
          setCancelConflict(false);
          setCancelError(cancelErrorMessage(code));
        },
      },
    );
  }

  function openRetry(orderId: string) {
    setRetryError(null);
    // A fresh confirmed attempt → a fresh Idempotency-Key.
    setRetryTarget({ orderId, idempotencyKey: crypto.randomUUID() });
  }

  function confirmRetry() {
    if (!retryTarget) return;
    retry.mutate(
      { orderId: retryTarget.orderId, idempotencyKey: retryTarget.idempotencyKey },
      {
        onSuccess: () => {
          // The drill refetch reflects the recovered saga (→ COMPLETED) /
          // tmsStatus; if it stayed NOT_NOTIFIED the action re-appears.
          setRetryTarget(null);
          setRetryError(null);
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setRetryError(retryTmsErrorMessage(code));
        },
      },
    );
  }

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
  // TMS retry: SHIPPED order whose saga is stuck at SHIPPED_NOT_NOTIFIED.
  const retryVisible = useMemo(
    () => canRetryTms(drillStatus, drillSagaState),
    [drillStatus, drillSagaState],
  );
  // Async-cancel hint: order CANCELLED but the saga still releasing inventory.
  const cancelPending = useMemo(
    () =>
      drillStatus === 'CANCELLED' &&
      drillSagaState === 'CANCELLATION_REQUESTED',
    [drillStatus, drillSagaState],
  );

  return (
    <section aria-labelledby="wms-outbound-heading">
      <h1
        id="wms-outbound-heading"
        className="mb-2 text-2xl font-semibold"
      >
        WMS 출고
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        출고 주문 목록 · 주문 상세(라인 + saga) · 피킹 → 패킹 → 출고 확정.
      </p>

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
          actionPending={actionPending}
          cancelMutationPending={cancel.isPending}
          retryMutationPending={retry.isPending}
          onClose={() => setDrillOrderId(null)}
          onAction={openAction}
          onCancel={openCancel}
          onRetry={openRetry}
        />
      )}

      <OutboundActionDialog
        open={action !== null}
        title={action ? ACTION_COPY[action.kind].title : ''}
        description={action ? ACTION_COPY[action.kind].description : ''}
        confirmLabel={action ? ACTION_COPY[action.kind].confirmLabel : ''}
        pending={activeMutation.isPending}
        errorMessage={actionError}
        conflict={actionConflict}
        onConfirm={confirmAction}
        onCancel={() => {
          setAction(null);
          setActionError(null);
          setActionConflict(false);
        }}
      />

      <OutboundCancelDialog
        open={cancelTarget !== null}
        orderLabel={cancelTarget?.orderLabel ?? ''}
        needsAdmin={cancelTarget?.needsAdmin ?? false}
        pending={cancel.isPending}
        errorMessage={cancelError}
        conflict={cancelConflict}
        onConfirm={confirmCancel}
        onCancel={() => {
          setCancelTarget(null);
          setCancelError(null);
          setCancelConflict(false);
        }}
      />

      {/* TMS retry confirm — reason-free (reuse the generic action dialog). */}
      <OutboundActionDialog
        open={retryTarget !== null}
        title="TMS 재전송을 시도할까요?"
        description="택배사(TMS) 통보를 다시 시도합니다. 재고는 이미 차감되어 있고, 통보만 재전송됩니다. 성공하면 saga 가 COMPLETED 로 회복됩니다. 관리자(OUTBOUND_ADMIN) 권한이 필요합니다."
        confirmLabel="TMS 재전송"
        pending={retry.isPending}
        errorMessage={retryError}
        onConfirm={confirmRetry}
        onCancel={() => {
          setRetryTarget(null);
          setRetryError(null);
        }}
      />
    </section>
  );
}
