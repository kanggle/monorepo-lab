/**
 * Pure NextAuth callback logic + the silent-refresh helper for web-store
 * (consumer-integration-guide § Phase 4.5 F2/F3).
 *
 * This module deliberately does NOT import `next-auth` — it holds only the
 * framework-agnostic logic so it can be unit-tested without triggering the
 * `NextAuth()` factory (which transitively imports `next/server` and fails to
 * resolve under the vitest/node test env). `auth.ts` composes these into the
 * `NextAuthConfig`.
 */

export interface IamOidcProfile {
  sub: string;
  email?: string;
  name?: string;
  preferred_username?: string;
  tenant_id?: string;
  account_id?: string;
  roles?: string[];
}

/**
 * ADR-MONO-035 (4b-1): the storefront requires the `CUSTOMER` role. Consumers
 * carry it; operators carry `ECOMMERCE_OPERATOR` / no `CUSTOMER`. Role-based replaces the
 * legacy `account_type === 'CONSUMER'` check (ADR-MONO-032 D5 step 4 removes
 * `account_type`).
 */
export const REQUIRED_CONSUMER_ROLE = 'CUSTOMER';

export function hasConsumerRole(roles: string[] | undefined | null): boolean {
  return Array.isArray(roles) && roles.includes(REQUIRED_CONSUMER_ROLE);
}

export const OIDC_ISSUER_URL = process.env.OIDC_ISSUER_URL ?? 'http://iam.local';
export const WEB_STORE_CLIENT_ID =
  process.env.ECOMMERCE_WEB_STORE_CLIENT_ID ?? 'ecommerce-web-store-client';
export const WEB_STORE_CLIENT_SECRET =
  process.env.ECOMMERCE_WEB_STORE_CLIENT_SECRET ?? '';

/**
 * Refresh the access token 60s before it expires (Phase 4.5 F3 — proactive
 * margin). The reactive 401 path (BFF proxy) is the floor; this window reduces
 * 401→re-issue round trips.
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
 */
export async function refreshAccessToken(
  refreshToken: string,
): Promise<RefreshedTokens | null> {
  try {
    const basic = Buffer.from(
      `${WEB_STORE_CLIENT_ID}:${WEB_STORE_CLIENT_SECRET}`,
    ).toString('base64');
    const res = await fetch(`${OIDC_ISSUER_URL}/oauth2/token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basic}`,
        Accept: 'application/json',
      },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
        client_id: WEB_STORE_CLIENT_ID,
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
      // Rotation: IAM returns a NEW refresh token (reuse-refresh-tokens=false).
      // If a token endpoint ever omits it, fall back to the one we sent so the
      // session does not silently lose its refresh capability.
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
 * silent refresh on subsequent calls (F3). Pure: only `fetch` (mockable) and
 * `Date.now` as side inputs.
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
    // (GAP end_session). Never surfaced to the public session.
    token.idToken = account.id_token;
    // A successful (re-)login clears any prior refresh failure marker.
    delete token.error;
  }
  if (profile) {
    const p = profile as IamOidcProfile;
    token.tenantId = p.tenant_id ?? token.tenantId;
    token.accountId = p.account_id ?? token.accountId;
    token.roles = p.roles ?? token.roles;
  }
  if (user && typeof user === 'object' && 'accountId' in user) {
    const u = user as { accountId?: string; tenantId?: string | null; roles?: string[] };
    token.accountId = u.accountId ?? token.accountId;
    token.tenantId = u.tenantId ?? token.tenantId;
    token.roles = u.roles ?? token.roles;
  }

  // Phase 4.5 F3 — proactive silent refresh. On subsequent calls (no
  // `account`), refresh if the access token is at/near expiry and a refresh
  // token is held. NextAuth serializes the `jwt` callback per session token, so
  // this is the in-flight-dedupe point (a single refresh per session JWT).
  if (!account) {
    const expiresAt = token.expiresAt as number | undefined;
    const refreshToken = token.refreshToken as string | undefined;
    const stillValid =
      typeof expiresAt === 'number' &&
      Date.now() < (expiresAt - REFRESH_MARGIN_SECONDS) * 1000;
    if (!stillValid && refreshToken && !token.error) {
      const refreshed = await refreshAccessToken(refreshToken);
      if (refreshed) {
        token.accessToken = refreshed.accessToken;
        token.refreshToken = refreshed.refreshToken;
        if (refreshed.idToken) token.idToken = refreshed.idToken;
        if (typeof refreshed.expiresAt === 'number') token.expiresAt = refreshed.expiresAt;
        delete token.error;
      } else {
        // Refresh failed → flag so session()/BFF force a full re-auth (F1).
        token.error = 'RefreshAccessTokenError';
      }
    }
  }
  return token;
}

/**
 * The `session` callback body — exposes ONLY non-sensitive identity claims to
 * client JS (F2). Degrades to anonymous when the role guard fails or a silent
 * refresh failed (F3 fallback). The access / refresh / id tokens are never
 * copied onto the public session.
 */
export function sessionCallback({ session, token }: SessionCallbackArgs): Record<string, unknown> {
  const refreshFailed = token.error === 'RefreshAccessTokenError';
  if (refreshFailed || !hasConsumerRole(token.roles as string[] | undefined)) {
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

/**
 * The `signIn` callback body — reject (redirect to the role_denied error) any
 * account lacking the `CUSTOMER` role before a JWT is issued.
 */
export function signInCallback(profile: IamOidcProfile | undefined): true | string {
  if (!hasConsumerRole(profile?.roles)) {
    return '/login?error=account_type_mismatch';
  }
  return true;
}
