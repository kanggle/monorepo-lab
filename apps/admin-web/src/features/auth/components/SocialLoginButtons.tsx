'use client';

import { useState } from 'react';
import { clientEnv } from '@/shared/config/env';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { Button } from '@/shared/ui/button';

/**
 * Social login buttons (Google / Kakao / Microsoft).
 *
 * Click flow:
 *   1. POST/GET `/api/auth/oauth/authorize?provider=&redirectUri=` (Next.js BFF)
 *   2. Receive `{ authorizationUrl, state }`. The BFF/auth-service stores
 *      `state` in Redis. Client never persists state.
 *   3. `window.location.assign(authorizationUrl)` to send the user to the
 *      provider's consent screen.
 *
 * Spec: specs/features/oauth-social-login.md.
 */

type Provider = 'google' | 'kakao' | 'microsoft';

interface ProviderConfig {
  id: Provider;
  label: string;
  icon: string;
  bgClass: string;
  textClass: string;
  borderClass: string;
}

const PROVIDERS: ProviderConfig[] = [
  {
    id: 'google',
    label: 'Google로 계속하기',
    icon: 'G',
    bgClass: 'bg-white',
    textClass: 'text-slate-900',
    borderClass: 'border-slate-300',
  },
  {
    id: 'kakao',
    label: 'Kakao로 계속하기',
    icon: 'K',
    bgClass: 'bg-[#FEE500]',
    textClass: 'text-slate-900',
    borderClass: 'border-[#FEE500]',
  },
  {
    id: 'microsoft',
    label: 'Microsoft로 계속하기',
    icon: 'M',
    bgClass: 'bg-white',
    textClass: 'text-slate-900',
    borderClass: 'border-slate-300',
  },
];

interface AuthorizeResponse {
  authorizationUrl: string;
  state: string;
}

export function SocialLoginButtons() {
  const [pending, setPending] = useState<Provider | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleClick(provider: Provider) {
    setError(null);
    setPending(provider);
    try {
      const redirectUri = `${clientEnv.NEXT_PUBLIC_APP_URL}/oauth/callback`;
      const url = `/api/auth/oauth/authorize?provider=${encodeURIComponent(
        provider,
      )}&redirectUri=${encodeURIComponent(redirectUri)}`;

      const res = await fetch(url, {
        method: 'GET',
        headers: { Accept: 'application/json' },
        credentials: 'include',
      });

      if (!res.ok) {
        const errBody = (await res.json().catch(() => ({}))) as {
          code?: string;
          message?: string;
        };
        const code = errBody.code ?? 'PROVIDER_ERROR';
        throw new ApiError(res.status, code, errBody.message ?? '소셜 로그인 시작 실패');
      }

      const data = (await res.json()) as AuthorizeResponse;
      window.location.assign(data.authorizationUrl);
    } catch (err) {
      setPending(null);
      if (err instanceof ApiError) {
        setError(messageForCode(err.code, err.message));
      } else {
        setError(messageForCode('PROVIDER_ERROR'));
      }
    }
  }

  const isDisabled = pending !== null;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-3" aria-hidden>
        <span className="h-px flex-1 bg-border" />
        <span className="text-xs text-muted-foreground">또는</span>
        <span className="h-px flex-1 bg-border" />
      </div>

      <div className="flex flex-col gap-2">
        {PROVIDERS.map((p) => (
          <Button
            key={p.id}
            type="button"
            variant="outline"
            disabled={isDisabled}
            aria-busy={pending === p.id}
            data-provider={p.id}
            onClick={() => handleClick(p.id)}
            className={`${p.bgClass} ${p.textClass} ${p.borderClass} hover:opacity-90`}
          >
            <span
              aria-hidden
              className="mr-2 inline-flex h-5 w-5 items-center justify-center rounded-sm bg-slate-200 text-xs font-bold text-slate-700"
            >
              {p.icon}
            </span>
            {pending === p.id ? '이동 중...' : p.label}
          </Button>
        ))}
      </div>

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
