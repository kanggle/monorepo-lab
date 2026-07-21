import 'server-only';
import { cookies } from 'next/headers';
import { getToken } from 'next-auth/jwt';
import { env } from '@/shared/config/env';

/**
 * RP-initiated OIDC logout (GAP `end_session` / `/connect/logout`).
 *
 * NextAuth's `signOut` only clears the local session cookie — the GAP (SAS) IdP
 * session survives, so the next sign-in silently re-authenticates with no
 * credential form. To actually sign the user out we must redirect the browser
 * to GAP's `end_session_endpoint` with an `id_token_hint` + a registered
 * `post_logout_redirect_uri`, so the IdP terminates ITS OWN session.
 *
 * The registered `post-logout-redirect-uris` for `fan-platform-user-flow-client`
 * (GAP V0011 + V0028) are the app roots `http://localhost:3002/` (dev, TASK-MONO-460) + `http://fan-platform.local/`
 * — only made effective once the GAP `OAuthClientMapper` copies them onto the
 * SAS `RegisteredClient` (TASK-BE-328). We therefore send the app origin + `/`
 * (no path, no query) so SAS matches it exactly.
 *
 * Mirrors the platform-console fix (TASK-PC-FE-033 / BE-328); see that task for
 * the revoke-≠-session-termination rationale.
 */

const SECURE_COOKIE = '__Secure-authjs.session-token';
const PLAIN_COOKIE = 'authjs.session-token';

/**
 * Reads the GAP `id_token` from the encrypted NextAuth session cookie. Returns
 * null when there is no session / no id_token (e.g. a legacy session minted
 * before this field was captured) — the caller then falls back to a local-only
 * logout. The id_token is read via `getToken` (server-only) so it is NEVER
 * exposed to the browser through `/api/auth/session`.
 */
export async function getIdTokenHint(): Promise<string | null> {
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

  return (token as { idToken?: string } | null)?.idToken ?? null;
}

/**
 * Builds the GAP `end_session` URL, or null when there is no `id_token_hint`
 * (→ caller does a local-only logout to the app root).
 */
export async function buildGapEndSessionUrl(): Promise<string | null> {
  const idToken = await getIdTokenHint();
  if (!idToken) return null;

  // Must EXACTLY match a registered post-logout-redirect-uri (GAP V0011): the
  // app origin + "/" (no path beyond root, no query string).
  const postLogout = new URL('/', env.nextAuthUrl).toString();

  const endSession = new URL(`${env.oidcIssuerUrl}/connect/logout`);
  endSession.searchParams.set('id_token_hint', idToken);
  endSession.searchParams.set('post_logout_redirect_uri', postLogout);
  endSession.searchParams.set('client_id', env.oidcClientId);
  return endSession.toString();
}
