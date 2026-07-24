import 'server-only';
import { headers } from 'next/headers';
import { getToken } from 'next-auth/jwt';
import { auth } from '@/shared/auth/auth';
import { selectAccessToken } from '@/shared/auth/auth-callbacks';

/**
 * Server-only access to the authenticated session + bearer token. NEVER import
 * this module from a client component — `server-only` will throw at build.
 *
 * The bearer token is read from the JWT session cookie and forwarded only to
 * `gatewayFetch()` calls inside Server Components / Server Actions / route
 * handlers. The browser bundle never sees it.
 *
 * <p><b>Why the token is read from the JWT and not from `auth()`:</b> the
 * `session` callback deliberately strips the access/refresh/id tokens off the
 * public session object (F2 — enforced by a unit test asserting
 * `session.accessToken` is undefined). `auth()` returns that already-stripped
 * session, so re-reading `session.accessToken` always yielded `null` and every
 * Server-Component data fetch went out unauthenticated → gateway 401 → the
 * "failed to load" error states (TASK-FAN-FE-008). We therefore decode the
 * encrypted session cookie directly with `getToken()`, which keeps the token
 * server-side and leaves the F2 invariant intact.
 */

export interface FanSession {
  accessToken: string | null;
  accountId: string | null;
  tenantId: string | null;
  roles: string[];
  // Signed-in fan's email / display name (from the OIDC `email`/`profile` scopes).
  // Non-sensitive identity, safe to forward to a client component that opens the
  // PG window (KG이니시스 V2 requires the buyer email — see portone-checkout.ts).
  email: string | null;
  displayName: string | null;
}

const EMPTY: FanSession = {
  accessToken: null,
  accountId: null,
  tenantId: null,
  roles: [],
  email: null,
  displayName: null,
};

/**
 * Decode the Auth.js session cookie server-side and return the stored bearer.
 * `cookieName`/`salt` default to `authjs.session-token` (or the `__Secure-`
 * prefixed name when the deployment URL is https), matching what Auth.js wrote.
 * Returns null when there is no cookie, no secret, or a failed silent refresh
 * flagged the session (`RefreshAccessTokenError`) — the caller then behaves as
 * unauthenticated rather than sending a known-stale token.
 */
async function readAccessTokenFromJwt(): Promise<string | null> {
  const secret = process.env.NEXTAUTH_SECRET;
  if (!secret) return null;
  try {
    const jwt = await getToken({
      req: { headers: await headers() },
      secret,
      secureCookie: (process.env.NEXTAUTH_URL ?? '').startsWith('https://'),
    });
    return selectAccessToken(jwt);
  } catch {
    return null;
  }
}

export async function getFanSession(): Promise<FanSession> {
  const session = await auth();
  if (!session) return EMPTY;
  const accessToken = await readAccessTokenFromJwt();
  return {
    accessToken,
    accountId: session.accountId ?? null,
    tenantId: session.tenantId ?? null,
    roles: session.roles ?? [],
    email: session.user?.email ?? null,
    displayName: session.user?.name ?? null,
  };
}

export async function isAuthenticated(): Promise<boolean> {
  const session = await auth();
  return Boolean(session);
}
