'use client';

import { useId } from 'react';
import { Button } from '@/shared/ui/Button';
import { ELEVATED_ROLE } from '../api/types';
import type { InviteOperatorInput } from '../api/invitation-types';
import { useInviteOperatorForm } from '../hooks/use-invite-operator-form';

/**
 * «운영자 초대» form (TASK-MONO-772 S5 — console-integration-contract § 2.4.3
 * row 11, «등록 → 초대»). A company operator is no longer CREATED by the
 * console; they are INVITED by email and become an operator only after they
 * accept, logged in with an IAM account whose email they have VERIFIED
 * (ADR-MONO-080 D6). The target tenant is the ACTIVE tenant — never `*`.
 *
 * Collects email · display name · roles (pre-filtered to the caller's
 * grantable set, as the create form does). It does NOT call the producer: it
 * hands a validated draft up, and the parent gates the call behind the
 * reason + confirm dialog. There is no password field and no account
 * pre-check (the TASK-MONO-334 pre-gate is retired with the non-`*` create).
 */

export interface InviteOperatorFormProps {
  /** The active tenant — the invitation target (never `*`). */
  tenantId: string;
  onSubmitDraft: (draft: InviteOperatorInput) => void;
  /** Inline error from the last invite attempt (no crash). */
  serverError?: string | null;
  /** When the last invite answered «already pending», the parent offers a
   *  jump to that row's resend (§ 2.4.3 error table). */
  onJumpToPending?: (() => void) | null;
  pending?: boolean;
  grantableRoles?: string[] | null;
  /** Bumped by the parent after a successful invite → the fields clear. */
  resetSignal?: number;
}

export function InviteOperatorForm({
  tenantId,
  onSubmitDraft,
  serverError,
  onJumpToPending = null,
  pending = false,
  grantableRoles = null,
  resetSignal = 0,
}: InviteOperatorFormProps) {
  const emailId = useId();
  const nameId = useId();
  const rolesId = useId();

  const {
    email,
    setEmail,
    displayName,
    setDisplayName,
    roles,
    touched,
    emailOk,
    nameOk,
    rolesOk,
    canSubmit,
    grantsElevated,
    renderableRoles,
    toggleRole,
    handleSubmit,
  } = useInviteOperatorForm({
    tenantId,
    onSubmitDraft,
    pending,
    grantableRoles,
    resetSignal,
  });

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-6 grid gap-4 rounded-md border border-border bg-background p-4 sm:grid-cols-2"
      aria-label="운영자 초대"
      data-testid="invite-operator-form"
      noValidate
    >
      <div className="sm:col-span-2">
        <h2 className="text-lg font-semibold text-foreground">운영자 초대</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          <strong data-testid="invite-operator-tenant">{tenantId}</strong>{' '}
          테넌트로 초대 메일을 보냅니다. 받은 사람이 그 이메일을 인증한 개인(IAM)
          계정으로 로그인해 수락하면 운영자가 됩니다. 계정이 없는 사람도 수락
          화면에서 가입할 수 있습니다. 다른 테넌트로 초대하려면 상단에서 테넌트를
          바꾸세요.
        </p>
      </div>

      <div>
        <label
          htmlFor={emailId}
          className="block text-sm font-medium text-foreground"
        >
          이메일 <span aria-hidden="true">*</span>
        </label>
        <input
          id={emailId}
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          aria-required="true"
          aria-invalid={touched && !emailOk}
          autoComplete="off"
          data-testid="invite-operator-email"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        {touched && !emailOk && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="invite-operator-email-error"
          >
            올바른 이메일 형식을 입력하세요.
          </p>
        )}
      </div>

      <div>
        <label
          htmlFor={nameId}
          className="block text-sm font-medium text-foreground"
        >
          표시 이름 <span aria-hidden="true">*</span>
        </label>
        <input
          id={nameId}
          type="text"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
          aria-required="true"
          aria-invalid={touched && !nameOk}
          maxLength={64}
          data-testid="invite-operator-displayName"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        {touched && !nameOk && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="invite-operator-displayName-error"
          >
            표시 이름을 1~64자로 입력하세요. 수락하면 이 이름으로 운영자가
            만들어집니다.
          </p>
        )}
      </div>

      <fieldset className="sm:col-span-2">
        <legend
          className="text-sm font-medium text-foreground"
          id={rolesId}
        >
          수락 때 부여할 역할 (1개 이상)
        </legend>
        <div
          className="mt-2 flex flex-wrap gap-3"
          role="group"
          aria-labelledby={rolesId}
        >
          {renderableRoles.map((role) => {
            const elevated = role === ELEVATED_ROLE;
            return (
              <label
                key={role}
                className="flex items-center gap-2 text-sm text-foreground"
              >
                <input
                  type="checkbox"
                  checked={roles.includes(role)}
                  onChange={() => toggleRole(role)}
                  data-testid={`invite-operator-role-${role}`}
                />
                <span
                  className={elevated ? 'font-medium text-destructive' : undefined}
                >
                  {role}
                  {elevated ? ' (특권)' : ''}
                </span>
              </label>
            );
          })}
        </div>
        {touched && !rolesOk && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="invite-operator-roles-error"
          >
            역할을 하나 이상 고르세요.
          </p>
        )}
        {grantsElevated && (
          <p
            className="mt-2 text-xs text-destructive"
            data-testid="invite-operator-elevated-warning"
            role="status"
          >
            수락하면 이 사람은 SUPER_ADMIN 특권을 가집니다. 확인 단계에서 사유가
            요구됩니다.
          </p>
        )}
      </fieldset>

      {serverError && (
        <div
          role="alert"
          data-testid="invite-operator-server-error"
          className="sm:col-span-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          <p>{serverError}</p>
          {onJumpToPending && (
            <button
              type="button"
              onClick={onJumpToPending}
              data-testid="invite-operator-jump-to-pending"
              className="mt-2 underline underline-offset-2"
            >
              대기 중인 초대 다시 보내기
            </button>
          )}
        </div>
      )}

      <div className="sm:col-span-2">
        <Button
          type="submit"
          disabled={pending}
          data-testid="invite-operator-submit"
        >
          {pending ? '처리 중…' : '초대 보내기 (확인 필요)'}
        </Button>
        {!canSubmit && touched && (
          <span className="sr-only" role="status">
            입력값을 확인하세요.
          </span>
        )}
      </div>
    </form>
  );
}
