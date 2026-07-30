'use client';

import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';

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
 *
 * TASK-PC-FE-269 — thin wrapper over `shared/ui/ConfirmDialog`
 * (`OperatorProfileEditDialogBody` folded in as `children`). This
 * migration already had a full focus trap + Escape handling before, so it
 * introduces NO new observable keyboard/focus behavior — pure dedup.
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
  const valueId = useId();
  const reasonId = useId();
  const clearId = useId();
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
    }
  }, [open, initialDefaultAccountId]);

  const trimmedReason = reason.trim();
  const reasonOk = trimmedReason.length > 0;

  const valueClientError = useMemo(
    () => (cleared ? null : value === '' ? null : clientValidate(value)),
    [cleared, value],
  );
  // When NOT cleared, value must pass validation AND be non-empty after
  // trim. When cleared, value is ignored.
  const valueOk = cleared || (value.trim().length > 0 && valueClientError === null);

  function submit() {
    if (!reasonOk || !valueOk) return;
    onConfirm(cleared ? null : value.trim(), trimmedReason);
  }

  return (
    <ConfirmDialog
      open={open}
      title={`프로파일 편집 — ${operatorIdLabel}`}
      description={
        <p data-testid="operator-profile-edit-hint">
          현재 값: {initialDefaultAccountId ? initialDefaultAccountId : '미설정'}.
          입력값이 그대로 저장됩니다. 기본 계정을 비우려면 아래 Clear 토글을
          사용하세요. 이 작업은 감사 사유와 함께 기록됩니다.
        </p>
      }
      confirmLabel="저장"
      confirmDisabled={!reasonOk || !valueOk}
      errorMessage={errorMessage}
      pending={pending}
      dialogTestId="operator-profile-edit-dialog"
      overlayTestId="operator-profile-edit-overlay"
      cancelTestId="operator-profile-edit-cancel"
      confirmTestId="operator-profile-edit-save"
      errorTestId="operator-profile-edit-error"
      initialFocusRef={valueRef}
      onConfirm={submit}
      onCancel={onCancel}
    >
      <div className="mt-4">
        <label
          htmlFor={valueId}
          className="block text-sm font-medium text-foreground"
        >
          기본 finance 계정 ID
        </label>
        <input
          id={valueId}
          ref={valueRef}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          disabled={cleared}
          placeholder="예: 01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"
          maxLength={36}
          autoComplete="off"
          spellCheck={false}
          aria-invalid={valueClientError !== null}
          data-testid="operator-profile-edit-value"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          UUID 형식 권장 (최대 36자, 공백·제어문자 금지). GAP은 이 값을
          finance에 verify하지 않습니다 (opaque).
        </p>
        {valueClientError && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="operator-profile-edit-value-error"
          >
            {valueClientError}
          </p>
        )}
      </div>

      <div className="mt-3 flex items-center gap-2">
        <input
          id={clearId}
          type="checkbox"
          checked={cleared}
          onChange={(e) => setCleared(e.target.checked)}
          data-testid="operator-profile-edit-clear"
        />
        <label htmlFor={clearId} className="text-sm text-foreground">
          기본 계정 제거 (저장 시 <code>null</code> 전송)
        </label>
      </div>

      <div className="mt-4">
        <label
          htmlFor={reasonId}
          className="block text-sm font-medium text-foreground"
        >
          감사 사유 <span aria-hidden="true">*</span>
          <span className="sr-only">(필수)</span>
        </label>
        <textarea
          id={reasonId}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
          aria-required="true"
          rows={3}
          data-testid="operator-profile-edit-reason"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          placeholder="이 운영 작업의 사유를 입력하세요 (감사 기록에 남습니다)"
        />
        {!reasonOk && (
          <p
            className="mt-1 text-xs text-muted-foreground"
            data-testid="operator-profile-edit-reason-required"
          >
            사유를 입력해야 작업을 진행할 수 있습니다.
          </p>
        )}
      </div>
    </ConfirmDialog>
  );
}
