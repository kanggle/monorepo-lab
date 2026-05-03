import 'server-only';
import { auth } from '@/shared/auth/auth';

/**
 * Server-only access to the authenticated session + bearer token. NEVER import
 * this module from a client component — `server-only` will throw at build.
 *
 * The bearer token is read from the JWT session cookie and forwarded only to
 * `gatewayFetch()` calls inside Server Components / Server Actions / route
 * handlers. The browser bundle never sees it.
 */

export interface FanSession {
  accessToken: string | null;
  accountId: string | null;
  tenantId: string | null;
  roles: string[];
}

const EMPTY: FanSession = {
  accessToken: null,
  accountId: null,
  tenantId: null,
  roles: [],
};

export async function getFanSession(): Promise<FanSession> {
  const session = await auth();
  if (!session) return EMPTY;
  // The JWT decoded by next-auth is what the session callback returned; we
  // attached the token onto the JWT (not the public session) so we re-read it
  // here via `auth()` which re-decodes from the cookie.
  // next-auth v5 exposes the decoded JWT under `session.user`-like fields when
  // the `session` callback maps them; we additionally attach the access token
  // under a server-only helper to avoid leaking via `useSession()`.
  const accessToken = (session as unknown as { accessToken?: string }).accessToken ?? null;
  return {
    accessToken,
    accountId: session.accountId ?? null,
    tenantId: session.tenantId ?? null,
    roles: session.roles ?? [],
  };
}

export async function isAuthenticated(): Promise<boolean> {
  const session = await auth();
  return Boolean(session);
}
