import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/shared/hooks';
import { isApiErrorResponse, getErrorMessage, ERROR_MESSAGES } from '@repo/types/guards';

export function useLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(
    searchParams.get('error') === 'oauth_failed'
      ? 'Google 로그인에 실패했습니다. 다시 시도해 주세요.'
      : '',
  );
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isValid = email.includes('@') && password.length >= 8;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || isSubmitting) return;

    setError('');
    setIsSubmitting(true);

    try {
      await login({ email, password });
      router.push('/dashboard');
    } catch (err) {
      if (isApiErrorResponse(err)) {
        setError(ERROR_MESSAGES[err.code] ?? err.message);
      } else {
        setError(getErrorMessage(err, '로그인에 실패했습니다.'));
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return {
    email,
    setEmail,
    password,
    setPassword,
    error,
    isSubmitting,
    isValid,
    handleSubmit,
  };
}
