'use client';

import { CloseButton } from '@/shared/ui/CloseButton';
import { messageForCode } from '@/shared/api/errors';
import type { OutboundOrderDetail, OutboundSaga } from '../api/types';
import type { ActionKind } from './outbound-ops-helpers';
import { OutboundDrillSummary } from './OutboundDrillSummary';
import { OutboundDrillLines } from './OutboundDrillLines';
import { OutboundDrillActions } from './OutboundDrillActions';

/**
 * Order-drill region of the wms outbound screen (TASK-PC-FE-101 split) — the
 * order detail (header + lines) + saga state + the confirm-gated lifecycle
 * actions (pick / pack / ship), cancel, and TMS-retry. Pure presentation: the
 * `OutboundOpsScreen` container owns all state + mutations + the status/saga
 * gating and passes everything via props. The container renders this only when
 * a drill order is selected.
 *
 * ── MODULE SPLIT (TASK-PC-FE-198) ── the loaded body is composed from the
 * `OutboundDrillSummary` (field grid), `OutboundDrillLines` (line table), and
 * `OutboundDrillActions` (lifecycle / cancel / retry region) presentational
 * children; this file keeps only the drill frame + loading/forbidden/degraded
 * branching.
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
          <OutboundDrillSummary detail={detail} saga={saga} />

          <OutboundDrillLines lines={detail.lines} />

          <OutboundDrillActions
            detail={detail}
            saga={saga}
            status={status}
            pickEnabled={pickEnabled}
            packEnabled={packEnabled}
            shipEnabled={shipEnabled}
            cancelVisible={cancelVisible}
            retryVisible={retryVisible}
            cancelPending={cancelPending}
            actionPending={actionPending}
            cancelMutationPending={cancelMutationPending}
            retryMutationPending={retryMutationPending}
            onAction={onAction}
            onCancel={onCancel}
            onRetry={onRetry}
          />
        </>
      )}
    </section>
  );
}
