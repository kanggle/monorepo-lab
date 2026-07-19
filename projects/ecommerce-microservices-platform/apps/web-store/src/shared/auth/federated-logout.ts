import 'server-only';
import { decodeServerJwt } from './decode-server-jwt';

/**
 * RP-initiated OIDC logout (GAP `end_session` / `/connect/logout`).
 *
 * NextAuth's `signOut` only clears the local session cookie — the GAP (SAS) IdP
 * session survives, so the next sign-in silently re-authenticates with no
 * credential form. To actually sign the user out we redirect the browser to
 * GAP's `end_session_endpoint` with an `id_token_hint` + a registered
 * `post_logout_redirect_uri`, so the IdP terminates ITS OWN session.
 *
 * web-store logout is client-driven (`next-auth/react` `signOut`), so this
 * server-only helper is exposed via the `/api/auth/end-session-url` route the
 * client calls BEFORE `signOut` (while the id_token cookie still exists).
 *
 * The registered `post-logout-redirect-uris` for `ecommerce-web-store-client`
 * (GAP V0012) are the app roots `http://localhost:3000/` + `http://web.ecommerce.local/`
 * — only made effective once the GAP `OAuthClientMapper` copies them onto the
 * SAS `RegisteredClient` (TASK-BE-328). We send the app origin + `/` (no path,
 * no query) so SAS matches it exactly.
 *
 * Mirrors the platform-console (TASK-PC-FE-033 / BE-328) and fan-platform-web
 * RP-initiated logout.
 */

const ISSUER = process.env.OIDC_ISSUER_URL ?? 'http://iam.local';
const CLIENT_ID =
  process.env.ECOMMERCE_WEB_STORE_CLIENT_ID ?? 'ecommerce-web-store-client';
const APP_URL = process.env.NEXTAUTH_URL ?? 'http://localhost:3000';

/**
 * Reads the GAP `id_token` from the encrypted NextAuth session cookie. Returns
 * null when there is no session / no id_token (e.g. a legacy session minted
 * before this field was captured) — the caller then falls back to a local-only
 * logout. Read via `getToken` (server-only) so the id_token is NEVER exposed to
 * the browser through `/api/auth/session`.
 */
async function getIdTokenHint(): Promise<string | null> {
  const token = await decodeServerJwt<{ idToken?: string }>();
  return token?.idToken ?? null;
}

/**
 * Builds the GAP `end_session` URL, or null when there is no `id_token_hint`
 * (→ caller does a local-only logout to the app root).
 */
export async function buildGapEndSessionUrl(): Promise<string | null> {
  const idToken = await getIdTokenHint();
  if (!idToken) return null;

  // Must EXACTLY match a registered post-logout-redirect-uri (GAP V0012): the
  // app origin + "/" (no path beyond root, no query string).
  const postLogout = new URL('/', APP_URL).toString();

  const endSession = new URL(`${ISSUER}/connect/logout`);
  endSession.searchParams.set('id_token_hint', idToken);
  endSession.searchParams.set('post_logout_redirect_uri', postLogout);
  endSession.searchParams.set('client_id', CLIENT_ID);
  return endSession.toString();
}
