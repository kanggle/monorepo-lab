import 'server-only';
import { cookies } from 'next/headers';
import { decodeSessionCookieHeader } from './session-token';

/**
 * Decode the encrypted NextAuth session-token cookie server-side via `getToken`,
 * so token internals (access token, id_token) are NEVER exposed to the browser
 * through `/api/auth/session`. Returns the raw decoded JWT payload cast to `T`,
 * or null when there is no session cookie / decoding fails. Callers apply their
 * own shape validation and error handling.
 *
 * Shared by `session.ts` (bearer/session) and `federated-logout.ts` (id_token
 * hint). The cookie-name resolution + `getToken` wiring live in
 * `session-token.ts` (TASK-FE-106 — also used by the edge middleware, which may
 * not import `server-only`).
 */
export async function decodeServerJwt<T>(): Promise<T | null> {
  const jar = await cookies();
  const cookieHeader = jar
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join('; ');
  return decodeSessionCookieHeader<T>(cookieHeader);
}
