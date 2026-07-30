'use client';

import { useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Reason-capture confirm dialog for a partnership mutation (TASK-PC-FE-187).
 * The producer requires `X-Operator-Reason` on every partnership mutation, so
 * the reason is a required gate (submit disabled while empty); the api core is
 * the fail-safe.
 *
 * Thin wrapper over the shared {@link ConfirmDialog} primitive
 * (TASK-PC-FE-262): the shell (backdrop / `role="dialog"` frame / ARIA /
 * Escape / focus trap / error banner / footer) lives in `shared/ui`, and this
 * file owns only the reason state + the domain body (the optional warning line
 * and the required reason textarea), passed as `children`.
 *
 * The public prop API is UNCHANGED — in particular there is deliberately NO
 * `open` prop: `PartnershipsScreen` mounts this component only when the dialog
 * is meant to be shown, so `open={true}` is passed to the primitive
 * internally and no caller changes.
 *
 * Adopting the primitive ALSO gives this dialog the Tab-loop focus trap it
 * previously lacked (it was Escape-only) — a documented, intentional a11y
 * improvement per TASK-PC-FE-262 § Goal, bringing it in line with the six
 * sibling confirm dialogs that already had one.
 */
export interface PartnershipConfirmDialogProps {
  title: string;
  description: string;
  confirmLabel: string;
  /** Extra warning line (e.g. for terminate) — optional. */
  warning?: string;
  /** Destructive styling for terminate. */
  destructive?: boolean;
  pending: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function PartnershipConfirmDialog({
  title,
  description,
  confirmLabel,
  warning,
  destructive = false,
  pending,
  error,
  onConfirm,
  onCancel,
}: PartnershipConfirmDialogProps) {
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
      dialogTestId="partnership-confirm-dialog"
      overlayTestId="partnership-confirm-overlay"
      cancelTestId="partnership-confirm-cancel"
      confirmTestId="partnership-confirm-submit"
      errorTestId="partnership-confirm-error"
      initialFocusRef={reasonRef}
      onConfirm={() => canConfirm && onConfirm(reason.trim())}
      onCancel={onCancel}
    >
      {warning && (
        <p
          className="mt-2 text-sm text-destructive"
          data-testid="partnership-confirm-warning"
        >
          {warning}
        </p>
      )}

      <label
        htmlFor="partnership-confirm-reason"
        className="mt-4 block text-sm font-medium text-foreground"
      >
        감사 사유 <span className="text-destructive">*</span>
      </label>
      <textarea
        id="partnership-confirm-reason"
        ref={reasonRef}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={3}
        data-testid="partnership-confirm-reason"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        placeholder="이 작업을 수행하는 이유를 입력하세요 (감사 기록에 남습니다)"
      />
    </ConfirmDialog>
  );
}
