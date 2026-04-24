'use client';

import { useRequireAuth } from '@/features/auth';
import { ProfileLoader } from '@/features/user';

export default function ProfilePage() {
  useRequireAuth();
  return <ProfileLoader />;
}
