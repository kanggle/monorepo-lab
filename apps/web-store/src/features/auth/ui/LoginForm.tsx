'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { isApiError, ERROR_MESSAGES } from '@repo/types/guards';
import { useAuth } from '../model/auth-context';
import { OAuthButton } from './OAuthButton';
import { GoogleIcon, NaverIcon, InstagramIcon } from '@/shared/ui/icons/oauth';

function resolveRedirect(raw: string | null): string {
  if (!raw) return '/';
  try {
    const decoded = decodeURIComponent(raw);
    if (decoded.startsWith('/') && !decoded.startsWith('//')) return decoded;
  } catch {
    // ignore malformed redirect
  }
  return '/';
}

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isValid = email.includes('@') && password.length >= 8;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || isSubmitting) return;

    setError('');
    setIsSubmitting(true);

    try {
      await login({ email, password });
      router.push(resolveRedirect(searchParams?.get('redirect') ?? null));
    } catch (err) {
      if (isApiError(err)) {
        setError(ERROR_MESSAGES[err.code] ?? err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('로그인에 실패했습니다.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <h1 className="auth-title">로그인</h1>

      {error && (
        <div role="alert" className="alert-error">
          {error}
        </div>
      )}

      <div className="form-group">
        <label htmlFor="email" className="label">이메일</label>
        <input
          id="email"
          type="email"
          className="input"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="email@example.com"
          required
          autoComplete="email"
        />
      </div>

      <div className="form-group">
        <label htmlFor="password" className="label">비밀번호</label>
        <input
          id="password"
          type="password"
          className="input"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="8자 이상"
          required
          minLength={8}
          autoComplete="current-password"
        />
      </div>

      <button
        type="submit"
        className="btn btn-primary btn-lg"
        style={{ width: '100%', marginTop: 'var(--space-2)' }}
        disabled={!isValid || isSubmitting}
      >
        {isSubmitting ? '로그인 중...' : '로그인'}
      </button>

      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', margin: 'var(--space-6) 0' }}>
        <div style={{ flex: 1, height: 1, background: 'var(--color-border)' }} />
        <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>또는</span>
        <div style={{ flex: 1, height: 1, background: 'var(--color-border)' }} />
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
        <OAuthButton
          provider="google"
          label="Google로 로그인"
          icon={<GoogleIcon />}
        />
        <OAuthButton
          provider="naver"
          label="네이버로 로그인"
          icon={<NaverIcon />}
          style={{ backgroundColor: '#03C75A', color: '#fff', border: 'none' }}
        />
        <OAuthButton
          provider="instagram"
          label="Instagram으로 로그인"
          icon={<InstagramIcon />}
          style={{ background: 'linear-gradient(45deg, #f09433, #e6683c, #dc2743, #cc2366, #bc1888)', color: '#fff', border: 'none' }}
        />
      </div>

      <p className="auth-footer">
        계정이 없으신가요?{' '}
        <Link href="/signup">회원가입</Link>
      </p>
    </form>
  );
}
