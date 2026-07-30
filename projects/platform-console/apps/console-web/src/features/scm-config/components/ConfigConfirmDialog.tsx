'use client';

import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

/**
 * Confirm-gated upsert dialog for an scm seed row (policy / sku-supplier-map)
 * (console-integration-contract § 2.4.6.2 mutation discipline — PUT is an
 * idempotent upsert but mutates seed state, so a confirm step is required UX).
 *
 * Thin wrapper over the shared {@link ConfirmDialog} primitive
 * (TASK-PC-FE-262): the shell (backdrop / `role="dialog"` frame / ARIA /
 * Escape / focus trap / error banner / footer) lives in `shared/ui`, and this
 * file owns only the domain body — the FULL-row summary `<dl>` and the
 * future-evaluation-only disclaimer — passed as `children`. The public prop
 * API is UNCHANGED, so no caller changes.
 *
 * Unlike the FE-077 approve/dismiss dialog there is NO reason/note field — the
 * producer's seed PUT carries NO reason (the body IS the full row; NO
 * `X-Operator-Reason`). The dialog just summarises the FULL-row body the
 * operator is about to upsert and makes clear the edit affects FUTURE
 * evaluation only.
 *
 * Invariants:
 *   - `onConfirm()` is NOT called until the operator explicitly confirms.
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open (the
 *     confirm button — no `initialFocusRef`, the primitive's default),
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
  return (
    <ConfirmDialog
      open={open}
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      pending={pending}
      errorMessage={errorMessage}
      dialogTestId="config-confirm-dialog"
      overlayTestId="config-confirm-overlay"
      cancelTestId="config-confirm-cancel"
      confirmTestId="config-confirm-submit"
      errorTestId="config-confirm-error"
      onConfirm={onConfirm}
      onCancel={onCancel}
    >
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
    </ConfirmDialog>
  );
}
