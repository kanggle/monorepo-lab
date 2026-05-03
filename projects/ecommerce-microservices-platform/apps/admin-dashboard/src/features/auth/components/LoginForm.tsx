'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/shared/hooks';

const styles = {
  main: { display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', backgroundColor: '#f5f5f5' } as const,
  form: { backgroundColor: '#fff', padding: '40px', borderRadius: '16px', boxShadow: '0 4px 24px rgba(0,0,0,0.08)', width: '100%', maxWidth: '420px' } as const,
  header: { textAlign: 'center', marginBottom: '32px' } as const,
  title: { fontSize: '1.5rem', fontWeight: 800, color: '#111827', marginBottom: '8px', letterSpacing: '-0.02em' } as const,
  titleAccent: { color: '#1A1A2E' } as const,
  subtitle: { fontSize: '0.875rem', color: '#9ca3af', margin: 0 } as const,
  error: { color: '#333', backgroundColor: '#f5f5f5', border: '1px solid #ddd', borderRadius: '8px', padding: '10px 14px', marginBottom: '20px', textAlign: 'center', fontSize: '0.8125rem' } as const,
  description: { color: '#6b7280', fontSize: '0.875rem', textAlign: 'center', marginBottom: '24px' } as const,
  submitBtn: { width: '100%', padding: '12px', borderRadius: '8px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', fontWeight: 600, fontSize: '0.9375rem', cursor: 'pointer', opacity: 1, transition: 'opacity 0.15s' } as const,
  submitBtnDisabled: { width: '100%', padding: '12px', borderRadius: '8px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', fontWeight: 600, fontSize: '0.9375rem', cursor: 'not-allowed', opacity: 0.5, transition: 'opacity 0.15s' } as const,
};

function describeError(code: string | null): string | null {
  if (!code) return null;
  if (code === 'account_type_mismatch' || code === 'AccountTypeMismatch') {
    return 'consumer 계정으로는 admin dashboard 에 접근할 수 없습니다. web-store 로 이동해 주세요.';
  }
  if (code === 'Configuration') {
    return '인증 서버 설정에 문제가 있습니다. 잠시 후 다시 시도해 주세요.';
  }
  if (code === 'AccessDenied') {
    return '로그인이 거부되었습니다. operator 권한을 확인해 주세요.';
  }
  return '로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
}

function resolveCallbackUrl(raw: string | null): string {
  if (!raw) return '/dashboard';
  try {
    const decoded = decodeURIComponent(raw);
    if (decoded.startsWith('/') && !decoded.startsWith('//')) return decoded;
  } catch {
    // ignore malformed
  }
  return '/dashboard';
}

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading, login } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const callbackUrl = useMemo(
    () => resolveCallbackUrl(searchParams?.get('from') ?? null),
    [searchParams],
  );
  const errorMessage = useMemo(
    () => describeError(searchParams?.get('error') ?? null),
    [searchParams],
  );

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace(callbackUrl);
    }
  }, [isAuthenticated, isLoading, router, callbackUrl]);

  if (isLoading || isAuthenticated) return null;

  async function handleClick() {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await login(callbackUrl);
    } finally {
      setIsSubmitting(false);
    }
  }

  const disabled = isSubmitting;

  return (
    <main style={styles.main}>
      <div style={styles.form}>
        <div style={styles.header}>
          <h1 style={styles.title}>
            <span style={styles.titleAccent}>Admin</span> Login
          </h1>
          <p style={styles.subtitle}>Global Account 로 로그인하세요</p>
        </div>

        {errorMessage && (
          <div role="alert" style={styles.error}>
            {errorMessage}
          </div>
        )}

        <p style={styles.description}>
          관리자 계정으로 admin-dashboard 에 접근하려면 Global Account 로 인증해야 합니다.
        </p>

        <button
          type="button"
          onClick={handleClick}
          disabled={disabled}
          style={!disabled ? styles.submitBtn : styles.submitBtnDisabled}
        >
          {disabled ? '이동 중...' : 'Global Account 로 로그인'}
        </button>
      </div>
    </main>
  );
}
