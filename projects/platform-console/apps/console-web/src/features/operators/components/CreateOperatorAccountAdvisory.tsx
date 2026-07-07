'use client';

/**
 * Account-existence advisory notes for `CreateOperatorForm` (TASK-PC-FE-209
 * split). The dangling-operator / break-glass / OIDC-ok advisory copy driven
 * by the TASK-PC-FE-179 pre-flight probe. ADVISORY only (the create is never
 * blocked — the producer is the final authority); the copy is preserved
 * verbatim. Presentational — the probe state resolves to the three mutually-
 * exclusive booleans in `useCreateOperatorForm` and arrives via props.
 */
export interface CreateOperatorAccountAdvisoryProps {
  /** The (trimmed) tenant the probe targeted — rendered inside the copy. */
  probeTenant: string;
  /** Definitively absent AND no break-glass password ⇒ dangling-operator warn. */
  showDangling: boolean;
  /** Definitively absent BUT a break-glass password is set ⇒ soft note. */
  showAbsentButPw: boolean;
  /** Account exists in the tenant ⇒ OIDC-login-ok confirmation. */
  showExistsOk: boolean;
}

export function CreateOperatorAccountAdvisory({
  probeTenant,
  showDangling,
  showAbsentButPw,
  showExistsOk,
}: CreateOperatorAccountAdvisoryProps) {
  return (
    <>
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
    </>
  );
}
