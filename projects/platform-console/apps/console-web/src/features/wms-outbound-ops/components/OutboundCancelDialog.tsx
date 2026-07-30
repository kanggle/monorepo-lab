'use client';

import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Confirm-gated outbound **cancel** dialog (TASK-PC-FE-085 —
 * console-integration-contract § 2.4.5.1 op 9).
 *
 * UNLIKE the reason-free forward {@link OutboundActionDialog} (pick/pack/ship),
 * the wms outbound cancel (`outbound-service-api.md` § 1.4) **requires a reason
 * (3..500 chars)**. So this dialog captures a reason in a textarea, validates
 * the 3..500 bound client-side (submit disabled until valid — no producer call
 * is fabricated for an empty/too-short/too-long reason; the producer is still
 * the final authority), and passes it to `onConfirm(reason)`. The reason rides
 * in the producer JSON body, NOT a header (the wms surface still has no
 * `X-Operator-Reason`).
 *
 * Thin wrapper (TASK-PC-FE-268) — delegates the shell (backdrop/frame/ARIA/
 * focus-trap/Escape/banners/footer) to `shared/ui/ConfirmDialog`; owns only
 * the reason state + the 3..500 validation, rendered via `children`. Its
 * Cancel button reads "닫기" (not the primitive's default "취소"), wired via
 * the `cancelLabel` prop TASK-PC-FE-268 added.
 */

const REASON_MIN = 3;
const REASON_MAX = 500;

export interface OutboundCancelDialogProps {
  open: boolean;
  /** The order being cancelled (for the heading). */
  orderLabel: string;
  /** True for post-pick orders (PICKED/PACKING/PACKED) — surfaces the
   *  "needs OUTBOUND_ADMIN" hint inside the dialog. */
  needsAdmin?: boolean;
  pending?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  /** True after a 409 CONFLICT refetch — surfaces a "retry" affordance copy. */
  conflict?: boolean;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function OutboundCancelDialog({
  open,
  orderLabel,
  needsAdmin = false,
  pending = false,
  errorMessage,
  conflict = false,
  onConfirm,
  onCancel,
}: OutboundCancelDialogProps) {
  const reasonId = useId();
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const [reason, setReason] = useState('');

  // Reset the reason each time the dialog opens (fresh per confirmed attempt).
  useEffect(() => {
    if (open) {
      setReason('');
    }
  }, [open]);

  const trimmed = reason.trim();
  const reasonValid = useMemo(
    () => trimmed.length >= REASON_MIN && trimmed.length <= REASON_MAX,
    [trimmed],
  );

  return (
    <ConfirmDialog
      open={open}
      title="출고 주문을 취소할까요?"
      description={
        <>
          {orderLabel} 주문을 취소합니다. 예약된 재고가 있으면 해제가
          요청되며(비동기), 완료되면 주문이 CANCELLED 상태가 됩니다. 사유는
          필수입니다.
        </>
      }
      confirmLabel="주문 취소"
      cancelLabel="닫기"
      pending={pending}
      confirmDisabled={!reasonValid}
      errorMessage={errorMessage}
      conflict={conflict}
      conflictMessage="주문 상태가 변경되었습니다. 최신 상태를 확인했습니다 — 계속하려면 다시 시도하세요."
      dialogTestId="outbound-cancel-dialog"
      overlayTestId="outbound-cancel-overlay"
      cancelTestId="outbound-cancel-dismiss"
      confirmTestId="outbound-cancel-confirm"
      errorTestId="outbound-cancel-error"
      conflictTestId="outbound-cancel-conflict"
      initialFocusRef={reasonRef}
      onConfirm={() => onConfirm(trimmed)}
      onCancel={onCancel}
    >
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
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          maxLength={REASON_MAX}
          aria-describedby={`${reasonId}-help`}
          data-testid="outbound-cancel-reason"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p id={`${reasonId}-help`} className="mt-1 text-xs text-muted-foreground">
          {REASON_MIN}~{REASON_MAX}자. 현재 {trimmed.length}자.
        </p>
      </div>
    </ConfirmDialog>
  );
}
