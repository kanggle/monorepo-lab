'use client';

import { useId } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Confirm-gated approve / dismiss dialog for a replenishment suggestion
 * (console-integration-contract § 2.4.6.1 mutation discipline).
 *
 * Thin wrapper over {@link ConfirmDialog} (TASK-PC-FE-262): the shell
 * (backdrop / `role="dialog"` frame / ARIA / Escape / focus trap / error
 * banner / footer) lives in `shared/ui`; this file owns only the
 * domain-specific body — the note/reason textarea — passed as `children`.
 * Renamed from `ReplenishmentActionDialog` in the same task to match the
 * dominant `<Domain>ConfirmDialog` scheme; props and testids are unchanged.
 *
 * Carries an OPTIONAL note/reason textarea — the reason rides in the request
 * BODY (`{ note }` for approve, `{ reason }` for dismiss), NOT an
 * `X-Operator-Reason` header (demand-planning-api defines none; inventing one
 * is a defect). The note/reason is OPTIONAL (the producer accepts an empty
 * body) — confirming with an empty field is allowed.
 *
 * Invariants:
 *   - `onConfirm(note)` is NOT called until the operator explicitly confirms.
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open (the
 *     confirm button — no `initialFocusRef`, the primitive's default),
 *     `Escape` cancels, focus is trapped, `role="dialog"` + `aria-modal` +
 *     labelled/described. axe-clean.
 */
export interface ReplenishmentConfirmDialogProps {
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

export function ReplenishmentConfirmDialog({
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
}: ReplenishmentConfirmDialogProps) {
  const noteId = useId();

  return (
    <ConfirmDialog
      open={open}
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      pending={pending}
      errorMessage={errorMessage}
      dialogTestId="replenishment-action-dialog"
      overlayTestId="replenishment-action-overlay"
      cancelTestId="replenishment-action-cancel"
      confirmTestId="replenishment-action-confirm"
      errorTestId="replenishment-action-error"
      onConfirm={onConfirm}
      onCancel={onCancel}
    >
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
    </ConfirmDialog>
  );
}
