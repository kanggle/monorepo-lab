'use client';

import { useState } from 'react';
import { messageForCode } from '@/shared/api/errors';
import { TENANT_SLUG_RE } from '../api/types';
import { OnboardingWhatHappens } from './OnboardingWhatHappens';

/**
 * Pre-operator "create organization" form (ADR-MONO-044 §3.4 / TASK-PC-FE-182).
 *
 * Posts to the same-origin `/api/onboarding/organizations` proxy (the browser
 * never touches admin-service — the proxy attaches the IAM access token as the
 * `subjectToken` server-side). On success the proxy has already re-exchanged an
 * operator token + set the session cookies, so we HARD-navigate to `/` (a full
 * document load re-reads the HttpOnly cookies server-side and the `(console)`
 * guard now passes — the owner is inside their new tenant's console). If the
 * proxy could create the org but not immediately re-exchange (`ready:false`),
 * we send the user to `/login` where a fresh login completes console entry.
 *
 * `submit` is injectable for tests (defaults to the real fetch).
 */
export interface CreateOrganizationFormProps {
  submit?: (body: {
    tenantId: string;
    organizationName: string;
  }) => Promise<Response>;
  /** Test seam for the post-success navigation (defaults to a hard nav). */
  navigate?: (href: string) => void;
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

export function CreateOrganizationForm({
  submit = defaultSubmit,
  navigate = defaultNavigate,
}: CreateOrganizationFormProps) {
  const [tenantId, setTenantId] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
        // ready → cookies set, walk into the console; else the org exists but
        // needs a fresh login to complete operator-session entry.
        navigate(data.ready ? '/' : '/login?onboarded=1');
        return;
      }
      const body = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
      };
      setError(messageForCode(body.code ?? '', body.message ?? undefined));
      setPending(false);
    } catch {
      setError('네트워크 오류로 조직을 만들지 못했습니다. 잠시 후 다시 시도하세요.');
      setPending(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <OnboardingWhatHappens />

      <div>
        <label
          htmlFor="onboarding-tenant-id"
          className="block text-sm font-medium text-foreground"
        >
          조직 ID
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
            조직 ID 형식이 올바르지 않습니다.
          </p>
        )}
      </div>

      <div>
        <label
          htmlFor="onboarding-org-name"
          className="block text-sm font-medium text-foreground"
        >
          조직 이름
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
          className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {error}
        </div>
      )}

      <button
        type="submit"
        disabled={!canSubmit}
        data-testid="onboarding-submit"
        className="inline-flex w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50"
      >
        {pending ? '조직 만드는 중…' : '조직 만들고 콘솔 입장'}
      </button>
    </form>
  );
}
