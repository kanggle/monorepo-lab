import 'server-only';
import { cookies } from 'next/headers';
import { getToken } from 'next-auth/jwt';

const SECURE_COOKIE = '__Secure-authjs.session-token';
const PLAIN_COOKIE = 'authjs.session-token';

/**
 * Decode the encrypted NextAuth session-token cookie server-side via `getToken`,
 * so token internals (access token, id_token) are NEVER exposed to the browser
 * through `/api/auth/session`. Returns the raw decoded JWT payload cast to `T`,
 * or null when there is no session cookie / decoding fails. Callers apply their
 * own shape validation and error handling.
 *
 * Shared by `session.ts` (bearer/session) and `federated-logout.ts` (id_token
 * hint) so the cookie-name resolution and `getToken` wiring stay in one place.
 */
export async function decodeServerJwt<T>(): Promise<T | null> {
  const jar = await cookies();
  const cookieName = jar.get(SECURE_COOKIE) ? SECURE_COOKIE : PLAIN_COOKIE;
  const cookieHeader = jar
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join('; ');

  const token = await getToken({
    req: { headers: { cookie: cookieHeader } },
    secret: process.env.NEXTAUTH_SECRET ?? '',
    // Auth.js v5 derives the decryption salt from the cookie name; pass both so
    // the secure (`__Secure-`) and plain variants both decode correctly.
    salt: cookieName,
    cookieName,
    secureCookie: cookieName === SECURE_COOKIE,
  });

  return (token as T | null) ?? null;
}
