'use client';

import { useId, useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Reason-capture confirm dialog for an operator-group mutation
 * (TASK-PC-FE-250). Every group mutation (create / rename / describe /
 * delete / member add·remove / grant add·revoke) requires a non-empty
 * operator-entered audit reason (the general `/api/admin/*` rule →
 * `X-Operator-Reason`); this dialog is the ONE gate that captures it before
 * the (already-validated) draft actually fires.
 *
 * Thin wrapper (TASK-PC-FE-268) — delegates the shell (backdrop/frame/ARIA/
 * focus-trap/Escape/error banner/footer) to `shared/ui/ConfirmDialog`
 * (superseding this file's own doc comment's former "mirrors
 * `features/org-hierarchy` `OrgReasonDialog` — kept feature-local, no
 * cross-feature import" rationale, which predates `shared/ui/ConfirmDialog`
 * and no longer applies now that the target is `shared/`, not another
 * feature — see TASK-PC-FE-268's identical treatment of `OrgReasonDialog`
 * itself). No `open` prop — the parent conditionally mounts this component,
 * so it passes `open={true}` to the primitive internally.
 *
 * Two documented, intentional behavior changes from the migration (same
 * precedent TASK-PC-FE-262 already established for `PartnershipConfirmDialog`
 * / `SubscriptionConfirmDialog` / `TenantConfirmDialog`, and this task's own
 * `OrgReasonDialog` migration):
 *   - Gains a Tab-loop focus trap it previously lacked.
 *   - `Escape` now cancels even while `pending` (previously gated on
 *     `!pending`).
 */
export interface GroupReasonDialogProps {
  title: string;
  description: string;
  confirmLabel: string;
  tone?: 'default' | 'destructive';
  pending: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function GroupReasonDialog({
  title,
  description,
  confirmLabel,
  tone = 'default',
  pending,
  error,
  onConfirm,
  onCancel,
}: GroupReasonDialogProps) {
  const reasonId = useId();
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const [reason, setReason] = useState('');

  const reasonOk = reason.trim().length > 0;

  return (
    <ConfirmDialog
      open
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      destructive={tone === 'destructive'}
      pending={pending}
      confirmDisabled={!reasonOk}
      errorMessage={error}
      dialogTestId="group-reason-dialog"
      overlayTestId="group-reason-overlay"
      cancelTestId="group-reason-cancel"
      confirmTestId="group-reason-submit"
      errorTestId="group-reason-error"
      initialFocusRef={reasonRef}
      onConfirm={() => onConfirm(reason.trim())}
      onCancel={onCancel}
    >
      <label
        htmlFor={reasonId}
        className="mt-4 block text-sm font-medium text-foreground"
      >
        감사 사유 <span className="text-destructive">*</span>
      </label>
      <textarea
        id={reasonId}
        ref={reasonRef}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={3}
        data-testid="group-reason-input"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        placeholder="이 작업을 수행하는 이유를 입력하세요"
      />
    </ConfirmDialog>
  );
}
