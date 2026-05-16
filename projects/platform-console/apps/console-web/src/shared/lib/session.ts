import { cookies } from 'next/headers';

/**
 * HttpOnly cookie session contract (single source of cookie names + options).
 *
 * Tokens live ONLY in these HttpOnly Secure SameSite=Strict cookies — never
 * localStorage / sessionStorage (frontend-app.md § Authentication forbidden
 * pattern; architecture.md § Forbidden Dependencies). Client JavaScript can
 * never read the access/refresh token.
 */

export const ACCESS_COOKIE = 'console_access_token';
export const REFRESH_COOKIE = 'console_refresh_token';
/** Active tenant for tenant-scoped server-side domain calls (multi-tenant). */
export const TENANT_COOKIE = 'console_active_tenant';
/** Short-lived PKCE/state cookies used only between /login and /callback. */
export const PKCE_VERIFIER_COOKIE = 'console_pkce_verifier';
export const OAUTH_STATE_COOKIE = 'console_oauth_state';

export const tokenCookieOpts = {
  httpOnly: true,
  secure: true,
  sameSite: 'strict' as const,
  path: '/',
};

/** PKCE/state cookies: HttpOnly, short maxAge (auth flow window only). */
export const transientCookieOpts = {
  ...tokenCookieOpts,
  maxAge: 600, // 10 min — bounded login round-trip
};

/** Server-side read of the current operator's access token (or null). */
export async function getAccessToken(): Promise<string | null> {
  const jar = await cookies();
  return jar.get(ACCESS_COOKIE)?.value ?? null;
}

/** Server-side read of the active tenant (or null when none selected). */
export async function getActiveTenant(): Promise<string | null> {
  const jar = await cookies();
  return jar.get(TENANT_COOKIE)?.value ?? null;
}

export async function isAuthenticated(): Promise<boolean> {
  return (await getAccessToken()) !== null;
}
