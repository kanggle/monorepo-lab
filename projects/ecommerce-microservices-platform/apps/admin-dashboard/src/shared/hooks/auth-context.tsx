'use client';

import { createContext, useCallback, useContext, useEffect, useMemo } from 'react';
import { SessionProvider, useSession, signIn, signOut } from 'next-auth/react';
import { setAccessToken, clearAccessToken } from '@/shared/auth/token-bridge';

/**
 * NextAuth-backed AuthContext for admin-dashboard. Same shim pattern as
 * web-store: maps `useSession()` onto the legacy `useAuth()` shape so the
 * AuthGuard, Sidebar, and other consumers do not need to be rewritten.
 */

export interface AuthUser {
  userId: string;
  email: string;
  name: string;
}

interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

interface AuthContextValue extends AuthState {
  /** Initiate GAP OIDC sign-in. Equivalent to `signIn('gap', { callbackUrl })`. */
  login: (callbackUrl?: string) => Promise<unknown> | unknown;
  /** Initiate `signOut()` and clear the api-client token bridge. */
  logout: () => Promise<unknown> | unknown;
}

const AuthContext = createContext<AuthContextValue | null>(null);

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

  useEffect(() => {
    if (isAuthenticated && session?.accessToken) {
      setAccessToken(session.accessToken);
    } else if (status === 'unauthenticated') {
      clearAccessToken();
    }
  }, [isAuthenticated, session, status]);

  const login = useCallback((callbackUrl?: string) => {
    return signIn('gap', { callbackUrl: callbackUrl ?? '/dashboard' });
  }, []);

  const logout = useCallback(async () => {
    clearAccessToken();
    // RP-initiated logout (TASK-PC-FE-033 / BE-328 / TASK-FE-071): fetch the GAP
    // end_session URL FIRST (while the id_token cookie still exists), then clear
    // the local NextAuth session, then hard-navigate to GAP so the IdP
    // terminates its own session — otherwise the next login silently
    // re-authenticates with no credential form. Falls back to a local-only
    // logout when there is no id_token_hint or the lookup fails.
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
    window.location.href = endSessionUrl ?? '/login';
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

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
