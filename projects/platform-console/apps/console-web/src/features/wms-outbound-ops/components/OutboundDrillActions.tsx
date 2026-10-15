'use client';

import { Button } from '@/shared/ui/Button';
import { cancelNeedsAdmin } from '../api/types';
import type { OutboundOrderDetail, OutboundSaga } from '../api/types';
import type { ActionKind } from './outbound-ops-helpers';

/**
 * Order-drill actions region (TASK-PC-FE-198 split) — the confirm-gated +
 * status/saga-gated lifecycle actions (pick / pack / ship), the reason-required
 * cancel affordance, the reason-free dispatch-retry recovery affordance
 * (logistics-service, TASK-PC-FE-258), and the async / pick-blocked hints. Pure
 * presentation: the {@link OutboundOrderDrill}
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
  /**
   * TASK-PC-FE-338 c-2 AC-2 (contract § 2.4.3.4 «Read-only sets hide write buttons») — `false`
   * when the operator holds no `OUTBOUND_WRITE`: pick / pack / ship / cancel hide (outbound-service
   * would `403` them). «발송 재시도» (logistics-service, `authenticated()` only) is not gated by
   * that role and stays. Default `true` (fail-open — the server stays final).
   */
  canWrite?: boolean;
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
  canWrite = true,
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
      {!canWrite && (
        <p
          role="note"
          data-testid="outbound-read-only-note"
          className="text-xs text-muted-foreground"
        >
          조회 전용 — 출고 변경(피킹 · 패킹 · 출고 확정 · 주문 취소)에 필요한 업무 권한이 이
          테넌트에서 배정되지 않아 해당 버튼을 숨겼습니다.
        </p>
      )}
      {/* Confirm-gated + status/saga-gated lifecycle actions. */}
      {canWrite && (
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
      )}

      {/* Cancel — the one NON-forward action (reason-required,
          role-escalating). Visible for cancellable statuses only. */}
      {cancelVisible && canWrite && (
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

      {/* Dispatch retry — the reason-free recovery action for a shipped order
          whose carrier dispatch failed (logistics-service, TASK-PC-FE-258). */}
      {retryVisible && (
        <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-border pt-3">
          <Button
            variant="secondary"
            onClick={() => onRetry(detail.orderId)}
            disabled={retryMutationPending}
            data-testid="outbound-action-retry-dispatch"
          >
            발송 재시도
          </Button>
          <span
            className="text-xs text-muted-foreground"
            data-testid="outbound-retry-dispatch-note"
          >
            택배사 발송(dispatch)이 실패한 경우 다시 시도합니다. 발송이 이미
            접수됐다면 중복 없이 무시됩니다.
          </span>
        </div>
      )}
      {canWrite && !pickEnabled && status === 'PICKING' && (
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
