'use client';

/**
 * Public AuthContext shape consumed by the rest of the app (Header, Cart,
 * WishlistButton, ReviewList, route guards). Backed by NextAuth v5 — the
 * `AuthProvider` in `features/auth/model/auth-context.tsx` uses
 * `useSession()` to populate this context and pushes the access token into
 * the api-client bridge.
 */

import { createContext, useContext } from 'react';

export interface AuthUser {
  /** GAP `sub` claim (== `accountId` for our use). */
  userId: string;
  email: string;
  name: string;
}

export interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

export interface AuthContextValue extends AuthState {
  /**
   * Initiate GAP OIDC sign-in. Equivalent to `signIn('iam', { callbackUrl })`.
   * The legacy `(email, password)` shape was retired with TASK-FE-067 — the
   * password is now collected by GAP itself.
   */
  login: (callbackUrl?: string) => Promise<unknown> | unknown;
  /** Initiate `signOut()` and clear the api-client token bridge. */
  logout: () => Promise<unknown> | unknown;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
