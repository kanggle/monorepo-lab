'use client';

import { useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Reason-capture confirm dialog for a tenant create/update mutation
 * (TASK-PC-FE-226). Every tenant mutation requires a non-empty
 * operator-entered audit reason (the general `/api/admin/*` rule); this dialog
 * is the ONE gate that captures it before the draft (already validated by
 * `TenantForm`) actually fires.
 *
 * Thin wrapper over the shared {@link ConfirmDialog} primitive
 * (TASK-PC-FE-262): the shell (backdrop / `role="dialog"` frame / ARIA /
 * Escape / focus trap / error banner / footer) lives in `shared/ui`, and this
 * file owns only the reason state + the domain body (the required reason
 * textarea — Tenant has no warning line), passed as `children`.
 *
 * The public prop API is UNCHANGED — in particular there is deliberately NO
 * `open` prop: `TenantsScreen` / `TenantDetail` mount this component only when
 * the dialog is meant to be shown, so `open={true}` is passed to the primitive
 * internally and no caller changes.
 *
 * Adopting the primitive ALSO gives this dialog the Tab-loop focus trap it
 * previously lacked (it was Escape-only) — a documented, intentional a11y
 * improvement per TASK-PC-FE-262 § Goal.
 */
export interface TenantConfirmDialogProps {
  title: string;
  description: string;
  confirmLabel: string;
  pending: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function TenantConfirmDialog({
  title,
  description,
  confirmLabel,
  pending,
  error,
  onConfirm,
  onCancel,
}: TenantConfirmDialogProps) {
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
      pending={pending}
      confirmDisabled={!reasonOk}
      errorMessage={error}
      dialogTestId="tenant-confirm-dialog"
      overlayTestId="tenant-confirm-overlay"
      cancelTestId="tenant-confirm-cancel"
      confirmTestId="tenant-confirm-submit"
      errorTestId="tenant-confirm-error"
      initialFocusRef={reasonRef}
      onConfirm={() => canConfirm && onConfirm(reason.trim())}
      onCancel={onCancel}
    >
      <label
        htmlFor="tenant-confirm-reason"
        className="mt-4 block text-sm font-medium text-foreground"
      >
        감사 사유 <span className="text-destructive">*</span>
      </label>
      <textarea
        id="tenant-confirm-reason"
        ref={reasonRef}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={3}
        data-testid="tenant-confirm-reason"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        placeholder="이 작업을 수행하는 이유를 입력하세요"
      />
    </ConfirmDialog>
  );
}
