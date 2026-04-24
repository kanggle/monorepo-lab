'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/shared/hooks';
import { useLoginForm } from '../hooks/use-login-form';

const styles = {
  main: { display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', backgroundColor: '#f5f5f5' } as const,
  form: { backgroundColor: '#fff', padding: '40px', borderRadius: '16px', boxShadow: '0 4px 24px rgba(0,0,0,0.08)', width: '100%', maxWidth: '420px' } as const,
  header: { textAlign: 'center', marginBottom: '32px' } as const,
  title: { fontSize: '1.5rem', fontWeight: 800, color: '#111827', marginBottom: '8px', letterSpacing: '-0.02em' } as const,
  titleAccent: { color: '#1A1A2E' } as const,
  subtitle: { fontSize: '0.875rem', color: '#9ca3af', margin: 0 } as const,
  error: { color: '#333', backgroundColor: '#f5f5f5', border: '1px solid #ddd', borderRadius: '8px', padding: '10px 14px', marginBottom: '20px', textAlign: 'center', fontSize: '0.8125rem' } as const,
  fieldGroup: { marginBottom: '20px' } as const,
  fieldGroupLast: { marginBottom: '28px' } as const,
  label: { display: 'block', marginBottom: '6px', fontWeight: 500, fontSize: '0.875rem', color: '#374151' } as const,
  input: { width: '100%', padding: '11px 14px', border: '1px solid #e5e7eb', borderRadius: '8px', boxSizing: 'border-box', fontSize: '0.875rem', backgroundColor: '#f9fafb', outline: 'none', transition: 'border-color 0.15s' } as const,
  submitBtn: { width: '100%', padding: '12px', borderRadius: '8px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', fontWeight: 600, fontSize: '0.9375rem', cursor: 'pointer', opacity: 1, transition: 'opacity 0.15s' } as const,
  submitBtnDisabled: { width: '100%', padding: '12px', borderRadius: '8px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', fontWeight: 600, fontSize: '0.9375rem', cursor: 'not-allowed', opacity: 0.5, transition: 'opacity 0.15s' } as const,
};

export function LoginForm() {
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();
  const { email, setEmail, password, setPassword, error, isSubmitting, isValid, handleSubmit } = useLoginForm();

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace('/dashboard');
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading || isAuthenticated) return null;

  return (
    <main style={styles.main}>
      <form onSubmit={handleSubmit} noValidate style={styles.form}>
        <div style={styles.header}>
          <h1 style={styles.title}>
            <span style={styles.titleAccent}>Admin</span> Login
          </h1>
          <p style={styles.subtitle}>
            관리자 계정으로 로그인하세요
          </p>
        </div>

        {error && (
          <div role="alert" style={styles.error}>
            {error}
          </div>
        )}

        <div style={styles.fieldGroup}>
          <label htmlFor="email" style={styles.label}>
            이메일
          </label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="email@example.com"
            required
            autoComplete="email"
            style={styles.input}
          />
        </div>

        <div style={styles.fieldGroupLast}>
          <label htmlFor="password" style={styles.label}>
            비밀번호
          </label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="8자 이상"
            required
            minLength={8}
            autoComplete="current-password"
            style={styles.input}
          />
        </div>

        <button
          type="submit"
          disabled={!isValid || isSubmitting}
          style={isValid && !isSubmitting ? styles.submitBtn : styles.submitBtnDisabled}
        >
          {isSubmitting ? '로그인 중...' : '로그인'}
        </button>
      </form>
    </main>
  );
}
