'use client';

import { useState } from 'react';
import { messageForCode } from '@/shared/api/errors';
import { performLogout } from '@/shared/lib/logout';
import { TENANT_SLUG_RE } from '../api/types';
import { OnboardingWhatHappens } from './OnboardingWhatHappens';

/**
 * Self-service "create a tenant" form — the self mode of the one «테넌트 생성» screen `/new-tenant`
 * (ADR-MONO-044 §3.4 / TASK-PC-FE-182; TASK-MONO-773 S6 — console-integration-contract § 2.6.4).
 *
 * Posts to the same-origin `/api/onboarding/organizations` proxy (the browser never touches admin-service —
 * the proxy attaches the IAM access token as the `subjectToken` server-side). On success the proxy has already
 * re-exchanged an operator token, dropped the non-operator marker and assumed the new tenant, so we
 * HARD-navigate to `/` (a full document load re-reads the HttpOnly cookies server-side; the `(console)` guard
 * now renders the operator shell inside the new tenant). If the proxy could create the tenant but not
 * immediately re-exchange (`ready:false`), we send the user to `/login` where a fresh login completes entry.
 *
 * `mode` — `'first'` (a non-operator's first company) or `'additional'` (a company operator's second company,
 * ADR-MONO-082 D6 b): same endpoint, same body; only the explainer differs.
 *
 * Error copy (§ 2.6.4 table): the trust-gate codes get the contract's sentences, plus a link to the IAM email
 * verification page (`EMAIL_NOT_VERIFIED`) or a logout (`ONBOARDING_ACCOUNT_NOT_ELIGIBLE` — «sign in with a
 * personal account»). Every other code keeps `messageForCode`.
 *
 * `submit` / `navigate` / `logout` are injectable for tests.
 */
export interface CreateOrganizationFormProps {
  submit?: (body: {
    tenantId: string;
    organizationName: string;
  }) => Promise<Response>;
  /** Test seam for the post-success navigation (defaults to a hard nav). */
  navigate?: (href: string) => void;
  /** TASK-MONO-773 S6 — first company (non-operator) or an additional one (company operator). */
  mode?: 'first' | 'additional';
  /** TASK-MONO-773 S6 — the IAM email-verification page (`<issuer>/email-verification`), when known. */
  emailVerificationUrl?: string | null;
  /** Test seam for the not-eligible logout (defaults to the shared RP-initiated logout). */
  logout?: () => void;
}

/**
 * TASK-MONO-773 S6 — the self-mode copy for IAM's trust-gate / provisioning answers
 * (console-integration-contract § 2.6.4 «Error copy»; codes from `onboarding-api.md`). Kept here, not in the
 * global `messageForCode` map: «회사를 만들려면 …» is this screen's sentence, and `EMAIL_NOT_VERIFIED` is a shared
 * IAM code other screens may word differently.
 */
const SELF_MODE_ERROR_COPY: Record<string, string> = {
  EMAIL_NOT_VERIFIED: '회사를 만들려면 먼저 이메일을 인증해야 합니다.',
  ONBOARDING_ACCOUNT_NOT_ELIGIBLE:
    '이 계정으로는 회사를 만들 수 없습니다. 사이트나 회사용 계정이라면 개인 계정으로 로그인해 만드세요.',
  OPERATOR_ALREADY_PROVISIONED:
    '이 계정은 지금 회사를 새로 만들 수 없습니다 (플랫폼 관리자 또는 정지된 운영자).',
  RATE_LIMITED: '요청이 많습니다. 잠시 후 다시 시도하세요.',
};

function copyFor(code: string, message: string | undefined): string {
  return SELF_MODE_ERROR_COPY[code] ?? messageForCode(code, message);
}

function defaultSubmit(body: {
  tenantId: string;
  organizationName: string;
}): Promise<Response> {
  return fetch('/api/onboarding/organizations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

function defaultNavigate(href: string): void {
  window.location.assign(href);
}

function defaultLogout(): void {
  void performLogout();
}

export function CreateOrganizationForm({
  submit = defaultSubmit,
  navigate = defaultNavigate,
  mode = 'first',
  emailVerificationUrl = null,
  logout = defaultLogout,
}: CreateOrganizationFormProps) {
  const [tenantId, setTenantId] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<{ code: string; text: string } | null>(null);

  const slugValid = TENANT_SLUG_RE.test(tenantId);
  const nameValid =
    organizationName.trim().length >= 1 && organizationName.trim().length <= 100;
  const canSubmit = slugValid && nameValid && !pending;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;
    setPending(true);
    setError(null);
    try {
      const res = await submit({
        tenantId,
        organizationName: organizationName.trim(),
      });
      if (res.status === 201) {
        const data = (await res.json().catch(() => ({}))) as {
          ready?: boolean;
        };
        // ready → cookies set, walk into the console; else the tenant exists but
        // needs a fresh login to complete operator-session entry.
        navigate(data.ready ? '/' : '/login?onboarded=1');
        return;
      }
      const body = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
      };
      const code = body.code ?? '';
      setError({ code, text: copyFor(code, body.message ?? undefined) });
      setPending(false);
    } catch {
      setError({
        code: 'NETWORK_ERROR',
        text: '네트워크 오류로 테넌트를 만들지 못했습니다. 잠시 후 다시 시도하세요.',
      });
      setPending(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <OnboardingWhatHappens mode={mode} />

      <div>
        <label
          htmlFor="onboarding-tenant-id"
          className="block text-sm font-medium text-foreground"
        >
          테넌트 ID
        </label>
        <input
          id="onboarding-tenant-id"
          name="tenantId"
          type="text"
          autoComplete="off"
          spellCheck={false}
          value={tenantId}
          onChange={(e) => setTenantId(e.target.value)}
          placeholder="acme-corp"
          data-testid="onboarding-tenant-id"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          URL·식별에 쓰입니다. 소문자로 시작하는 2~32자 (영문 소문자·숫자·하이픈).
          생성 후 변경할 수 없습니다.
        </p>
        {tenantId.length > 0 && !slugValid && (
          <p
            role="alert"
            data-testid="onboarding-tenant-id-error"
            className="mt-1 text-xs text-destructive"
          >
            테넌트 ID 형식이 올바르지 않습니다.
          </p>
        )}
      </div>

      <div>
        <label
          htmlFor="onboarding-org-name"
          className="block text-sm font-medium text-foreground"
        >
          회사 이름
        </label>
        <input
          id="onboarding-org-name"
          name="organizationName"
          type="text"
          value={organizationName}
          onChange={(e) => setOrganizationName(e.target.value)}
          placeholder="Acme Corporation"
          maxLength={100}
          data-testid="onboarding-org-name"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
      </div>

      {error && (
        <div
          role="alert"
          data-testid="onboarding-error"
          className="space-y-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          <p>{error.text}</p>
          {error.code === 'EMAIL_NOT_VERIFIED' && emailVerificationUrl && (
            <a
              href={emailVerificationUrl}
              data-testid="onboarding-error-verify-link"
              className="font-medium underline underline-offset-2"
            >
              이메일 인증하러 가기
            </a>
          )}
          {error.code === 'ONBOARDING_ACCOUNT_NOT_ELIGIBLE' && (
            <button
              type="button"
              onClick={() => logout()}
              data-testid="onboarding-error-logout"
              className="font-medium underline underline-offset-2"
            >
              로그아웃하고 개인 계정으로 다시 로그인
            </button>
          )}
        </div>
      )}

      <button
        type="submit"
        disabled={!canSubmit}
        data-testid="onboarding-submit"
        className="inline-flex w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50"
      >
        {pending ? '테넌트 만드는 중…' : '테넌트 만들기'}
      </button>
    </form>
  );
}
