'use client';

import { useEffect, useId, useRef } from 'react';
import { Button } from '@/shared/ui/Button';

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
 * Invariants:
 *   - `onConfirm` is NOT called until the operator explicitly confirms
 *     (no one-click ack — task Failure Scenario / § 2.4.5).
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open,
 *     `Escape` cancels, focus is trapped, `role="dialog"` + `aria-modal`
 *     + labelled/described. axe-clean.
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
  const titleId = useId();
  const descId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      const t = setTimeout(() => confirmRef.current?.focus(), 0);
      return () => clearTimeout(t);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onCancel();
      }
      if (e.key === 'Tab' && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button, [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="wms-ack-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="wms-ack-dialog"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 id={titleId} className="text-lg font-semibold text-foreground">
          알림을 확인 처리할까요?
        </h2>
        <p id={descId} className="mt-2 text-sm text-muted-foreground">
          <span className="font-medium text-foreground">{alertLabel}</span>{' '}
          알림을 확인(acknowledge) 처리합니다. 이 작업은 한 번만
          반영됩니다(멱등). 계속하시겠습니까?
        </p>

        {errorMessage && (
          <p
            role="alert"
            data-testid="wms-ack-error"
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
            data-testid="wms-ack-cancel"
          >
            취소
          </Button>
          <Button
            ref={confirmRef}
            onClick={onConfirm}
            disabled={pending}
            data-testid="wms-ack-confirm"
          >
            {pending ? '처리 중…' : '확인 처리'}
          </Button>
        </div>
      </div>
    </div>
  );
}
