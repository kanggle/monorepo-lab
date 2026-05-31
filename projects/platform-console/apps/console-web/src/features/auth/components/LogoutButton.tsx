'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Logout — POSTs to the `/api/auth/logout` server route (GAP revoke + cookie
 * clear), then hard-navigates to the URL it returns. That URL is the OIDC
 * end_session endpoint (`/connect/logout`) so the IdP terminates its OWN
 * session — without it the next login silently re-authenticates with no
 * credential form (TASK-PC-FE-033). Falls back to `/login` if the route
 * returns no `logoutUrl`. No client-side token handling.
 */
export function LogoutButton() {
  const [busy, setBusy] = useState(false);
  const onClick = async () => {
    setBusy(true);
    let logoutUrl = '/login';
    try {
      const res = await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
      });
      const body = (await res.json().catch(() => ({}))) as {
        logoutUrl?: string;
      };
      if (body.logoutUrl) logoutUrl = body.logoutUrl;
    } finally {
      window.location.assign(logoutUrl);
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
