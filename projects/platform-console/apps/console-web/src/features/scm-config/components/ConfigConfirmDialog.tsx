'use client';

import { useEffect, useId, useRef } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Confirm-gated upsert dialog for an scm seed row (policy / sku-supplier-map)
 * (console-integration-contract § 2.4.6.2 mutation discipline — PUT is an
 * idempotent upsert but mutates seed state, so a confirm step is required UX).
 *
 * Unlike the FE-077 approve/dismiss dialog there is NO reason/note field — the
 * producer's seed PUT carries NO reason (the body IS the full row; NO
 * `X-Operator-Reason`). The dialog just summarises the FULL-row body the
 * operator is about to upsert and makes clear the edit affects FUTURE
 * evaluation only.
 *
 * Invariants:
 *   - `onConfirm()` is NOT called until the operator explicitly confirms.
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open,
 *     `Escape` cancels, focus is trapped, `role="dialog"` + `aria-modal` +
 *     labelled/described. axe-clean.
 */
export interface ConfigConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  /** A read-only summary of the FULL-row body about to be upserted. */
  summary: { label: string; value: string }[];
  pending?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfigConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  summary,
  pending = false,
  errorMessage,
  onConfirm,
  onCancel,
}: ConfigConfirmDialogProps) {
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
      data-testid="config-confirm-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="config-confirm-dialog"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 id={titleId} className="text-lg font-semibold text-foreground">
          {title}
        </h2>
        <p id={descId} className="mt-2 text-sm text-muted-foreground">
          {description}
        </p>

        <dl className="mt-4 grid grid-cols-[auto,1fr] gap-x-4 gap-y-1 text-sm">
          {summary.map((s) => (
            <div key={s.label} className="contents">
              <dt className="font-medium text-muted-foreground">{s.label}</dt>
              <dd className="font-mono text-foreground">{s.value}</dd>
            </div>
          ))}
        </dl>

        <p className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-xs text-muted-foreground">
          이 설정은 <strong>이후(미래)</strong> 보충 추천 평가에만
          반영됩니다. 기존 추천·발주(PO)를 변경하거나 발주를 발행하지 않습니다.
        </p>

        {errorMessage && (
          <p
            role="alert"
            data-testid="config-confirm-error"
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
            data-testid="config-confirm-cancel"
          >
            취소
          </Button>
          <Button
            ref={confirmRef}
            onClick={onConfirm}
            disabled={pending}
            data-testid="config-confirm-submit"
          >
            {pending ? '처리 중…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
