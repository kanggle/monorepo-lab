'use client';

/**
 * Account-existence advisory/blocking notes for `CreateOperatorForm`
 * (TASK-PC-FE-209 split; behavior changed by TASK-MONO-334). The account-
 * existence PRE-GATE copy driven by the probe in `useCreateOperatorForm`.
 *
 * TASK-MONO-334 (ADR-MONO-035 amendment): an operator may be created ONLY for an
 * email that already owns a tenant account. So — unlike the SUPERSEDED
 * TASK-PC-FE-179 advisory — an "absent" account is now a BLOCKING error (submit
 * is disabled; a break-glass password no longer bypasses it), and an
 * "unavailable" probe also blocks (we cannot confirm the account exists).
 * Presentational only — the probe state resolves to the mutually-exclusive
 * booleans in the hook and arrives via props.
 */
export interface CreateOperatorAccountAdvisoryProps {
  /** The (trimmed) tenant the probe targeted — rendered inside the copy. */
  probeTenant: string;
  /** Definitively absent ⇒ BLOCKING error (must sign up first). */
  showBlockingAbsent: boolean;
  /** Probe could not determine existence ⇒ BLOCKING (cannot verify — try again). */
  showUnavailable: boolean;
  /** Probe in flight ⇒ transient "checking…" hint. */
  showChecking: boolean;
  /** Account exists in the tenant ⇒ OIDC-login-ok confirmation (submit enabled). */
  showExistsOk: boolean;
}

export function CreateOperatorAccountAdvisory({
  probeTenant,
  showBlockingAbsent,
  showUnavailable,
  showChecking,
  showExistsOk,
}: CreateOperatorAccountAdvisoryProps) {
  return (
    <>
      {showChecking && (
        <p
          role="status"
          data-testid="create-operator-account-checking"
          className="sm:col-span-2 text-xs text-muted-foreground"
        >
          가입된 계정인지 확인 중…
        </p>
      )}
      {showBlockingAbsent && (
        <p
          role="alert"
          data-testid="create-operator-account-error"
          className="sm:col-span-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive"
        >
          이 이메일은 <span className="font-medium">{probeTenant}</span> 테넌트에
          가입된 계정이 아닙니다. 운영자는 가입된 계정에만 등록할 수 있습니다. 먼저
          이 이메일로 회원가입한 뒤 다시 시도하세요.
        </p>
      )}
      {showUnavailable && (
        <p
          role="alert"
          data-testid="create-operator-account-unavailable"
          className="sm:col-span-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground"
        >
          가입 계정 여부를 확인할 수 없습니다. 잠시 후 다시 시도하세요. (확인
          전에는 운영자를 등록할 수 없습니다.)
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
    </>
  );
}
