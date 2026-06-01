'use client';

import { useCallback, useEffect, useMemo } from 'react';
import { SessionProvider, useSession, signIn, signOut } from 'next-auth/react';
import { AuthContext, type AuthUser } from '@/shared/lib/auth-context';
import { setAccessToken, clearAccessToken } from '@/shared/auth/token-bridge';

export { useAuth } from '@/shared/lib/auth-context';

/**
 * GAP-backed AuthProvider — bridges NextAuth's `useSession()` onto the
 * legacy `useAuth()` interface used across web-store (Header, CartProvider,
 * WishlistButton, etc.).
 *
 * Two responsibilities:
 *   1. Map `useSession()` onto `{ user, isAuthenticated, isLoading }`
 *   2. Push the GAP-issued access token into the synchronous token bridge
 *      so the existing axios client can attach `Authorization: Bearer ...`
 *      without becoming async-aware.
 */
function AuthProviderInner({ children }: { children: React.ReactNode }) {
  const { data: session, status } = useSession();

  const isLoading = status === 'loading';
  const isAuthenticated = status === 'authenticated' && Boolean(session?.accountId);

  const user: AuthUser | null = useMemo(() => {
    if (!isAuthenticated || !session) return null;
    return {
      userId: session.accountId ?? '',
      email: session.user?.email ?? '',
      name: session.user?.name ?? '',
    };
  }, [isAuthenticated, session]);

  // Sync access token into the api-client bridge whenever the session changes.
  useEffect(() => {
    if (isAuthenticated && session?.accessToken) {
      setAccessToken(session.accessToken);
    } else if (status === 'unauthenticated') {
      clearAccessToken();
    }
  }, [isAuthenticated, session, status]);

  const login = useCallback((callbackUrl?: string) => {
    return signIn('gap', { callbackUrl: callbackUrl ?? '/' });
  }, []);

  const logout = useCallback(async () => {
    clearAccessToken();
    try {
      localStorage.removeItem('cart');
    } catch {
      // localStorage 접근 실패 시 무시
    }
    // RP-initiated logout (TASK-PC-FE-033 / BE-328): fetch the GAP end_session
    // URL FIRST (while the id_token cookie still exists), then clear the local
    // NextAuth session, then hard-navigate to GAP so the IdP terminates its own
    // session — otherwise the next login silently re-authenticates with no
    // credential form. Falls back to a local-only logout when there is no
    // id_token_hint or the lookup fails.
    let endSessionUrl: string | null = null;
    try {
      const res = await fetch('/api/auth/end-session-url', { cache: 'no-store' });
      if (res.ok) {
        endSessionUrl = ((await res.json()) as { url: string | null }).url;
      }
    } catch {
      // network/lookup failure → local-only logout
    }
    await signOut({ redirect: false });
    window.location.href = endSessionUrl ?? '/';
  }, []);

  const value = useMemo(
    () => ({ user, isAuthenticated, isLoading, login, logout }),
    [user, isAuthenticated, isLoading, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <AuthProviderInner>{children}</AuthProviderInner>
    </SessionProvider>
  );
}
