'use client';

import { Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { saveTokens } from '@repo/api-client';

function OAuthCallbackContent() {
  const searchParams = useSearchParams();
  const [error, setError] = useState('');

  useEffect(() => {
    const accessToken = searchParams.get('accessToken');
    const refreshToken = searchParams.get('refreshToken');
    const oauthError = searchParams.get('error');

    if (oauthError) {
      setError('소셜 로그인에 실패했습니다. 다시 시도해주세요.');
      return;
    }

    if (accessToken && refreshToken) {
      saveTokens(accessToken, refreshToken);
      window.location.href = '/';
    } else {
      setError('로그인 정보를 받지 못했습니다.');
    }
  }, [searchParams]);

  if (error) {
    return (
      <div style={{ maxWidth: '400px', margin: '0 auto', padding: 'var(--space-16) var(--space-6)', textAlign: 'center' }}>
        <p style={{ color: 'var(--color-error)', marginBottom: 'var(--space-4)' }}>{error}</p>
        <a href="/login" className="btn btn-primary btn-lg" style={{ display: 'inline-block' }}>
          로그인 페이지로 돌아가기
        </a>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '400px', margin: '0 auto', padding: 'var(--space-16) var(--space-6)', textAlign: 'center' }}>
      <p style={{ color: 'var(--color-text-secondary)' }}>로그인 처리 중...</p>
    </div>
  );
}

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={null}>
      <OAuthCallbackContent />
    </Suspense>
  );
}
