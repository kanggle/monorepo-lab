'use client';

import { Button } from '@/shared/ui/Button';
import { CloseButton } from '@/shared/ui/CloseButton';
import { messageForCode } from '@/shared/api/errors';
import { cancelNeedsAdmin } from '../api/types';
import type { OutboundOrderDetail, OutboundSaga } from '../api/types';
import type { ActionKind } from './outbound-ops-helpers';

/**
 * Order-drill region of the wms outbound screen (TASK-PC-FE-101 split) — the
 * order detail (header + lines) + saga state + the confirm-gated lifecycle
 * actions (pick / pack / ship), cancel, and TMS-retry. Pure presentation: the
 * `OutboundOpsScreen` container owns all state + mutations + the status/saga
 * gating and passes everything via props. The container renders this only when
 * a drill order is selected.
 */
export interface OutboundOrderDrillProps {
  loading: boolean;
  forbidden: boolean;
  degraded: boolean;
  detail: OutboundOrderDetail | null;
  saga: OutboundSaga | null;
  status: string | undefined;
  pickEnabled: boolean;
  packEnabled: boolean;
  shipEnabled: boolean;
  cancelVisible: boolean;
  retryVisible: boolean;
  cancelPending: boolean;
  actionPending: boolean;
  cancelMutationPending: boolean;
  retryMutationPending: boolean;
  onClose: () => void;
  onAction: (kind: ActionKind, orderId: string) => void;
  onCancel: (
    orderId: string,
    orderLabel: string,
    status: string | undefined,
  ) => void;
  onRetry: (orderId: string) => void;
}

export function OutboundOrderDrill({
  loading,
  forbidden,
  degraded,
  detail,
  saga,
  status,
  pickEnabled,
  packEnabled,
  shipEnabled,
  cancelVisible,
  retryVisible,
  cancelPending,
  actionPending,
  cancelMutationPending,
  retryMutationPending,
  onClose,
  onAction,
  onCancel,
  onRetry,
}: OutboundOrderDrillProps) {
  return (
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
        <CloseButton onClick={onClose} data-testid="outbound-drill-close" />
      </div>

      {loading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="outbound-drill-loading"
        >
          불러오는 중…
        </p>
      ) : forbidden ? (
        <div
          role="status"
          data-testid="outbound-drill-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded || !detail || !saga ? (
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
                {detail.orderNo ?? detail.orderId}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">상태</dt>
              <dd data-testid="outbound-drill-status">
                {detail.status}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">saga 상태</dt>
              <dd data-testid="outbound-drill-saga">{saga.state}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">version</dt>
              <dd>{detail.version}</dd>
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
              {detail.lines.map((l, i) => (
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
              onClick={() => onAction('pick', detail.orderId)}
              disabled={!pickEnabled || actionPending}
              data-testid="outbound-action-pick"
            >
              피킹 확정
            </Button>
            <Button
              onClick={() => onAction('pack', detail.orderId)}
              disabled={!packEnabled || actionPending}
              data-testid="outbound-action-pack"
            >
              패킹 진행
            </Button>
            <Button
              onClick={() => onAction('ship', detail.orderId)}
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
                  onCancel(
                    detail.orderId,
                    detail.orderNo ?? detail.orderId,
                    status,
                  )
                }
                disabled={cancelMutationPending}
                data-testid="outbound-action-cancel-order"
              >
                주문 취소
              </Button>
              {cancelNeedsAdmin(status) && (
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
              {`취소 요청됨 · 예약 재고 해제 대기 (saga: ${saga.state}).`}
            </p>
          )}

          {/* TMS retry — the recovery admin action for a shipped order whose
              carrier notification failed (saga SHIPPED_NOT_NOTIFIED). */}
          {retryVisible && (
            <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-border pt-3">
              <Button
                variant="secondary"
                onClick={() => onRetry(detail.orderId)}
                disabled={retryMutationPending}
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
          {!pickEnabled && status === 'PICKING' && (
            <p
              className="mt-2 text-xs text-muted-foreground"
              data-testid="outbound-pick-blocked-hint"
            >
              saga 상태가 RESERVED 가 되면 피킹을 확정할 수 있습니다 (현재:
              {` ${saga.state}`}).
            </p>
          )}
        </>
      )}
    </section>
  );
}
