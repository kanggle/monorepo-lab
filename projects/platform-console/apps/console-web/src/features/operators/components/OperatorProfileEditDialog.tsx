'use client';

import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { OperatorProfileEditDialogBody } from './OperatorProfileEditDialogBody';

/**
 * Per-row admin profile-edit dialog (TASK-PC-FE-017 — sibling of
 * `OperatorConfirmDialog`). Captures a single `defaultAccountId` input +
 * an explicit Clear toggle + a required audit reason, then submits via
 * the consumer's `setOperatorProfile(operatorId, defaultAccountId, reason)`
 * mutation. The producer endpoint is
 * `PATCH /api/admin/operators/{operatorId}/profile` (admin-on-behalf-of —
 * cross-operator counterpart of self-serve `me/profile`; TASK-BE-307).
 *
 * v1 design (§ Decision authority "Why no current-value pre-population"):
 * the dialog opens with an empty input + an explicit hint that the current
 * value is NOT visible — Save unconditionally overwrites with the input
 * (or `null` for Clear). The "current value source" producer extension is
 * a separate future task; the v1 dialog never fabricates a current value.
 *
 * Invariants:
 *   - Save submits `(defaultAccountId: string | null, reason: string)` —
 *     never sends an empty string; Clear sets `null`. The producer rejects
 *     `""` as `400 INVALID_REQUEST`, so the only way to clear is the
 *     explicit Clear toggle.
 *   - Save is disabled when reason is empty (trim), OR (Clear OFF AND
 *     input fails client-side validation: whitespace-only / > 36 chars /
 *     internal whitespace or control chars).
 *   - Keyboard / WCAG AA: focus moves to the first input on open, Escape
 *     cancels, focus is trapped, `role="dialog"` + `aria-modal` +
 *     labelled/described.
 *   - Inline server error after a failed submit (no crash).
 *   - This is a SEPARATE component from `OperatorConfirmDialog` per the
 *     task's Decision authority — single-responsibility per-attribute
 *     admin dialog scales linearly without growing the confirm dialog's
 *     scope (`wmsDefaultWarehouseId`, etc. each get their own component).
 */

// Mirror of the proxy zod regex (`^[^\s\x00-\x1f\x7f]+$`): no whitespace,
// no control chars, no DEL. ASCII range `\x00-\x1f` and `\x7f` covered
// by the explicit ranges + the `\s` (which includes `\t \n \r \f \v`).
// eslint-disable-next-line no-control-regex
const VALID_ID_RE = /^[^\s\x00-\x1f\x7f]+$/;

function clientValidate(raw: string): string | null {
  const trimmed = raw.trim();
  if (trimmed.length === 0) {
    return '값은 공백만으로 구성될 수 없습니다.';
  }
  if (trimmed.length > 36) {
    return '값은 최대 36자까지 입력할 수 있습니다.';
  }
  if (!VALID_ID_RE.test(trimmed)) {
    return '값에 공백·제어문자가 포함될 수 없습니다.';
  }
  return null;
}

export interface OperatorProfileEditDialogProps {
  open: boolean;
  /** Human-friendly label for the target operator (email or operatorId). */
  operatorIdLabel: string;
  /**
   * TASK-PC-FE-018 — the target operator's current
   * {@code finance_default_account_id} value (from the operators list
   * response's {@code operatorContext?.defaultAccountId}, populated by
   * TASK-BE-308). When present, the dialog opens with the input pre-filled
   * + a hint "현재 값: {value}". When null/undefined, the dialog opens
   * empty + a hint "현재 값: 미설정". The pre-population is informational
   * only — the operator can type a new value, Clear, or Save unchanged
   * (an unchanged Save is accepted by the producer and produces an audit
   * row with the same value).
   */
  initialDefaultAccountId?: string | null;
  pending?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  /** Called with the trimmed value (or `null` for Clear) + trimmed reason. */
  onConfirm: (defaultAccountId: string | null, reason: string) => void;
  onCancel: () => void;
}

export function OperatorProfileEditDialog({
  open,
  operatorIdLabel,
  initialDefaultAccountId,
  pending = false,
  errorMessage,
  onConfirm,
  onCancel,
}: OperatorProfileEditDialogProps) {
  const titleId = useId();
  const descId = useId();
  const valueId = useId();
  const reasonId = useId();
  const clearId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const valueRef = useRef<HTMLInputElement>(null);

  const [value, setValue] = useState('');
  const [cleared, setCleared] = useState(false);
  const [reason, setReason] = useState('');

  // Reset state on open (TASK-PC-FE-018 — initialize input with the
  // target operator's current value when supplied; null/undefined opens
  // empty. Clear always starts OFF — empty initial ≠ clear-intent
  // (§ Decision authority "Why pre-populate AND keep Clear toggle visible").
  useEffect(() => {
    if (open) {
      setValue(initialDefaultAccountId ?? '');
      setCleared(false);
      setReason('');
      const t = setTimeout(() => valueRef.current?.focus(), 0);
      return () => clearTimeout(t);
    }
  }, [open, initialDefaultAccountId]);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onCancel();
      }
      if (e.key === 'Tab' && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button, textarea, input, [tabindex]:not([tabindex="-1"])',
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

  const trimmedReason = reason.trim();
  const reasonOk = trimmedReason.length > 0;

  const valueClientError = useMemo(
    () => (cleared ? null : value === '' ? null : clientValidate(value)),
    [cleared, value],
  );
  // When NOT cleared, value must pass validation AND be non-empty after
  // trim. When cleared, value is ignored.
  const valueOk = cleared || (value.trim().length > 0 && valueClientError === null);

  const canConfirm = reasonOk && valueOk && !pending;

  function submit() {
    if (!canConfirm) return;
    onConfirm(cleared ? null : value.trim(), trimmedReason);
  }

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="operator-profile-edit-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="operator-profile-edit-dialog"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <OperatorProfileEditDialogBody
          titleId={titleId}
          descId={descId}
          valueId={valueId}
          reasonId={reasonId}
          clearId={clearId}
          valueRef={valueRef}
          operatorIdLabel={operatorIdLabel}
          initialDefaultAccountId={initialDefaultAccountId}
          value={value}
          setValue={setValue}
          cleared={cleared}
          setCleared={setCleared}
          reason={reason}
          setReason={setReason}
          valueClientError={valueClientError}
          reasonOk={reasonOk}
          errorMessage={errorMessage}
          pending={pending}
          canConfirm={canConfirm}
          onCancel={onCancel}
          submit={submit}
        />
      </div>
    </div>
  );
}
