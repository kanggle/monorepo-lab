'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { isApiError, ERROR_MESSAGES } from '@repo/types/guards';
import { useAuth } from '../model/auth-context';

export function SignupForm() {
  const router = useRouter();
  const { signup } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const nameTrimmed = name.trim();
  const isNameValid = nameTrimmed.length > 0 && nameTrimmed.length <= 50;
  const isEmailValid = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email) && email.length <= 255;
  const hasLetter = /[a-zA-Z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSpecial = /[^a-zA-Z0-9]/.test(password);
  const isPasswordValid = password.length >= 8 && password.length <= 128 && hasLetter && hasNumber && hasSpecial;
  const isValid = isNameValid && isEmailValid && isPasswordValid;
  const passwordRules = [
    { condition: password.length >= 8, label: '8자 이상' },
    { condition: hasLetter, label: '영문 포함' },
    { condition: hasNumber, label: '숫자 포함' },
    { condition: hasSpecial, label: '특수문자 포함' },
  ];

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || isSubmitting) return;

    setError('');
    setIsSubmitting(true);

    try {
      await signup({ email, password, name: name.trim() });
      router.push('/login');
    } catch (err) {
      if (isApiError(err)) {
        setError(ERROR_MESSAGES[err.code] ?? err.message ?? '회원가입에 실패했습니다.');
      } else {
        setError('회원가입에 실패했습니다.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <h1 className="auth-title">회원가입</h1>

      {error && (
        <div role="alert" className="alert-error">
          {error}
        </div>
      )}

      <div className="form-group">
        <label htmlFor="name" className="label">이름</label>
        <input
          id="name"
          type="text"
          className="input"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="이름 (최대 50자)"
          required
          maxLength={50}
          autoComplete="name"
        />
        {name.length > 0 && !isNameValid && (
          <p style={{ marginTop: 'var(--space-1)', fontSize: 'var(--font-size-xs)', color: 'var(--color-error)' }}>
            이름은 1~50자 사이여야 합니다
          </p>
        )}
      </div>

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
          maxLength={255}
          autoComplete="email"
        />
        {email.length > 0 && !isEmailValid && (
          <p style={{ marginTop: 'var(--space-1)', fontSize: 'var(--font-size-xs)', color: 'var(--color-error)' }}>
            올바른 이메일 형식이 아닙니다
          </p>
        )}
      </div>

      <div className="form-group">
        <label htmlFor="password" className="label">비밀번호</label>
        <input
          id="password"
          type="password"
          className="input"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="영문, 숫자, 특수문자 포함 8자 이상"
          required
          minLength={8}
          autoComplete="new-password"
        />
        {password.length > 0 && (
          <ul style={{
            marginTop: 'var(--space-2)', fontSize: 'var(--font-size-xs)',
            display: 'flex', flexDirection: 'column', gap: '2px',
          }}>
            {passwordRules.map(({ condition, label }) => (
              <li key={label} style={{ color: condition ? 'var(--color-success)' : 'var(--color-error)' }}>
                {condition ? '\u2713' : '\u2022'} {label}
              </li>
            ))}
          </ul>
        )}
      </div>

      <button
        type="submit"
        className="btn btn-primary btn-lg"
        style={{ width: '100%', marginTop: 'var(--space-2)' }}
        disabled={!isValid || isSubmitting}
      >
        {isSubmitting ? '가입 중...' : '회원가입'}
      </button>

      <p className="auth-footer">
        이미 계정이 있으신가요?{' '}
        <Link href="/login">로그인</Link>
      </p>
    </form>
  );
}
