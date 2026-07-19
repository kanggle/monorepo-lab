'use client';

import { useEffect, useRef, useState } from 'react';

/**
 * Reason-capture confirm dialog for an operator-group mutation
 * (TASK-PC-FE-250). Mirrors `features/org-hierarchy` `OrgReasonDialog` (kept
 * feature-local — no cross-feature import). Every group mutation (create /
 * rename / describe / delete / member add·remove / grant add·revoke) requires a
 * non-empty operator-entered audit reason (the general `/api/admin/*` rule →
 * `X-Operator-Reason`); this dialog is the ONE gate that captures it before the
 * (already-validated) draft actually fires.
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
  const [reason, setReason] = useState('');
  const reasonRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    reasonRef.current?.focus();
  }, []);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape' && !pending) onCancel();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel, pending]);

  const reasonOk = reason.trim().length > 0;
  const canConfirm = reasonOk && !pending;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="group-reason-title"
    >
      <div className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg">
        <h2
          id="group-reason-title"
          className="text-lg font-semibold text-foreground"
        >
          {title}
        </h2>
        <p className="mt-2 text-sm text-muted-foreground">{description}</p>

        <label
          htmlFor="group-reason-input"
          className="mt-4 block text-sm font-medium text-foreground"
        >
          감사 사유 <span className="text-destructive">*</span>
        </label>
        <textarea
          id="group-reason-input"
          ref={reasonRef}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          data-testid="group-reason-input"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          placeholder="이 작업을 수행하는 이유를 입력하세요"
        />

        {error && (
          <div
            role="alert"
            data-testid="group-reason-error"
            className="mt-3 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {error}
          </div>
        )}

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={pending}
            data-testid="group-reason-cancel"
            className="rounded-md border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-50"
          >
            취소
          </button>
          <button
            type="button"
            onClick={() => canConfirm && onConfirm(reason.trim())}
            disabled={!canConfirm}
            data-testid="group-reason-submit"
            className={
              tone === 'destructive'
                ? 'rounded-md bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground transition-colors hover:bg-destructive/90 disabled:cursor-not-allowed disabled:opacity-50'
                : 'rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50'
            }
          >
            {pending ? '처리 중…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
