'use client';

import type { Ref } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Presentational body of the {@link OutboundCancelDialog} (TASK-PC-FE-198
 * split) — the heading + description, the post-pick OUTBOUND_ADMIN hint, the
 * reason textarea (controlled) + char-count help, the 409-conflict retry
 * notice, the inline error, and the dismiss / confirm footer. Pure
 * presentation: the {@link OutboundCancelDialog} container owns ALL state
 * (reason value, focus trap, keyboard handling, the 3..500 validation) and
 * passes the resolved values + handlers via props. This body renders inside
 * the container's `role="dialog"` frame so the container's focus trap
 * (`dialogRef`) still sees the textarea + buttons as descendants. Markup +
 * testids preserved verbatim (`outbound-cancel-admin-hint`,
 * `outbound-cancel-reason`, `outbound-cancel-conflict`,
 * `outbound-cancel-error`, `outbound-cancel-dismiss`,
 * `outbound-cancel-confirm`).
 */
export interface OutboundCancelDialogBodyProps {
  titleId: string;
  descId: string;
  reasonId: string;
  orderLabel: string;
  needsAdmin: boolean;
  reason: string;
  onReasonChange: (value: string) => void;
  reasonRef: Ref<HTMLTextAreaElement>;
  reasonMax: number;
  helpText: string;
  conflict: boolean;
  errorMessage?: string | null;
  pending: boolean;
  reasonValid: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function OutboundCancelDialogBody({
  titleId,
  descId,
  reasonId,
  orderLabel,
  needsAdmin,
  reason,
  onReasonChange,
  reasonRef,
  reasonMax,
  helpText,
  conflict,
  errorMessage,
  pending,
  reasonValid,
  onConfirm,
  onCancel,
}: OutboundCancelDialogBodyProps) {
  return (
    <>
      <h2 id={titleId} className="text-lg font-semibold text-foreground">
        출고 주문을 취소할까요?
      </h2>
      <p id={descId} className="mt-2 text-sm text-muted-foreground">
        {orderLabel} 주문을 취소합니다. 예약된 재고가 있으면 해제가
        요청되며(비동기), 완료되면 주문이 CANCELLED 상태가 됩니다. 사유는
        필수입니다.
      </p>

      {needsAdmin && (
        <p
          data-testid="outbound-cancel-admin-hint"
          className="mt-3 rounded-md border border-amber-300/50 bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
        >
          피킹 이후 취소는 관리자(OUTBOUND_ADMIN) 권한이 필요합니다. 권한이
          없으면 취소가 거부될 수 있습니다.
        </p>
      )}

      <div className="mt-4">
        <label
          htmlFor={reasonId}
          className="block text-sm font-medium text-foreground"
        >
          취소 사유 <span className="text-destructive">*</span>
        </label>
        <textarea
          id={reasonId}
          ref={reasonRef}
          value={reason}
          onChange={(e) => onReasonChange(e.target.value)}
          rows={3}
          maxLength={reasonMax}
          aria-describedby={`${reasonId}-help`}
          data-testid="outbound-cancel-reason"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p
          id={`${reasonId}-help`}
          className="mt-1 text-xs text-muted-foreground"
        >
          {helpText}
        </p>
      </div>

      {conflict && (
        <p
          role="status"
          data-testid="outbound-cancel-conflict"
          className="mt-4 rounded-md border border-amber-300/50 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
        >
          주문 상태가 변경되었습니다. 최신 상태를 확인했습니다 — 계속하려면
          다시 시도하세요.
        </p>
      )}

      {errorMessage && (
        <p
          role="alert"
          data-testid="outbound-cancel-error"
          className="mt-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {errorMessage}
        </p>
      )}

      <div className="mt-6 flex justify-end gap-3">
        <Button
          variant="secondary"
          onClick={onCancel}
          disabled={pending}
          data-testid="outbound-cancel-dismiss"
        >
          닫기
        </Button>
        <Button
          onClick={onConfirm}
          disabled={pending || !reasonValid}
          data-testid="outbound-cancel-confirm"
        >
          {pending ? '처리 중…' : '주문 취소'}
        </Button>
      </div>
    </>
  );
}
