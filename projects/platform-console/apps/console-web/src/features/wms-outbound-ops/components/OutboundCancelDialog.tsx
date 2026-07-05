'use client';

import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { OutboundCancelDialogBody } from './OutboundCancelDialogBody';

/**
 * Confirm-gated outbound **cancel** dialog (TASK-PC-FE-085 —
 * console-integration-contract § 2.4.5.1 op 9).
 *
 * UNLIKE the reason-free forward {@link OutboundActionDialog} (pick/pack/ship),
 * the wms outbound cancel (`outbound-service-api.md` § 1.4) **requires a reason
 * (3..500 chars)**. So this dialog captures a reason in a textarea, validates
 * the 3..500 bound client-side (submit disabled until valid — no producer call
 * is fabricated for an empty/too-short/too-long reason; the producer is still
 * the final authority), and passes it to `onConfirm(reason)`. The reason rides
 * in the producer JSON body, NOT a header (the wms surface still has no
 * `X-Operator-Reason`).
 *
 * Invariants (mirror OutboundActionDialog): `onConfirm` is NOT called until the
 * operator explicitly confirms; keyboard-operable + WCAG AA (focus into the
 * dialog on open, `Escape` cancels, focus trapped, `role="dialog"` +
 * `aria-modal` + labelled/described). axe-clean.
 *
 * ── MODULE SPLIT (TASK-PC-FE-198) ── this file keeps ALL orchestration (the
 * reason state, the open-reset + auto-focus effect, the Escape / focus-trap
 * keyboard handler, and the 3..500 validation); the dialog's inner content is
 * rendered by the prop-driven `OutboundCancelDialogBody` presentational child.
 */

const REASON_MIN = 3;
const REASON_MAX = 500;

export interface OutboundCancelDialogProps {
  open: boolean;
  /** The order being cancelled (for the heading). */
  orderLabel: string;
  /** True for post-pick orders (PICKED/PACKING/PACKED) — surfaces the
   *  "needs OUTBOUND_ADMIN" hint inside the dialog. */
  needsAdmin?: boolean;
  pending?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  /** True after a 409 CONFLICT refetch — surfaces a "retry" affordance copy. */
  conflict?: boolean;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function OutboundCancelDialog({
  open,
  orderLabel,
  needsAdmin = false,
  pending = false,
  errorMessage,
  conflict = false,
  onConfirm,
  onCancel,
}: OutboundCancelDialogProps) {
  const titleId = useId();
  const descId = useId();
  const reasonId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const [reason, setReason] = useState('');

  // Reset the reason each time the dialog opens (fresh per confirmed attempt).
  useEffect(() => {
    if (open) {
      setReason('');
      const t = setTimeout(() => reasonRef.current?.focus(), 0);
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

  const trimmed = reason.trim();
  const reasonValid = useMemo(
    () => trimmed.length >= REASON_MIN && trimmed.length <= REASON_MAX,
    [trimmed],
  );

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="outbound-cancel-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="outbound-cancel-dialog"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <OutboundCancelDialogBody
          titleId={titleId}
          descId={descId}
          reasonId={reasonId}
          orderLabel={orderLabel}
          needsAdmin={needsAdmin}
          reason={reason}
          onReasonChange={setReason}
          reasonRef={reasonRef}
          reasonMax={REASON_MAX}
          helpText={`${REASON_MIN}~${REASON_MAX}자. 현재 ${trimmed.length}자.`}
          conflict={conflict}
          errorMessage={errorMessage}
          pending={pending}
          reasonValid={reasonValid}
          onConfirm={() => onConfirm(trimmed)}
          onCancel={onCancel}
        />
      </div>
    </div>
  );
}
