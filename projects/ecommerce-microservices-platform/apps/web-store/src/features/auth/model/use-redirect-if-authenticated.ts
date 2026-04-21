'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from './auth-context';

interface RedirectIfAuthenticatedResult {
  isReady: boolean;
}

export function useRedirectIfAuthenticated(): RedirectIfAuthenticatedResult {
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace('/');
    }
  }, [isLoading, isAuthenticated, router]);

  return { isReady: !isLoading && !isAuthenticated };
}
