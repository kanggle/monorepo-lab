'use client';

import { useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '../model/auth-context';

/**
 * GAP-backed login surface for web-store. After TASK-FE-067, web-store no
 * longer collects email/password directly — credentials are entered on the
 * GAP authorize page and exchanged via the OIDC `authorization_code` + PKCE
 * flow.
 *
 * Error surfaces:
 *   - `?error=account_type_mismatch` → an OPERATOR completed GAP login but is
 *     not allowed to enter web-store. Tell them to use the operator console
 *     (platform-console on the hub; the standalone deployment has no console).
 *   - `?error=Configuration` / `?error=...` → NextAuth-internal errors (most
 *     commonly "discovery doc fetch failed" when GAP is down). Generic
 *     fallback message.
 */

function resolveCallbackUrl(raw: string | null): string {
  if (!raw) return '/';
  try {
    const decoded = decodeURIComponent(raw);
    if (decoded.startsWith('/') && !decoded.startsWith('//')) return decoded;
  } catch {
    // ignore malformed
  }
  return '/';
}

function describeError(code: string | null): string | null {
  if (!code) return null;
  if (code === 'account_type_mismatch') {
    return 'operator 계정으로는 web-store 에 접근할 수 없습니다. 운영자 콘솔을 이용해 주세요.';
  }
  if (code === 'Configuration') {
    return '인증 서버 설정에 문제가 있습니다. 잠시 후 다시 시도해 주세요.';
  }
  if (code === 'AccessDenied') {
    return '로그인이 거부되었습니다. 권한을 확인해 주세요.';
  }
  // Surface anything else as a generic message.
  return '로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
}

export function LoginForm() {
  const searchParams = useSearchParams();
  const { login, isLoading } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const callbackUrl = useMemo(
    () => resolveCallbackUrl(searchParams?.get('from') ?? searchParams?.get('redirect') ?? null),
    [searchParams],
  );
  const errorMessage = useMemo(
    () => describeError(searchParams?.get('error') ?? null),
    [searchParams],
  );

  async function handleClick() {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await login(callbackUrl);
    } finally {
      // Note: signIn() typically redirects, so we won't actually reach here.
      setIsSubmitting(false);
    }
  }

  const disabled = isSubmitting || isLoading;

  return (
    <div>
      <h1 className="auth-title">로그인</h1>

      {errorMessage && (
        <div role="alert" className="alert-error">
          {errorMessage}
        </div>
      )}

      <p style={{ color: 'var(--color-text-secondary)', marginBottom: 'var(--space-6)' }}>
        Global Account 로 로그인하여 쇼핑을 계속하세요.
      </p>

      <button
        type="button"
        onClick={handleClick}
        className="btn btn-primary btn-lg"
        style={{ width: '100%' }}
        disabled={disabled}
      >
        {disabled ? '이동 중...' : 'Global Account 로 로그인'}
      </button>

      <p className="auth-footer" style={{ marginTop: 'var(--space-6)' }}>
        계정이 없으신가요?{' '}
        <a href="/signup">회원가입</a>
      </p>
    </div>
  );
}
