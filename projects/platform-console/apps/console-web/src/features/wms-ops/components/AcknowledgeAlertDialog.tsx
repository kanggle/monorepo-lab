'use client';

import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Confirm-gated alert acknowledge dialog (console-integration-contract
 * § 2.4.5 mutation discipline).
 *
 * REASON-FREE by design — wms's alert-ack does NOT define an
 * `X-Operator-Reason` (carrying GAP's § 2.4.1 reason-capture over is a
 * header-matrix-drift defect). There is therefore NO reason textarea here
 * (the deliberate contrast with the IAM `ConfirmActionDialog`). The single
 * security gate is the explicit confirm (no one-click ack).
 *
 * Thin wrapper (TASK-PC-FE-268) — delegates the shell (backdrop/frame/ARIA/
 * focus-trap/Escape/error banner/footer) to `shared/ui/ConfirmDialog`; no
 * `children` needed (no domain-specific body content).
 */
export interface AcknowledgeAlertDialogProps {
  open: boolean;
  alertLabel: string;
  pending?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

export function AcknowledgeAlertDialog({
  open,
  alertLabel,
  pending = false,
  errorMessage,
  onConfirm,
  onCancel,
}: AcknowledgeAlertDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      title="알림을 확인 처리할까요?"
      description={
        <>
          <span className="font-medium text-foreground">{alertLabel}</span>{' '}
          알림을 확인(acknowledge) 처리합니다. 이 작업은 한 번만
          반영됩니다(멱등). 계속하시겠습니까?
        </>
      }
      confirmLabel="확인 처리"
      pending={pending}
      errorMessage={errorMessage}
      dialogTestId="wms-ack-dialog"
      overlayTestId="wms-ack-overlay"
      cancelTestId="wms-ack-cancel"
      confirmTestId="wms-ack-confirm"
      errorTestId="wms-ack-error"
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}
