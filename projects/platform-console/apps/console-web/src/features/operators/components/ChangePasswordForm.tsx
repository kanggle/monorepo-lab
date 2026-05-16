'use client';

import { useId, useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { messageForCode } from '@/shared/api/errors';
import { passwordPolicyError } from '../api/types';

/**
 * Self change-password form (console-integration-contract § 2.4.3 —
 * `PATCH /api/admin/operators/me/password`). This is the LOGGED-IN
 * operator's OWN password (current + new + confirm). There is NO
 * admin-set-other-password endpoint in the parity line — the console does
 * NOT invent one (task Edge Case).
 *
 * Password safety: the three password values live ONLY in this leaf form
 * state, are NEVER logged / echoed into structured logs or events, never
 * placed in a query string, and are cleared from state on a successful
 * submit. The client-side policy mirror (≥10, letter+digit+special) is a
 * UX pre-check only — the producer is the final authority. This is a
 * mutation but it is the SELF auth flow: the producer requires NO
 * `X-Operator-Reason` (so there is no reason-capture here — the gate is
 * the current-password proof itself + the confirm step in this form).
 */

export interface ChangePasswordFormProps {
  onSubmit: (currentPassword: string, newPassword: string) => void;
  /** Inline server error from the last attempt (no crash). */
  serverError?: string | null;
  pending?: boolean;
  /** Cleared inputs after a successful submit. */
  succeeded?: boolean;
}

export function ChangePasswordForm({
  onSubmit,
  serverError,
  pending = false,
  succeeded = false,
}: ChangePasswordFormProps) {
  const curId = useId();
  const newId = useId();
  const confirmId = useId();

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [touched, setTouched] = useState(false);

  // Clear the inputs once the parent reports success (do not retain a
  // password in component state beyond its use).
  if (succeeded && (current || next || confirm)) {
    setCurrent('');
    setNext('');
    setConfirm('');
  }

  const policyError = useMemo(
    () => (next === '' ? null : passwordPolicyError(next)),
    [next],
  );
  const confirmMismatch = confirm !== '' && confirm !== next;

  const canSubmit =
    current.length > 0 &&
    next.length > 0 &&
    policyError === null &&
    confirm === next &&
    !pending;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!canSubmit) return;
    onSubmit(current, next);
  }

  return (
    <form
      onSubmit={submit}
      className="mb-8 grid max-w-md gap-4 rounded-md border border-border bg-background p-4"
      aria-label="내 비밀번호 변경"
      data-testid="change-password-form"
    >
      <h2 className="text-lg font-semibold text-foreground">
        내 비밀번호 변경
      </h2>
      <p className="text-xs text-muted-foreground">
        로그인한 본인 계정의 비밀번호만 변경할 수 있습니다 (다른 운영자의
        비밀번호를 설정하는 기능은 없습니다).
      </p>

      <div>
        <label
          htmlFor={curId}
          className="block text-sm font-medium text-foreground"
        >
          현재 비밀번호 <span aria-hidden="true">*</span>
        </label>
        <input
          id={curId}
          type="password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          required
          aria-required="true"
          autoComplete="current-password"
          data-testid="change-password-current"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>

      <div>
        <label
          htmlFor={newId}
          className="block text-sm font-medium text-foreground"
        >
          새 비밀번호 <span aria-hidden="true">*</span>
        </label>
        <input
          id={newId}
          type="password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          required
          aria-required="true"
          aria-invalid={policyError !== null}
          autoComplete="new-password"
          data-testid="change-password-new"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          10자 이상, 영문·숫자·특수문자 각 1자 이상.
        </p>
        {policyError && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="change-password-policy-error"
          >
            {messageForCode('PASSWORD_POLICY_VIOLATION')}
          </p>
        )}
      </div>

      <div>
        <label
          htmlFor={confirmId}
          className="block text-sm font-medium text-foreground"
        >
          새 비밀번호 확인 <span aria-hidden="true">*</span>
        </label>
        <input
          id={confirmId}
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          required
          aria-required="true"
          aria-invalid={confirmMismatch}
          autoComplete="new-password"
          data-testid="change-password-confirm"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        {(confirmMismatch || (touched && confirm !== next)) && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="change-password-confirm-error"
          >
            {messageForCode('PASSWORD_CONFIRM_MISMATCH')}
          </p>
        )}
      </div>

      {serverError && (
        <p
          role="alert"
          data-testid="change-password-server-error"
          className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {serverError}
        </p>
      )}
      {succeeded && (
        <p
          role="status"
          data-testid="change-password-success"
          className="rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          비밀번호가 변경되었습니다.
        </p>
      )}

      <div>
        <Button
          type="submit"
          disabled={!canSubmit}
          data-testid="change-password-submit"
        >
          {pending ? '처리 중…' : '비밀번호 변경'}
        </Button>
      </div>
    </form>
  );
}
