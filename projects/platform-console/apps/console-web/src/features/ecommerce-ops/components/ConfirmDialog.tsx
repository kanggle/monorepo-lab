'use client';

import { useEffect, useId, useRef } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Confirm-gated mutation dialog for the ecommerce product surface
 * (console-integration-contract § 2.4.10 mutation discipline). Mirrors the
 * wms-outbound `OutboundActionDialog` shape — the single security gate is the
 * explicit confirm (no one-click mutate; NO `Idempotency-Key` — the producer
 * defines none, so confirm-gate + producer state guards are the double-submit
 * defence).
 *
 * Optional `children` slot renders a form body (used by the stock-adjust
 * dialog, which needs a delta + reason input before the confirm).
 *
 * Invariants:
 *   - `onConfirm` is NOT called until the operator explicitly confirms.
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open,
 *     `Escape` cancels, focus is trapped, `role="dialog"` + `aria-modal`
 *     + labelled/described.
 */
export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  /** `destructive` (delete) tints the confirm button. */
  tone?: 'default' | 'destructive';
  pending?: boolean;
  /** Disables confirm (e.g. an invalid inline form). */
  confirmDisabled?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  /** True after a 409 CONFLICT refetch — surfaces a "retry" affordance copy. */
  conflict?: boolean;
  children?: React.ReactNode;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = 'default',
  pending = false,
  confirmDisabled = false,
  errorMessage,
  conflict = false,
  children,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
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
          'button, input, textarea, select, [tabindex]:not([tabindex="-1"])',
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
      data-testid="ecommerce-confirm-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="ecommerce-confirm-dialog"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 id={titleId} className="text-lg font-semibold text-foreground">
          {title}
        </h2>
        <p id={descId} className="mt-2 text-sm text-muted-foreground">
          {description}
        </p>

        {children && <div className="mt-4">{children}</div>}

        {conflict && (
          <p
            role="status"
            data-testid="ecommerce-confirm-conflict"
            className="mt-4 rounded-md border border-amber-300/50 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
          >
            상품 상태가 변경되었습니다. 최신 상태를 확인했습니다 — 계속하려면 다시
            시도하세요.
          </p>
        )}

        {errorMessage && (
          <p
            role="alert"
            data-testid="ecommerce-confirm-error"
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
            data-testid="ecommerce-confirm-cancel"
          >
            취소
          </Button>
          <Button
            ref={confirmRef}
            onClick={onConfirm}
            disabled={pending || confirmDisabled}
            className={
              tone === 'destructive'
                ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
                : undefined
            }
            data-testid="ecommerce-confirm-confirm"
          >
            {pending ? '처리 중…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
