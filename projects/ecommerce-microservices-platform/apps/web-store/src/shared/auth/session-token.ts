import { getToken } from 'next-auth/jwt';

/**
 * Decode-only access to the encrypted NextAuth session-token cookie, from a raw
 * `Cookie` header. 🔴 Never refreshes: unlike `auth()` it does not run the
 * `jwt` callback, so it can never spend (rotate) the refresh token.
 *
 * Deliberately free of `server-only` / `next/headers` so the edge middleware
 * (TASK-FE-106) and the `/api/auth/session` wrapper can use it alongside the
 * RSC helper `decode-server-jwt.ts`, keeping the cookie-name resolution in ONE
 * place.
 */

export const SECURE_SESSION_COOKIE = '__Secure-authjs.session-token';
export const PLAIN_SESSION_COOKIE = 'authjs.session-token';

/** Names of every cookie in a `Cookie` header (values are not needed here). */
export function cookieNames(cookieHeader: string): string[] {
  return cookieHeader
    .split(';')
    .map((part) => part.split('=')[0]?.trim() ?? '')
    .filter(Boolean);
}

/**
 * Every session-token cookie the header carries — the base name and Auth.js's
 * size chunks (`<name>.0`, `<name>.1`, …), for both the secure and plain names.
 */
export function sessionCookieNamesIn(cookieHeader: string): string[] {
  return cookieNames(cookieHeader).filter(
    (name) =>
      name === SECURE_SESSION_COOKIE ||
      name === PLAIN_SESSION_COOKIE ||
      name.startsWith(`${SECURE_SESSION_COOKIE}.`) ||
      name.startsWith(`${PLAIN_SESSION_COOKIE}.`),
  );
}

/**
 * Decode the session JWT carried by `cookieHeader`. Returns the raw payload cast
 * to `T`, or null when there is no session cookie / decoding fails.
 */
export async function decodeSessionCookieHeader<T>(cookieHeader: string): Promise<T | null> {
  // A chunked secure cookie has no base-name entry, only `.0`, `.1`, …
  const secure = sessionCookieNamesIn(cookieHeader).some((n) => n.startsWith(SECURE_SESSION_COOKIE));
  const cookieName = secure ? SECURE_SESSION_COOKIE : PLAIN_SESSION_COOKIE;
  const token = await getToken({
    req: { headers: { cookie: cookieHeader } },
    secret: process.env.NEXTAUTH_SECRET ?? '',
    // Auth.js v5 derives the decryption salt from the cookie name; pass both so
    // the secure (`__Secure-`) and plain variants both decode correctly.
    salt: cookieName,
    cookieName,
    secureCookie: secure,
  });
  return (token as T | null) ?? null;
}
