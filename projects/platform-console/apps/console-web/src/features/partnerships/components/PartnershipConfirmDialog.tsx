'use client';

import { useEffect, useRef, useState } from 'react';

/**
 * Reason-capture confirm dialog for a partnership mutation (TASK-PC-FE-187).
 * Mirrors `features/subscriptions` `SubscriptionConfirmDialog` (the pattern of
 * record) — kept feature-local (no cross-feature import). The producer requires
 * `X-Operator-Reason` on every partnership mutation, so the reason is a
 * required gate (submit disabled while empty); the api core is the fail-safe.
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
      aria-labelledby="partnership-confirm-title"
    >
      <div className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg">
        <h2
          id="partnership-confirm-title"
          className={
            destructive
              ? 'text-lg font-semibold text-destructive'
              : 'text-lg font-semibold text-foreground'
          }
        >
          {title}
        </h2>
        <p className="mt-2 text-sm text-muted-foreground">{description}</p>
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

        {error && (
          <div
            role="alert"
            data-testid="partnership-confirm-error"
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
            data-testid="partnership-confirm-cancel"
            className="rounded-md border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-50"
          >
            취소
          </button>
          <button
            type="button"
            onClick={() => canConfirm && onConfirm(reason.trim())}
            disabled={!canConfirm}
            data-testid="partnership-confirm-submit"
            className={
              destructive
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
