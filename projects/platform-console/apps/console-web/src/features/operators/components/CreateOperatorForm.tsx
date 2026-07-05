'use client';

import { useId } from 'react';
import { Button } from '@/shared/ui/Button';
import { ELEVATED_ROLE, type CreateOperatorInput } from '../api/types';
import { checkAccountExistsForTenant } from '../api/account-existence';
import { useCreateOperatorForm } from '../hooks/use-create-operator-form';

/**
 * Create-operator form (console-integration-contract § 2.4.3). Collects
 * email, displayName, password (client-side policy mirror), roles
 * (multi-select), tenantId. It does NOT call the producer — it hands a
 * validated draft up to the parent, which gates the actual create behind
 * the reason-capture + confirm dialog (privilege-sensitive; the create
 * never fires one-click). The password is held only in this leaf form
 * state and is NEVER logged / echoed into structured logs (security UX).
 *
 * `tenantId='*'` (SUPER_ADMIN platform scope) is offered ONLY when the
 * current operator is platform-scope (`isPlatformOperator`). A non-platform
 * operator never sees `*` — the UI must not present an escalation it cannot
 * perform (task Edge Case; the producer would 403 TENANT_SCOPE_DENIED).
 *
 * feat/iam-grantable-roles-filter — the role checkboxes are pre-filtered to
 * `grantableRoles` (server-provided, `GET .../operators/grantable-roles`) —
 * see `grantableRoles` prop doc. UX pre-filter ONLY; the producer's
 * `403 ROLE_GRANT_FORBIDDEN` remains the final no-escalation authority.
 *
 * TASK-PC-FE-196 — the form state, validation, dangling-account pre-flight
 * probe, grantable-role filter, and confirm-gated submit live in the
 * `useCreateOperatorForm` hook (operators/hooks/); this component is the
 * presentational shell that renders from the hook's result.
 */

export interface CreateOperatorFormProps {
  /** Tenants the operator may pick for the new operator. */
  tenantOptions: string[];
  /** True ⇒ also offer the `*` platform-scope sentinel. */
  isPlatformOperator: boolean;
  /** Hand the validated draft up; the parent confirms + fires the create.
   *  The second arg signals whether the draft grants `SUPER_ADMIN` (the
   *  parent always uses elevated confirm copy for a create, and calls the
   *  SUPER_ADMIN grant out additionally). */
  onSubmitDraft: (draft: CreateOperatorInput, grantsElevated: boolean) => void;
  /** Inline server error from the last create attempt (no crash). */
  serverError?: string | null;
  pending?: boolean;
  /** feat/iam-grantable-roles-filter — the seed role names the CALLING
   *  operator may grant. Role checkboxes render only the intersection of
   *  `KNOWN_OPERATOR_ROLES` and this set. `null` / `undefined` (absent —
   *  e.g. the server-side fetch failed) ⇒ fall back to rendering EVERY
   *  `KNOWN_OPERATOR_ROLES` checkbox (never an empty list — the producer
   *  403 is still the final authority). */
  grantableRoles?: string[] | null;
  /** TASK-PC-FE-179 — pre-flight "does this email have a signed-up account in
   *  the selected tenant?" probe. Default calls the accounts search BFF; tests
   *  inject a stub so the leaf never touches the network. Returns `true`
   *  (exists) / `false` (definitively absent) / `null` (unknown — skip the
   *  warning; fail-soft). */
  checkAccountExists?: (
    email: string,
    tenantId: string,
    signal?: AbortSignal,
  ) => Promise<boolean | null>;
}

export function CreateOperatorForm({
  tenantOptions,
  isPlatformOperator,
  onSubmitDraft,
  serverError,
  pending = false,
  grantableRoles = null,
  checkAccountExists = checkAccountExistsForTenant,
}: CreateOperatorFormProps) {
  const emailId = useId();
  const nameId = useId();
  const pwId = useId();
  const tenantId = useId();
  const rolesId = useId();

  const {
    email,
    setEmail,
    displayName,
    setDisplayName,
    password,
    setPassword,
    tenant,
    setTenant,
    roles,
    touched,
    emailOk,
    pwError,
    canSubmit,
    grantsElevated,
    probeTenant,
    showDangling,
    showAbsentButPw,
    showExistsOk,
    renderableRoles,
    toggleRole,
    handleSubmit,
  } = useCreateOperatorForm({
    onSubmitDraft,
    pending,
    grantableRoles,
    checkAccountExists,
  });

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-8 grid gap-4 rounded-md border border-border bg-background p-4 sm:grid-cols-2"
      aria-label="운영자 계정 생성"
      data-testid="create-operator-form"
    >
      <h2 className="sm:col-span-2 text-lg font-semibold text-foreground">
        운영자 계정 생성
      </h2>

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
          data-testid="create-operator-email"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        {touched && !emailOk && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="create-operator-email-error"
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
          maxLength={64}
          data-testid="create-operator-displayName"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>

      <div>
        <label
          htmlFor={pwId}
          className="block text-sm font-medium text-foreground"
        >
          break-glass 로컬 비밀번호{' '}
          <span className="text-muted-foreground">(선택)</span>
        </label>
        <input
          id={pwId}
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-invalid={pwError !== null}
          autoComplete="new-password"
          data-testid="create-operator-password"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p
          className="mt-1 text-xs text-muted-foreground"
          id={`${pwId}-policy`}
        >
          비워두면 OIDC-only 운영자로 생성됩니다 — 주 로그인은 이 이메일의 통합
          IAM 계정 자격증명입니다. 입력 시 IdP 장애 대비용 비상 로컬 로그인이며,
          10자 이상·영문·숫자·특수문자 각 1자 이상이어야 합니다.
        </p>
        {pwError && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="create-operator-password-error"
          >
            비밀번호가 정책을 충족하지 않습니다.
          </p>
        )}
      </div>

      <div>
        <label
          htmlFor={tenantId}
          className="block text-sm font-medium text-foreground"
        >
          테넌트 <span aria-hidden="true">*</span>
        </label>
        <select
          id={tenantId}
          value={tenant}
          onChange={(e) => setTenant(e.target.value)}
          required
          aria-required="true"
          data-testid="create-operator-tenant"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <option value="">테넌트 선택…</option>
          {tenantOptions.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
          {isPlatformOperator && (
            <option value="*" data-testid="create-operator-tenant-platform">
              * (플랫폼 스코프 — SUPER_ADMIN)
            </option>
          )}
        </select>
        {!isPlatformOperator && (
          <p className="mt-1 text-xs text-muted-foreground">
            플랫폼 스코프(*) 운영자 계정 생성은 플랫폼 스코프 운영자만 가능합니다.
          </p>
        )}
      </div>

      {showDangling && (
        <p
          role="status"
          data-testid="create-operator-account-warning"
          className="sm:col-span-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive"
        >
          이 이메일은 <span className="font-medium">{probeTenant}</span> 테넌트에
          가입된 계정이 아닙니다. break-glass 비밀번호도 비어 있어, 지금 생성하면
          아무도 로그인할 수 없는 <span className="font-medium">허수 운영자</span>가
          됩니다. 먼저 이 이메일로 회원가입하거나, 비상 로컬 비밀번호를 입력하세요.
          (생성 자체는 막지 않습니다.)
        </p>
      )}
      {showAbsentButPw && (
        <p
          role="status"
          data-testid="create-operator-account-breakglass-note"
          className="sm:col-span-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground"
        >
          이 이메일은 <span className="font-medium">{probeTenant}</span> 테넌트에
          가입된 계정이 아니지만, 입력한 break-glass 로컬 비밀번호로 로그인할 수
          있습니다. (통합 IAM 로그인은 이 이메일로 회원가입해야 가능합니다.)
        </p>
      )}
      {showExistsOk && (
        <p
          role="status"
          data-testid="create-operator-account-ok"
          className="sm:col-span-2 text-xs text-muted-foreground"
        >
          ✓ 이 테넌트에 가입된 계정입니다 — OIDC(통합 IAM) 로그인 가능.
        </p>
      )}

      <fieldset className="sm:col-span-2">
        <legend
          className="text-sm font-medium text-foreground"
          id={rolesId}
        >
          역할 (복수 선택)
        </legend>
        <div
          className="mt-2 flex flex-wrap gap-3"
          role="group"
          aria-labelledby={rolesId}
        >
          {renderableRoles.map((role) => {
            const checked = roles.includes(role);
            const elevated = role === ELEVATED_ROLE;
            return (
              <label
                key={role}
                className="flex items-center gap-2 text-sm text-foreground"
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggleRole(role)}
                  data-testid={`create-operator-role-${role}`}
                />
                <span
                  className={
                    elevated ? 'font-medium text-destructive' : undefined
                  }
                >
                  {role}
                  {elevated ? ' (특권)' : ''}
                </span>
              </label>
            );
          })}
        </div>
        {grantsElevated && (
          <p
            className="mt-2 text-xs text-destructive"
            data-testid="create-operator-elevated-warning"
            role="status"
          >
            이 운영자에게 SUPER_ADMIN 특권을 부여합니다. 확인 단계에서 사유가
            요구됩니다.
          </p>
        )}
      </fieldset>

      {serverError && (
        <p
          role="alert"
          data-testid="create-operator-server-error"
          className="sm:col-span-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {serverError}
        </p>
      )}

      <div className="sm:col-span-2">
        <Button
          type="submit"
          disabled={!canSubmit}
          data-testid="create-operator-submit"
        >
          {pending ? '처리 중…' : '운영자 계정 생성 (확인 필요)'}
        </Button>
      </div>
    </form>
  );
}
