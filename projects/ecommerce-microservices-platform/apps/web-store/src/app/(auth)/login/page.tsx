'use client';

import { Suspense } from 'react';
import { AuthCardLayout, LoginForm, useRedirectIfAuthenticated } from '@/features/auth';

function LoginPageContent() {
  const { isReady } = useRedirectIfAuthenticated();
  if (!isReady) return null;
  return (
    <AuthCardLayout>
      <LoginForm />
    </AuthCardLayout>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginPageContent />
    </Suspense>
  );
}
