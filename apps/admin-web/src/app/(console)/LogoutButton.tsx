'use client';

import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/button';

export function LogoutButton() {
  const router = useRouter();

  async function handleLogout() {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
    router.push('/login');
    router.refresh();
  }

  return (
    <Button variant="ghost" size="sm" onClick={handleLogout}>
      로그아웃
    </Button>
  );
}
