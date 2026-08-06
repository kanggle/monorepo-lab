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
  /**
   * Initiate the same GAP OIDC flow as {@link login}, but ask GAP to open on its
   * signup form rather than its login form (TASK-BE-578).
   *
   * <p>Deliberately a named method rather than a flag on `login`: the two differ
   * only in where the user lands, and a boolean at the call site would read as a
   * different kind of sign-in. It is still one mechanism underneath — the hint
   * rides on the same `signIn('iam')`, so the saved `/oauth2/authorize` request
   * (and therefore the tenant the account is born into) is identical.
   */
  signup: (callbackUrl?: string) => Promise<unknown> | unknown;
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
