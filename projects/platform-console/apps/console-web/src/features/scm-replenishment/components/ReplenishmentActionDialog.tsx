'use client';

import { useEffect, useId, useRef } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Confirm-gated approve / dismiss dialog for a replenishment suggestion
 * (console-integration-contract § 2.4.6.1 mutation discipline).
 *
 * Carries an OPTIONAL note/reason textarea — the reason rides in the request
 * BODY (`{ note }` for approve, `{ reason }` for dismiss), NOT an
 * `X-Operator-Reason` header (demand-planning-api defines none; inventing one
 * is a defect). The note/reason is OPTIONAL (the producer accepts an empty
 * body) — confirming with an empty field is allowed.
 *
 * Invariants:
 *   - `onConfirm(note)` is NOT called until the operator explicitly confirms.
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open,
 *     `Escape` cancels, focus is trapped, `role="dialog"` + `aria-modal` +
 *     labelled/described. axe-clean.
 */
export interface ReplenishmentActionDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  /** Label for the optional note/reason field (e.g. "메모 (선택)"). */
  noteLabel: string;
  noteValue: string;
  onNoteChange: (v: string) => void;
  pending?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ReplenishmentActionDialog({
  open,
  title,
  description,
  confirmLabel,
  noteLabel,
  noteValue,
  onNoteChange,
  pending = false,
  errorMessage,
  onConfirm,
  onCancel,
}: ReplenishmentActionDialogProps) {
  const titleId = useId();
  const descId = useId();
  const noteId = useId();
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
          'button, textarea, [tabindex]:not([tabindex="-1"])',
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
      data-testid="replenishment-action-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="replenishment-action-dialog"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 id={titleId} className="text-lg font-semibold text-foreground">
          {title}
        </h2>
        <p id={descId} className="mt-2 text-sm text-muted-foreground">
          {description}
        </p>

        <div className="mt-4">
          <label
            htmlFor={noteId}
            className="block text-sm font-medium text-foreground"
          >
            {noteLabel}
          </label>
          <textarea
            id={noteId}
            value={noteValue}
            onChange={(e) => onNoteChange(e.target.value)}
            rows={2}
            data-testid="replenishment-action-note"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>

        {errorMessage && (
          <p
            role="alert"
            data-testid="replenishment-action-error"
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
            data-testid="replenishment-action-cancel"
          >
            취소
          </Button>
          <Button
            ref={confirmRef}
            onClick={onConfirm}
            disabled={pending}
            data-testid="replenishment-action-confirm"
          >
            {pending ? '처리 중…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
