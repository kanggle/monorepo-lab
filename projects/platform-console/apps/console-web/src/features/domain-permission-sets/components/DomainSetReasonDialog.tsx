'use client';

import { useId, useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * 🔴 Revocation-latency copy (ADR-083 D7 — verbatim meaning, never «즉시»).
 * Every edit / delete confirm says the change applies from the next tenant
 * exchange and that an already-issued assume token keeps working until it
 * expires. **Post-P3 version** (TASK-BE-632 live — TASK-PC-FE-338 c-2 PR 1):
 * «최대 약 6분» = TTL 300 s + the resource servers' 60 s clock skew (the
 * pre-P3 copy said 30 minutes).
 *
 * TASK-PC-FE-338 slice b — promoted to `shared/api/domain-permission-sets-copy.ts`
 * (two more consumers: `features/operators` + `features/operator-groups`,
 * architecture.md § Forbidden Dependencies remedy). Re-exported here so this
 * module's existing import path is unchanged.
 */
export { DOMAIN_SET_LATENCY_COPY } from '@/shared/api/domain-permission-sets-copy';

/**
 * Reason-capture confirm dialog for a 업무 권한 세트 mutation (TASK-PC-FE-338).
 * Every mutation (create / edit / delete) requires a non-empty
 * operator-entered audit reason (`X-Operator-Reason`); this dialog is the ONE
 * gate that captures it before the (already-validated) draft actually fires.
 * Thin wrapper over `shared/ui/ConfirmDialog` (TASK-PC-FE-268 precedent) —
 * mirrors `features/operator-groups` `GroupReasonDialog`, kept feature-local
 * (no cross-feature import; architecture.md § Allowed Dependencies).
 */
export interface DomainSetReasonDialogProps {
  title: string;
  description: string;
  confirmLabel: string;
  tone?: 'default' | 'destructive';
  pending: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function DomainSetReasonDialog({
  title,
  description,
  confirmLabel,
  tone = 'default',
  pending,
  error,
  onConfirm,
  onCancel,
}: DomainSetReasonDialogProps) {
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
      dialogTestId="domain-set-reason-dialog"
      overlayTestId="domain-set-reason-overlay"
      cancelTestId="domain-set-reason-cancel"
      confirmTestId="domain-set-reason-submit"
      errorTestId="domain-set-reason-error"
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
        data-testid="domain-set-reason-input"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        placeholder="이 작업을 수행하는 이유를 입력하세요"
      />
    </ConfirmDialog>
  );
}
