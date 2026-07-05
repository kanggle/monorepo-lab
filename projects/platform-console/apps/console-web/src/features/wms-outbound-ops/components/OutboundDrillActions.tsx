'use client';

import { Button } from '@/shared/ui/Button';
import { cancelNeedsAdmin } from '../api/types';
import type { OutboundOrderDetail, OutboundSaga } from '../api/types';
import type { ActionKind } from './outbound-ops-helpers';

/**
 * Order-drill actions region (TASK-PC-FE-198 split) — the confirm-gated +
 * status/saga-gated lifecycle actions (pick / pack / ship), the reason-required
 * cancel affordance, the TMS-retry recovery affordance, and the async /
 * pick-blocked hints. Pure presentation: the {@link OutboundOrderDrill}
 * container owns all state + mutations + the status/saga gating and passes the
 * resolved flags + handlers via props.
 */
export interface OutboundDrillActionsProps {
  detail: OutboundOrderDetail;
  saga: OutboundSaga;
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
  onAction: (kind: ActionKind, orderId: string) => void;
  onCancel: (
    orderId: string,
    orderLabel: string,
    status: string | undefined,
  ) => void;
  onRetry: (orderId: string) => void;
}

export function OutboundDrillActions({
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
  onAction,
  onCancel,
  onRetry,
}: OutboundDrillActionsProps) {
  return (
    <>
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
  );
}
