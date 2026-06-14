'use client';

import { useId, useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
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
 */

export interface OutboundOpsScreenProps {
  orders: OutboundOrderPage;
}

type ActionKind = 'pick' | 'pack' | 'ship';

const STATUS_FILTER_OPTIONS = [
  '',
  'PICKING',
  'PICKED',
  'PACKING',
  'PACKED',
  'SHIPPED',
  'CANCELLED',
  'BACKORDERED',
] as const;

const ACTION_COPY: Record<
  ActionKind,
  { title: string; description: string; confirmLabel: string }
> = {
  pick: {
    title: '피킹을 확정할까요?',
    description:
      '시스템이 계획한 피킹(위치·수량)을 그대로 확정합니다. 콘솔은 위치/수량을 임의로 만들지 않습니다. 이 작업은 멱등하게 한 번만 반영됩니다.',
    confirmLabel: '피킹 확정',
  },
  pack: {
    title: '패킹을 진행할까요?',
    description:
      '주문 라인 전체로 패킹 단위를 생성하고 봉인(seal)합니다. 두 번의 호출(생성 → 봉인)이 각각 멱등하게 처리됩니다. 완료되면 주문이 PACKED 상태가 됩니다.',
    confirmLabel: '패킹 진행',
  },
  ship: {
    title: '출고를 확정할까요?',
    description:
      '출고(shipment)를 확정합니다. 완료되면 주문이 SHIPPED 상태가 되고, 연결된 ecommerce 주문도 출고 완료로 전환됩니다. 이 작업은 멱등하게 한 번만 반영됩니다.',
    confirmLabel: '출고 확정',
  },
};

/** Cancel-specific producer error → inline operator message (§ 1.4 errors). */
function cancelErrorMessage(code: string): string {
  switch (code) {
    case 'ORDER_ALREADY_SHIPPED':
      return '이미 출고된 주문은 취소할 수 없습니다.';
    case 'STATE_TRANSITION_INVALID':
      return '주문 상태가 변경되어 취소할 수 없습니다. 목록을 새로고침하세요.';
    case 'FORBIDDEN':
      return '취소 권한이 없습니다. 피킹 이후 취소는 관리자(OUTBOUND_ADMIN) 권한이 필요합니다.';
    default:
      return messageForCode(code, '주문을 취소하지 못했습니다.');
  }
}

/** TMS-retry-specific producer error → inline operator message (§ 4.3 +
 *  the proxy's SHIPMENT_NOT_FOUND). */
function retryTmsErrorMessage(code: string): string {
  switch (code) {
    case 'SHIPMENT_NOT_FOUND':
      return '출고 건을 찾을 수 없습니다. TMS 재전송 대상이 없습니다.';
    case 'STATE_TRANSITION_INVALID':
      return '이미 정상 통보되었거나 재전송 대상 상태가 아닙니다. 목록을 새로고침하세요.';
    case 'FORBIDDEN':
      return 'TMS 재전송 권한이 없습니다. 관리자(OUTBOUND_ADMIN) 권한이 필요합니다.';
    case 'DUPLICATE_REQUEST':
      return '이미 재전송 요청이 접수되었습니다 (중복 무시).';
    default:
      return messageForCode(code, 'TMS 재전송을 처리하지 못했습니다.');
  }
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

  const rows = ordersData.content;
  const totalPages = Math.max(1, ordersData.page.totalPages);

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
        WMS 출고 운영
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        출고 주문 목록 · 주문 상세(라인 + saga) · 피킹 → 패킹 → 출고 확정.
        ecommerce 주문이 자동 생성한 출고 주문을 콘솔에서 SHIPPED 까지
        진행합니다.
      </p>

      {/* ── Orders table ─────────────────────────────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">출고 주문</h2>
      <form
        onSubmit={submitStatusFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="출고 주문 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            data-testid="outbound-status-filter"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {STATUS_FILTER_OPTIONS.map((s) => (
              <option key={s || 'all'} value={s}>
                {s || '전체'}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="outbound-filter-submit">
          조회
        </Button>
      </form>

      {ordersForbidden ? (
        <div
          role="status"
          data-testid="outbound-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : ordersDegraded ? (
        <div
          role="status"
          data-testid="outbound-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 출고 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="outbound-empty"
        >
          표시할 출고 주문이 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="outbound-table">
            <caption className="sr-only">출고 주문 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  주문번호
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  saga
                </th>
                <th scope="col" className="p-2">
                  라인 수
                </th>
                <th scope="col" className="p-2">
                  생성 시각 (UTC)
                </th>
                <th scope="col" className="p-2">
                  작업
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((o, i) => (
                <tr
                  key={o.orderId}
                  data-testid={`outbound-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{o.orderNo ?? o.orderId}</td>
                  <td className="p-2" data-testid={`outbound-row-status-${i}`}>
                    {o.status ?? '—'}
                  </td>
                  <td className="p-2">{o.sagaState ?? '—'}</td>
                  <td className="p-2">{o.lineCount ?? '—'}</td>
                  <td className="p-2">{o.createdAt ?? '—'}</td>
                  <td className="p-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setDrillOrderId(o.orderId)}
                      data-testid={`outbound-drill-${i}`}
                    >
                      상세
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="mb-8 flex items-center justify-between"
            aria-label="출고 주문 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
              }
              data-testid="outbound-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="outbound-pageinfo"
            >
              {`${ordersData.page.number + 1} / ${totalPages} 페이지 · 총 ${ordersData.page.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={ordersData.page.number + 1 >= ordersData.page.totalPages}
              onClick={() => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))}
              data-testid="outbound-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}

      {/* ── Order drill (lines + saga + actions) ─────────────────────────── */}
      {drillOrderId !== null && (
        <section
          aria-labelledby="outbound-drill-heading"
          data-testid="outbound-drill"
          className="rounded-md border border-border p-4"
        >
          <div className="mb-3 flex items-center justify-between">
            <h2
              id="outbound-drill-heading"
              className="text-lg font-medium text-foreground"
            >
              주문 상세
            </h2>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setDrillOrderId(null)}
              data-testid="outbound-drill-close"
            >
              닫기
            </Button>
          </div>

          {drill.isLoading ? (
            <p
              className="text-sm text-muted-foreground"
              data-testid="outbound-drill-loading"
            >
              불러오는 중…
            </p>
          ) : drillForbidden ? (
            <div
              role="status"
              data-testid="outbound-drill-forbidden"
              className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
            >
              {messageForCode('FORBIDDEN')}
            </div>
          ) : drillDegraded || !drillDetail || !drillSaga ? (
            <div
              role="status"
              data-testid="outbound-drill-degraded"
              className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
            >
              주문 상세를 일시적으로 불러올 수 없습니다. 잠시 후 다시
              시도하세요.
            </div>
          ) : (
            <>
              <dl className="mb-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
                <div>
                  <dt className="text-muted-foreground">주문번호</dt>
                  <dd data-testid="outbound-drill-orderno">
                    {drillDetail.orderNo ?? drillDetail.orderId}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">상태</dt>
                  <dd data-testid="outbound-drill-status">
                    {drillDetail.status}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">saga 상태</dt>
                  <dd data-testid="outbound-drill-saga">{drillSaga.state}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">version</dt>
                  <dd>{drillDetail.version}</dd>
                </div>
              </dl>

              <table
                className="mb-4 data-table"
                data-testid="outbound-drill-lines"
              >
                <caption className="sr-only">주문 라인</caption>
                <thead>
                  <tr className="border-b border-border text-left">
                    <th scope="col" className="p-2">
                      라인
                    </th>
                    <th scope="col" className="p-2">
                      SKU
                    </th>
                    <th scope="col" className="p-2">
                      로트
                    </th>
                    <th scope="col" className="p-2">
                      주문 수량
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {drillDetail.lines.map((l, i) => (
                    <tr
                      key={l.orderLineId}
                      data-testid={`outbound-line-${i}`}
                      className="border-b border-border"
                    >
                      <td className="p-2">{l.lineNo ?? i + 1}</td>
                      <td className="p-2">{l.skuId}</td>
                      <td className="p-2">{l.lotId ?? '—'}</td>
                      <td className="p-2">{l.qtyOrdered}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* Confirm-gated + status/saga-gated lifecycle actions. */}
              <div className="flex flex-wrap gap-3">
                <Button
                  onClick={() => openAction('pick', drillDetail.orderId)}
                  disabled={!pickEnabled || actionPending}
                  data-testid="outbound-action-pick"
                >
                  피킹 확정
                </Button>
                <Button
                  onClick={() => openAction('pack', drillDetail.orderId)}
                  disabled={!packEnabled || actionPending}
                  data-testid="outbound-action-pack"
                >
                  패킹 진행
                </Button>
                <Button
                  onClick={() => openAction('ship', drillDetail.orderId)}
                  disabled={!shipEnabled || actionPending}
                  data-testid="outbound-action-ship"
                >
                  출고 확정
                </Button>
              </div>

              {/* Cancel — the one NON-forward action (reason-required,
                  role-escalating). Visible for cancellable statuses only. */}
              {cancelVisible && (
                <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-border pt-3">
                  <Button
                    variant="secondary"
                    onClick={() =>
                      openCancel(
                        drillDetail.orderId,
                        drillDetail.orderNo ?? drillDetail.orderId,
                        drillStatus,
                      )
                    }
                    disabled={cancel.isPending}
                    data-testid="outbound-action-cancel-order"
                  >
                    주문 취소
                  </Button>
                  {cancelNeedsAdmin(drillStatus) && (
                    <span
                      className="text-xs text-muted-foreground"
                      data-testid="outbound-cancel-admin-note"
                    >
                      피킹 이후 취소는 관리자(OUTBOUND_ADMIN) 권한이 필요합니다.
                    </span>
                  )}
                </div>
              )}
              {cancelPending && (
                <p
                  className="mt-2 text-xs text-muted-foreground"
                  data-testid="outbound-cancel-pending-hint"
                >
                  {`취소 요청됨 · 예약 재고 해제 대기 (saga: ${drillSaga.state}).`}
                </p>
              )}

              {/* TMS retry — the recovery admin action for a shipped order whose
                  carrier notification failed (saga SHIPPED_NOT_NOTIFIED). */}
              {retryVisible && (
                <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-border pt-3">
                  <Button
                    variant="secondary"
                    onClick={() => openRetry(drillDetail.orderId)}
                    disabled={retry.isPending}
                    data-testid="outbound-action-retry-tms"
                  >
                    TMS 재전송
                  </Button>
                  <span
                    className="text-xs text-muted-foreground"
                    data-testid="outbound-retry-admin-note"
                  >
                    출고는 완료됐지만 택배사 통보가 실패했습니다. 재전송은
                    관리자(OUTBOUND_ADMIN) 권한이 필요합니다.
                  </span>
                </div>
              )}
              {!pickEnabled && drillStatus === 'PICKING' && (
                <p
                  className="mt-2 text-xs text-muted-foreground"
                  data-testid="outbound-pick-blocked-hint"
                >
                  saga 상태가 RESERVED 가 되면 피킹을 확정할 수 있습니다 (현재:
                  {` ${drillSaga.state}`}).
                </p>
              )}
            </>
          )}
        </section>
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
