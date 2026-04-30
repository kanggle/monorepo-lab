'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { clientEnv } from '@/shared/config/env';
import { ApiError, messageForCode } from '@/shared/api/errors';

/**
 * OAuth callback handler — runs on `/oauth/callback`.
 *
 * Reads `provider`, `code`, `state` from query params, POSTs to the BFF
 * `/api/auth/oauth/callback`, and on success redirects to `/accounts`.
 *
 * On error, redirects to `/login?error=<code>` so the LoginForm can surface
 * a user-friendly message.
 */

const SUPPORTED_PROVIDERS = new Set(['google', 'kakao', 'microsoft']);

export function OAuthCallbackHandler() {
  const router = useRouter();
  const params = useSearchParams();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  // Guard against React 19 strict-mode double-effect causing two POSTs.
  const dispatched = useRef(false);

  useEffect(() => {
    if (dispatched.current) return;
    dispatched.current = true;

    const provider = params?.get('provider');
    const code = params?.get('code');
    const state = params?.get('state');
    const providerError = params?.get('error');

    // Provider-side error (e.g., user denied consent on the provider page)
    if (providerError) {
      router.replace(`/login?error=${encodeURIComponent(providerError.toUpperCase())}`);
      return;
    }

    if (!provider || !SUPPORTED_PROVIDERS.has(provider) || !code || !state) {
      router.replace('/login?error=INVALID_STATE');
      return;
    }

    const redirectUri = `${clientEnv.NEXT_PUBLIC_APP_URL}/oauth/callback`;

    (async () => {
      try {
        const res = await fetch('/api/auth/oauth/callback', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ provider, code, state, redirectUri }),
        });
        if (!res.ok) {
          const body = (await res.json().catch(() => ({}))) as {
            code?: string;
            message?: string;
          };
          const code = body.code ?? 'PROVIDER_ERROR';
          throw new ApiError(res.status, code, body.message ?? 'OAuth callback failed');
        }
        router.replace('/accounts');
        router.refresh();
      } catch (err) {
        if (err instanceof ApiError) {
          setErrorMessage(messageForCode(err.code, err.message));
          router.replace(`/login?error=${encodeURIComponent(err.code)}`);
        } else {
          setErrorMessage(messageForCode('PROVIDER_ERROR'));
          router.replace('/login?error=PROVIDER_ERROR');
        }
      }
    })();
  }, [params, router]);

  if (errorMessage) {
    return (
      <p role="alert" className="text-sm text-destructive">
        {errorMessage}
      </p>
    );
  }
  return <p className="text-sm text-muted-foreground">잠시만 기다려주세요...</p>;
}
