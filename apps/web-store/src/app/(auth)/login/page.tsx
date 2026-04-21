'use client';

import { AuthCardLayout, LoginForm, useRedirectIfAuthenticated } from '@/features/auth';

export default function LoginPage() {
  const { isReady } = useRedirectIfAuthenticated();

  if (!isReady) return null;

  return (
    <AuthCardLayout>
      <LoginForm />
    </AuthCardLayout>
  );
}
