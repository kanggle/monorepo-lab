'use client';

import { useId, useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Self update-profile form (console-integration-contract § 2.4.3 row 6 —
 * `PATCH /api/admin/operators/me/profile`, TASK-PC-FE-016). This is the
 * LOGGED-IN operator's OWN profile carrier (v1 = `operatorContext.
 * defaultAccountId`). There is NO admin-set-other-profile endpoint in the
 * parity line — the console does not invent one (task Edge Case).
 *
 * Save is button-explicit only — do NOT auto-save on input change
 * (privacy / audit-row pollution: every keystroke would write an audit
 * row producer-side). Clear is also explicit; it sends JSON `null` (not
 * empty string — the producer rejects empty as `400 INVALID_REQUEST`).
 *
 * Client-side validation mirrors the producer + proxy zod (≤ 36 chars,
 * no internal whitespace, no control chars) as a UX pre-check ONLY — the
 * producer is the final authority. An invalid finance account UUID
 * surfaces honestly as a finance card `404 ACCOUNT_NOT_FOUND` post-save
 * on the operator overview (BE-304 § Decision authority — opaque to GAP).
 */

export interface MyProfileFormProps {
  /** Server-rendered initial value (null when never set). */
  initial: string | null;
  /** `value` is the new finance account UUID OR `null` to clear. */
  onSubmit: (value: string | null) => void;
  /** Inline server error from the last attempt (no crash). */
  serverError?: string | null;
  pending?: boolean;
  /** Inline transient success message after a successful submit. */
  succeeded?: boolean;
}

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

export function MyProfileForm({
  initial,
  onSubmit,
  serverError,
  pending = false,
  succeeded = false,
}: MyProfileFormProps) {
  const inputId = useId();
  const initialDisplay = initial ?? '';
  const [value, setValue] = useState<string>(initialDisplay);
  const [touched, setTouched] = useState(false);

  const trimmed = value.trim();
  const clientError = useMemo(
    () => (touched && value !== '' ? clientValidate(value) : null),
    [touched, value],
  );

  // Save is enabled only when the (trimmed) value differs from the
  // initial AND passes client validation AND is not empty (use Clear
  // for empty → null transition explicitly).
  const isUnchanged = trimmed === (initial ?? '');
  const canSave =
    !pending &&
    trimmed.length > 0 &&
    clientValidate(value) === null &&
    !isUnchanged;

  // Clear is enabled when the initial is set OR the user has typed into
  // an originally-empty input (so the form is dirty — clearing local
  // text without persisting null is otherwise just a reset).
  const canClear = !pending && (initial !== null || trimmed.length > 0);

  function submitSave(e: React.FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!canSave) return;
    onSubmit(trimmed);
  }

  function submitClear() {
    setTouched(true);
    if (!canClear) return;
    setValue('');
    // Explicit `null` (NOT empty string) — the producer rejects empty as
    // `400 INVALID_REQUEST`; `null` is the documented clear semantic.
    onSubmit(null);
  }

  return (
    <form
      onSubmit={submitSave}
      className="mb-8 grid max-w-md gap-4 rounded-md border border-border bg-background p-4"
      aria-label="내 프로파일 — 기본 finance 계정"
      data-testid="my-profile-form"
    >
      <h2 className="text-lg font-semibold text-foreground">
        내 프로파일 — 기본 finance 계정
      </h2>
      <p className="text-xs text-muted-foreground">
        Operator Overview의 finance 카드가 기본으로 조회할 계정입니다. 비워두려면
        Clear를 누르세요. 유효하지 않은 값은 finance 카드에 404로 표시됩니다.
      </p>

      <div>
        <label
          htmlFor={inputId}
          className="block text-sm font-medium text-foreground"
        >
          기본 finance 계정 ID
        </label>
        <input
          id={inputId}
          type="text"
          value={value}
          onChange={(e) => {
            setValue(e.target.value);
            if (!touched) setTouched(true);
          }}
          placeholder="예: 01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"
          maxLength={36}
          autoComplete="off"
          spellCheck={false}
          aria-invalid={clientError !== null}
          data-testid="my-profile-default-account-id"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          UUID 형식 권장 (최대 36자, 공백·제어문자 금지). GAP은 이 값을
          finance에 verify하지 않습니다 (opaque).
        </p>
        {clientError && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="my-profile-client-error"
          >
            {clientError}
          </p>
        )}
      </div>

      {serverError && (
        <p
          role="alert"
          data-testid="my-profile-server-error"
          className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {serverError}
        </p>
      )}
      {succeeded && (
        <p
          role="status"
          data-testid="my-profile-success"
          className="rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          저장되었습니다.
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        <Button
          type="submit"
          disabled={!canSave}
          data-testid="my-profile-save"
        >
          {pending ? '처리 중…' : '저장'}
        </Button>
        <Button
          type="button"
          variant="secondary"
          disabled={!canClear}
          onClick={submitClear}
          data-testid="my-profile-clear"
        >
          Clear
        </Button>
      </div>
    </form>
  );
}
