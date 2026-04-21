'use client';

import { AuthCardLayout, SignupForm, useRedirectIfAuthenticated } from '@/features/auth';

export default function SignupPage() {
  const { isReady } = useRedirectIfAuthenticated();

  if (!isReady) return null;

  return (
    <AuthCardLayout>
      <SignupForm />
    </AuthCardLayout>
  );
}
