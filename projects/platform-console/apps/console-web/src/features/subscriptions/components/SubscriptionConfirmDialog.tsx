'use client';

import { useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Reason-capture confirm dialog for a subscription mutation (TASK-PC-FE-183).
 * The producer requires `X-Operator-Reason` on every subscription mutation, so
 * the reason is a required gate; the api core is the fail-safe.
 *
 * Thin wrapper over the shared {@link ConfirmDialog} primitive
 * (TASK-PC-FE-262): the shell (backdrop / `role="dialog"` frame / ARIA /
 * Escape / focus trap / error banner / footer) lives in `shared/ui`, and this
 * file owns only the reason state + the domain body (the optional warning line
 * and the required reason textarea), passed as `children`.
 *
 * The public prop API is UNCHANGED — in particular there is deliberately NO
 * `open` prop: `SubscriptionsScreen` mounts this component only when the dialog
 * is meant to be shown, so `open={true}` is passed to the primitive internally
 * and no caller changes.
 *
 * Adopting the primitive ALSO gives this dialog the Tab-loop focus trap it
 * previously lacked (it was Escape-only) — a documented, intentional a11y
 * improvement per TASK-PC-FE-262 § Goal.
 */
export interface SubscriptionConfirmDialogProps {
  title: string;
  description: string;
  confirmLabel: string;
  /** Extra warning line (e.g. for suspend/cancel) — optional. */
  warning?: string;
  /** Destructive styling for suspend/cancel. */
  destructive?: boolean;
  pending: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function SubscriptionConfirmDialog({
  title,
  description,
  confirmLabel,
  warning,
  destructive = false,
  pending,
  error,
  onConfirm,
  onCancel,
}: SubscriptionConfirmDialogProps) {
  const [reason, setReason] = useState('');
  const reasonRef = useRef<HTMLTextAreaElement>(null);

  const reasonOk = reason.trim().length > 0;
  const canConfirm = reasonOk && !pending;

  return (
    <ConfirmDialog
      open
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      destructive={destructive}
      pending={pending}
      confirmDisabled={!reasonOk}
      errorMessage={error}
      dialogTestId="subscription-confirm-dialog"
      overlayTestId="subscription-confirm-overlay"
      cancelTestId="subscription-confirm-cancel"
      confirmTestId="subscription-confirm-submit"
      errorTestId="subscription-confirm-error"
      initialFocusRef={reasonRef}
      onConfirm={() => canConfirm && onConfirm(reason.trim())}
      onCancel={onCancel}
    >
      {warning && (
        <p
          className="mt-2 text-sm text-destructive"
          data-testid="subscription-confirm-warning"
        >
          {warning}
        </p>
      )}

      <label
        htmlFor="subscription-confirm-reason"
        className="mt-4 block text-sm font-medium text-foreground"
      >
        감사 사유 <span className="text-destructive">*</span>
      </label>
      <textarea
        id="subscription-confirm-reason"
        ref={reasonRef}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={3}
        data-testid="subscription-confirm-reason"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        placeholder="이 작업을 수행하는 이유를 입력하세요"
      />
    </ConfirmDialog>
  );
}
