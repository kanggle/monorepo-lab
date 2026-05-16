'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Logout — POSTs to the `/api/auth/logout` server route (GAP revoke + cookie
 * clear), then hard-navigates to `/login`. No client-side token handling.
 */
export function LogoutButton() {
  const [busy, setBusy] = useState(false);
  const onClick = async () => {
    setBusy(true);
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
      });
    } finally {
      window.location.assign('/login');
    }
  };
  return (
    <Button
      variant="secondary"
      onClick={onClick}
      disabled={busy}
      data-testid="logout-button"
    >
      로그아웃
    </Button>
  );
}
