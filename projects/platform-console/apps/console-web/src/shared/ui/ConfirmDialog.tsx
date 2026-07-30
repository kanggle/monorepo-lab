'use client';

import {
  useEffect,
  useId,
  useRef,
  type ReactNode,
  type RefObject,
} from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Shared confirm-gate modal shell (TASK-PC-FE-262).
 *
 * Nine feature-local dialogs (`features/ecommerce-ops` `ConfirmDialog`,
 * `features/wms-outbound-ops` `OutboundActionDialog`, `features/operators`
 * `OperatorConfirmDialog`, `features/accounts` `ConfirmActionDialog`,
 * `features/scm-config` `ConfigConfirmDialog`, `features/scm-replenishment`
 * `ReplenishmentConfirmDialog`, `features/partnerships`
 * `PartnershipConfirmDialog`, `features/subscriptions`
 * `SubscriptionConfirmDialog`, `features/tenants` `TenantConfirmDialog`)
 * each reimplemented the SAME shell — backdrop overlay, `role="dialog"` frame,
 * ARIA labelling, Escape-to-cancel, a Tab-loop focus trap, an inline error
 * banner and a Cancel/Confirm footer. Per `platform/refactoring-policy.md`
 * § Prioritization duplication outranks naming, so the shell is promoted here
 * (the `shared/ui/StatusBadge` / `shared/ui/DetailHeader` precedent documented
 * in `docs/conventions/frontend-ui.md` § 3) and each domain keeps only its own
 * body content, passed as `children`.
 *
 * Domain-specific body content (reason textarea, role editor, summary `<dl>`,
 * note field, typed-confirmation input …) stays feature-local and renders in
 * the `children` slot, BETWEEN the description and the conflict/error banners —
 * the DOM order every migrated component already had.
 *
 * Invariants (unchanged from the nine originals):
 *   - `onConfirm` is NOT called until the operator explicitly confirms.
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open,
 *     `Escape` cancels, focus is trapped to the dialog frame,
 *     `role="dialog"` + `aria-modal` + labelled/described. axe-clean.
 *
 * Per-element `data-testid` values are explicit PROPS, not derived from a
 * prefix: the migrated components' testid suffixes are NOT uniform (six use
 * `…-submit` for the confirm button, three use `…-confirm`; the accounts family
 * carries no domain prefix at all). Deriving them would have silently broken
 * existing assertions.
 *
 * TASK-PC-FE-268 migrated 3 more instances TASK-PC-FE-262 had left
 * unverified (`wms-outbound-ops` `OutboundCancelDialog`, `wms-ops`
 * `AcknowledgeAlertDialog`, `org-hierarchy` `OrgReasonDialog`) and added the
 * optional `cancelLabel` prop (default `'취소'`) for `OutboundCancelDialog`'s
 * `'닫기'` dismiss copy.
 */
export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  /** Cancel-button text (TASK-PC-FE-268). Defaults to '취소' — override for a
   *  caller whose dismiss affordance reads differently (e.g. '닫기'). */
  cancelLabel?: string;
  /** Privilege-high / irreversible action → tints title + confirm button. */
  destructive?: boolean;
  pending?: boolean;
  /** Extra disable condition from the caller (e.g. an empty reason field). */
  confirmDisabled?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  /** True after a 409 CONFLICT refetch — surfaces a "retry" affordance copy. */
  conflict?: boolean;
  /** Conflict copy. Caller-supplied — it differs per producer (order vs
   *  product vs shipment), so there is deliberately NO default here. */
  conflictMessage?: ReactNode;
  /** Domain-specific body, rendered between description and the banners. */
  children?: ReactNode;
  dialogTestId: string;
  overlayTestId?: string;
  cancelTestId: string;
  confirmTestId: string;
  errorTestId?: string;
  conflictTestId?: string;
  /** Element to auto-focus on open. Omit to auto-focus the Confirm button
   *  (the primitive's own ref) — pass e.g. a reason-textarea ref to preserve a
   *  caller's existing "focus the reason field" behavior. */
  initialFocusRef?: RefObject<HTMLElement | null>;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = '취소',
  destructive = false,
  pending = false,
  confirmDisabled = false,
  errorMessage,
  conflict = false,
  conflictMessage,
  children,
  dialogTestId,
  overlayTestId,
  cancelTestId,
  confirmTestId,
  errorTestId,
  conflictTestId,
  initialFocusRef,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const titleId = useId();
  const descId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      const target = initialFocusRef ?? confirmRef;
      const t = setTimeout(() => target.current?.focus(), 0);
      return () => clearTimeout(t);
    }
  }, [open, initialFocusRef]);

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
      data-testid={overlayTestId}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid={dialogTestId}
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2
          id={titleId}
          className={
            destructive
              ? 'text-lg font-semibold text-destructive'
              : 'text-lg font-semibold text-foreground'
          }
        >
          {title}
        </h2>
        <div id={descId} className="mt-2 text-sm text-muted-foreground">
          {description}
        </div>

        {children}

        {conflict && (
          <p
            role="status"
            data-testid={conflictTestId}
            className="mt-4 rounded-md border border-amber-300/50 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
          >
            {conflictMessage}
          </p>
        )}

        {errorMessage && (
          <p
            role="alert"
            data-testid={errorTestId}
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
            data-testid={cancelTestId}
          >
            {cancelLabel}
          </Button>
          <Button
            ref={confirmRef}
            onClick={onConfirm}
            disabled={pending || confirmDisabled}
            className={
              destructive
                ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
                : undefined
            }
            data-testid={confirmTestId}
          >
            {pending ? '처리 중…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
