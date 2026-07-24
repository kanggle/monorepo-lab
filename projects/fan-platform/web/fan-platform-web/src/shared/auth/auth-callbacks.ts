/**
 * Pure NextAuth callback logic + the silent-refresh helper for fan-platform-web
 * (consumer-integration-guide § Phase 4.5 F3).
 *
 * This module deliberately does NOT import `next-auth` — it holds only the
 * framework-agnostic logic so it can be unit-tested without triggering the
 * `NextAuth()` factory (which transitively imports `next/server` and fails to
 * resolve under the vitest/node test env). `auth.ts` composes these into the
 * `NextAuthConfig`.
 *
 * Fan-web reads OIDC config from `@/shared/config/env` (not process.env
 * directly) — this module follows the same convention.
 */

import { env } from '@/shared/config/env';

/**
 * Refresh the access token 60s before it expires (Phase 4.5 F3 — proactive
 * margin). The reactive re-auth path is the floor; this window reduces the
 * risk of sending an already-expired token on the next API call.
 */
export const REFRESH_MARGIN_SECONDS = 60;

export interface RefreshedTokens {
  accessToken: string;
  refreshToken: string;
  idToken?: string;
  expiresAt?: number;
}

/**
 * Exchange a stored `refresh_token` for a rotated access/refresh pair at the
 * IAM `/oauth2/token` endpoint (RFC 6749 § 6, `client_secret_basic`). Returns
 * null on any non-2xx / malformed response so the caller can flag the session
 * for a full re-auth (F1 fallback). Server-only — invoked from the `jwt`
 * callback which runs on the server.
 *
 * Rotation: IAM issues a NEW refresh token (`reuse-refresh-tokens=false`).
 * If the token endpoint ever omits `refresh_token` in its response, this
 * falls back to the token that was sent — preventing the session from silently
 * losing its refresh capability.
 */
export async function refreshAccessToken(
  refreshToken: string,
): Promise<RefreshedTokens | null> {
  try {
    const basic = Buffer.from(
      `${env.oidcClientId}:${env.oidcClientSecret}`,
    ).toString('base64');
    const res = await fetch(`${env.oidcIssuerUrl}/oauth2/token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basic}`,
        Accept: 'application/json',
      },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
        client_id: env.oidcClientId,
      }),
      cache: 'no-store',
    });
    if (!res.ok) return null;
    const data = (await res.json()) as {
      access_token?: string;
      refresh_token?: string;
      id_token?: string;
      expires_in?: number;
    };
    if (!data.access_token) return null;
    return {
      accessToken: data.access_token,
      refreshToken: data.refresh_token ?? refreshToken,
      idToken: data.id_token,
      expiresAt:
        typeof data.expires_in === 'number'
          ? Math.floor(Date.now() / 1000) + data.expires_in
          : undefined,
    };
  } catch {
    return null;
  }
}

// Minimal structural types mirroring the NextAuth callback args we use. Kept
// loose (the real next-auth types are imported only in auth.ts).
/* eslint-disable @typescript-eslint/no-explicit-any */
type JwtToken = Record<string, any>;

interface JwtCallbackArgs {
  token: JwtToken;
  account?: {
    access_token?: string;
    refresh_token?: string;
    expires_at?: number;
    id_token?: string;
  } | null;
  profile?: unknown;
  user?: unknown;
}

interface SessionCallbackArgs {
  session: Record<string, any>;
  token: JwtToken;
}
/* eslint-enable @typescript-eslint/no-explicit-any */

/**
 * The `jwt` callback body — persists tokens on sign-in and performs proactive
 * silent refresh on subsequent calls (Phase 4.5 F3). Pure: only `fetch`
 * (mockable) and `Date.now` as side inputs.
 *
 * NextAuth serialises the `jwt` callback per session token, providing
 * in-flight deduplication of refresh calls.
 */
export async function jwtCallback({
  token,
  account,
  profile,
  user,
}: JwtCallbackArgs): Promise<JwtToken> {
  if (account) {
    token.accessToken = account.access_token;
    token.refreshToken = account.refresh_token;
    token.expiresAt = account.expires_at;
    // Keep the id_token server-side as the RP-initiated-logout id_token_hint
    // (IAM end_session). Never surfaced to the public session.
    token.idToken = account.id_token;
    // A successful (re-)login clears any prior refresh failure marker.
    delete token.error;
  }
  if (profile) {
    const p = profile as {
      tenant_id?: string;
      account_id?: string;
      roles?: string[];
    };
    token.tenantId = p.tenant_id ?? token.tenantId;
    token.accountId = p.account_id ?? token.accountId;
    token.roles = p.roles ?? token.roles;
  }
  if (user && typeof user === 'object' && 'accountId' in user) {
    const u = user as {
      accountId?: string;
      tenantId?: string | null;
      roles?: string[];
    };
    token.accountId = u.accountId ?? token.accountId;
    token.tenantId = u.tenantId ?? token.tenantId;
    token.roles = u.roles ?? token.roles;
  }

  // Phase 4.5 F3 — proactive silent refresh. On subsequent calls (no
  // `account`), refresh if the access token is at/near expiry and a refresh
  // token is held. Skip if there is already a `token.error` (prevents
  // infinite retry loops on a broken refresh endpoint).
  if (!account) {
    const expiresAt = token.expiresAt as number | undefined;
    const storedRefreshToken = token.refreshToken as string | undefined;
    const stillValid =
      typeof expiresAt === 'number' &&
      Date.now() < (expiresAt - REFRESH_MARGIN_SECONDS) * 1000;
    if (!stillValid && storedRefreshToken && !token.error) {
      const refreshed = await refreshAccessToken(storedRefreshToken);
      if (refreshed) {
        token.accessToken = refreshed.accessToken;
        token.refreshToken = refreshed.refreshToken;
        if (refreshed.idToken) token.idToken = refreshed.idToken;
        if (typeof refreshed.expiresAt === 'number')
          token.expiresAt = refreshed.expiresAt;
        delete token.error;
      } else {
        // Refresh failed → flag so middleware forces a full re-auth (F1).
        token.error = 'RefreshAccessTokenError';
      }
    }
  }
  return token;
}

/**
 * Select the bearer access token from a decoded Auth.js JWT for server-side
 * gateway calls. Pure counterpart of the `getToken()` plumbing in the
 * server-only `session.ts` (kept here so it is unit-testable without the
 * `server-only` / `next/headers` boundary).
 *
 * Returns null when: there is no JWT, a prior silent refresh failed
 * (`RefreshAccessTokenError` — sending the known-stale token would just 401),
 * or the `accessToken` claim is missing / not a non-empty string. The token is
 * NEVER copied onto the public session (F2) — this reads the raw JWT instead,
 * which is why the token survives here even though `sessionCallback` strips it.
 */
export function selectAccessToken(jwt: JwtToken | null | undefined): string | null {
  if (!jwt) return null;
  if (jwt.error === 'RefreshAccessTokenError') return null;
  const accessToken = jwt.accessToken;
  return typeof accessToken === 'string' && accessToken.length > 0 ? accessToken : null;
}

/**
 * The `session` callback body — exposes ONLY non-sensitive identity claims to
 * client JS (F2). Degrades to anonymous when a silent refresh failed (F3
 * fallback). The access / refresh / id tokens are never copied onto the
 * public session object.
 */
export function sessionCallback({
  session,
  token,
}: SessionCallbackArgs): Record<string, unknown> {
  if (token.error === 'RefreshAccessTokenError') {
    // Degrade to anonymous so the `authorized` middleware callback receives
    // `auth = null`-equivalent and redirects to /login?from=…
    return {
      ...session,
      user: undefined,
      accountId: null,
      tenantId: null,
      roles: [],
    };
  }
  session.accountId = (token.accountId as string | null | undefined) ?? null;
  session.tenantId = (token.tenantId as string | null | undefined) ?? null;
  session.roles = (token.roles as string[] | undefined) ?? [];
  return session;
}
