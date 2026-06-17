import 'server-only';
import { cookies } from 'next/headers';
import { getToken } from 'next-auth/jwt';
import { auth } from '@/shared/auth/auth';

/**
 * Server-only access to the authenticated session + bearer token. NEVER import
 * from a client component — `server-only` will throw at build time.
 *
 * Phase 4.5 F2 (token confidentiality): the access token is NOT on the public
 * NextAuth session anymore. It lives only on the encrypted server-side JWT
 * cookie, so this helper reads it via `getToken` (server-only) — the same path
 * `federated-logout.ts` uses for the id_token. Direct server-side fetches
 * (Server Actions, Server Components, route handlers, the BFF proxy) read the
 * bearer here; client components reach the backend through the same-origin
 * `/api/bff/[...path]` proxy which attaches the bearer server-side.
 */

export interface WebStoreSession {
  accessToken: string | null;
  accountId: string | null;
  tenantId: string | null;
  roles: string[];
}

const EMPTY: WebStoreSession = {
  accessToken: null,
  accountId: null,
  tenantId: null,
  roles: [],
};

const SECURE_COOKIE = '__Secure-authjs.session-token';
const PLAIN_COOKIE = 'authjs.session-token';

interface WebStoreJwt {
  accessToken?: string;
  accountId?: string;
  tenantId?: string | null;
  roles?: string[];
  error?: string;
}

/**
 * Decode the encrypted NextAuth JWT cookie server-side. Returns null when there
 * is no session or a silent refresh has failed (`error` set) — callers then
 * treat the request as unauthenticated.
 */
async function readServerToken(): Promise<WebStoreJwt | null> {
  const jar = await cookies();
  const cookieName = jar.get(SECURE_COOKIE) ? SECURE_COOKIE : PLAIN_COOKIE;
  const cookieHeader = jar
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join('; ');

  const token = (await getToken({
    req: { headers: { cookie: cookieHeader } },
    secret: process.env.NEXTAUTH_SECRET ?? '',
    // Auth.js v5 derives the decryption salt from the cookie name; pass both so
    // the secure (`__Secure-`) and plain variants both decode correctly.
    salt: cookieName,
    cookieName,
    secureCookie: cookieName === SECURE_COOKIE,
  })) as WebStoreJwt | null;

  if (!token) return null;
  if (token.error) return null; // refresh failed → force re-auth (F3 fallback)
  return token;
}

export async function getWebStoreSession(): Promise<WebStoreSession> {
  const token = await readServerToken();
  if (!token) return EMPTY;
  return {
    accessToken: token.accessToken ?? null,
    accountId: token.accountId ?? null,
    tenantId: token.tenantId ?? null,
    roles: token.roles ?? [],
  };
}

export async function isAuthenticated(): Promise<boolean> {
  const session = await auth();
  return Boolean(session?.accountId);
}
