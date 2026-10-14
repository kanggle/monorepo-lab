'use client';

import { useId, useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Reason-capture confirm dialog for the per-operator ADR-MONO-083 「업무
 * 권한」 grant/revoke mutations (TASK-PC-FE-338 slice b). Thin wrapper over
 * `shared/ui/ConfirmDialog` — mirrors `features/operator-groups`
 * `GroupReasonDialog` / `features/domain-permission-sets`
 * `DomainSetReasonDialog`, kept feature-local (no cross-feature import;
 * architecture.md § Allowed Dependencies).
 */
export interface OperatorDomainSetReasonDialogProps {
  title: string;
  description: string;
  confirmLabel: string;
  tone?: 'default' | 'destructive';
  pending: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function OperatorDomainSetReasonDialog({
  title,
  description,
  confirmLabel,
  tone = 'default',
  pending,
  error,
  onConfirm,
  onCancel,
}: OperatorDomainSetReasonDialogProps) {
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
      dialogTestId="operator-domain-access-reason-dialog"
      overlayTestId="operator-domain-access-reason-overlay"
      cancelTestId="operator-domain-access-reason-cancel"
      confirmTestId="operator-domain-access-reason-submit"
      errorTestId="operator-domain-access-reason-error"
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
        data-testid="operator-domain-access-reason-input"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        placeholder="이 작업을 수행하는 이유를 입력하세요"
      />
    </ConfirmDialog>
  );
}
