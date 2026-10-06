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

/**
 * TASK-FAN-FE-027 — the claim the `jwt` callback sets (and the `session`
 * callback surfaces) when ITS refresh attempt got IAM's rotation-shaped
 * refusal. Read by `session-route.ts` only; never meaningful to client code.
 * (Same name and meaning as ecommerce web-store, TASK-FE-106.)
 */
export const REFRESH_RACE_LOST_CLAIM = 'refreshRaceLost';

export interface RefreshedTokens {
  accessToken: string;
  refreshToken: string;
  idToken?: string;
  expiresAt?: number;
}

/**
 * The outcome of one refresh_token grant (TASK-FAN-FE-027, copied from
 * web-store TASK-FE-106).
 *
 *   - `ok`               → IAM rotated the pair.
 *   - `rotation_suspect` → IAM `400 invalid_grant`. 🔴 Since iam `TASK-BE-606`
 *     this is ALSO what the LOSER of a same-token concurrent refresh receives
 *     within the 30s grace window ("refused, nothing revoked") — by error shape
 *     alone it cannot be told apart from a genuinely dead refresh token, so the
 *     caller must not conclude "dead" from it before looking again (see
 *     `session-route.ts`).
 *   - `failed`           → anything else (other 4xx, 5xx, network, malformed body).
 */
export type RefreshGrantResult =
  | { kind: 'ok'; tokens: RefreshedTokens }
  | { kind: 'rotation_suspect' }
  | { kind: 'failed' };

/**
 * The ONE "does this session JWT still hold a usable access token" judge
 * (TASK-FAN-FE-027). Used by the `jwt` callback to decide whether to refresh,
 * by the `/api/auth/session` retry hop to decide whether a concurrent winner's
 * rotated cookie has landed, and by the header to tell `SessionKeeper` that
 * the server render used an expired bearer — a second copy of this predicate
 * is how those would quietly disagree.
 */
export function hasFreshAccessToken(
  token: { expiresAt?: unknown },
  nowMs: number = Date.now(),
): boolean {
  const expiresAt = token.expiresAt;
  return typeof expiresAt === 'number' && nowMs < (expiresAt - REFRESH_MARGIN_SECONDS) * 1000;
}

/**
 * Exchange a stored `refresh_token` for a rotated access/refresh pair at the
 * IAM `/oauth2/token` endpoint (RFC 6749 § 6, `client_secret_basic`), and say
 * which kind of failure it was when it fails ({@link RefreshGrantResult}).
 * Server-only — invoked from the `jwt` callback which runs on the server.
 *
 * Rotation: IAM issues a NEW refresh token (`reuse-refresh-tokens=false`).
 * If the token endpoint ever omits `refresh_token` in its response, this
 * falls back to the token that was sent — preventing the session from silently
 * losing its refresh capability.
 */
export async function refreshTokenGrant(refreshToken: string): Promise<RefreshGrantResult> {
  try {
    const basic = Buffer.from(
      `${env.oidcClientId}:${env.oidcClientSecret}`,
    ).toString('base64');
    // DEMO-URL-EXEMPT: oidc-issuer — the issuer is NOT derived from the demo IP.
    //   ADR-MONO-069 (C2) pinned a STABLE name (`https://auth.hubwang.com`), and this
    //   string is compared CHARACTER-BY-CHARACTER against the token's `iss` claim ⇒
    //   silently rewriting it breaks login. In the containerised demo the value is
    //   already injected in `DEMO_DOMAIN` form by `demo.env`.
    //   TASK-MONO-623 — the same declaration console-web carries on its four
    //   OIDC legs; this marker is what `scripts/check-fetch-resolution.mjs` reads.
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
    if (!res.ok) {
      const body = (await res.json().catch(() => ({}))) as { error?: unknown };
      return res.status === 400 && body?.error === 'invalid_grant'
        ? { kind: 'rotation_suspect' }
        : { kind: 'failed' };
    }
    const data = (await res.json()) as {
      access_token?: string;
      refresh_token?: string;
      id_token?: string;
      expires_in?: number;
    };
    if (!data.access_token) return { kind: 'failed' };
    return {
      kind: 'ok',
      tokens: {
        accessToken: data.access_token,
        refreshToken: data.refresh_token ?? refreshToken,
        idToken: data.id_token,
        expiresAt:
          typeof data.expires_in === 'number'
            ? Math.floor(Date.now() / 1000) + data.expires_in
            : undefined,
      },
    };
  } catch {
    return { kind: 'failed' };
  }
}

/**
 * Tokens-or-null view of {@link refreshTokenGrant} (kept for existing callers
 * and tests). Returns null on ANY failure, including a rotation-race refusal;
 * callers that must tell those apart use {@link refreshTokenGrant}.
 */
export async function refreshAccessToken(
  refreshToken: string,
): Promise<RefreshedTokens | null> {
  const result = await refreshTokenGrant(refreshToken);
  return result.kind === 'ok' ? result.tokens : null;
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
 * 🔴 TASK-FAN-FE-027 — this used to say "NextAuth serialises the `jwt` callback
 * per session token, providing in-flight deduplication of refresh calls".
 * Nothing does: not across parallel requests of one browser, not across tabs,
 * and not across serverless instances. See the refresh block below.
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
  //
  // 🔴 TASK-FAN-FE-027 — there is NO in-flight dedupe here. Several requests
  // carrying the SAME expired session cookie each send the SAME refresh token;
  // IAM rotates for the first and refuses the rest within its 30s grace window
  // (iam TASK-BE-606/608: `400 invalid_grant`, nothing revoked). A loser cannot
  // learn the winner's rotated pair from here (it lives in the winner's
  // response cookie), so it must neither keep the old refresh token as if
  // nothing happened (re-sending it after 30s IS reuse → family revoke) nor be
  // allowed to overwrite the winner's cookie. It flags the token terminally
  // (same `error` as before — if this cookie is ever written the result is a
  // logout, never a replay) and marks it `refreshRaceLost` so the
  // `/api/auth/session` wrapper (`session-route.ts`) discards this response —
  // cookie included — and lets the browser ask again with whatever cookie it
  // holds by then.
  //
  // 🔴 And this callback must only ever run where its result is written back:
  // `GET /api/auth/session`. The request-less `auth()` form runs it and then
  // drops the rotated cookie — every server-side read is decode-only
  // (`session-token.ts`), never `auth()`.
  if (!account) {
    // The marker is per-call: a token whose cookie escaped with it must not
    // keep re-triggering the retry hop on every later read.
    delete token[REFRESH_RACE_LOST_CLAIM];
    const storedRefreshToken = token.refreshToken as string | undefined;
    if (!hasFreshAccessToken(token) && storedRefreshToken && !token.error) {
      const result = await refreshTokenGrant(storedRefreshToken);
      if (result.kind === 'ok') {
        const refreshed = result.tokens;
        token.accessToken = refreshed.accessToken;
        token.refreshToken = refreshed.refreshToken;
        if (refreshed.idToken) token.idToken = refreshed.idToken;
        if (typeof refreshed.expiresAt === 'number')
          token.expiresAt = refreshed.expiresAt;
        delete token.error;
      } else {
        // Refresh failed → flag so middleware forces a full re-auth (F1).
        token.error = 'RefreshAccessTokenError';
        if (result.kind === 'rotation_suspect') token[REFRESH_RACE_LOST_CLAIM] = true;
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
/**
 * NextAuth `redirect` callback (TASK-FAN-FE-031).
 *
 * 🔴 Without it, NextAuth's default rewrites every cross-origin URL to `baseUrl`.
 * The header's logout passes the IdP `end_session` URL (`<issuer>/connect/logout`)
 * as `signOut({ redirectTo })`, and the issuer is a different origin from this
 * app (deployed: `auth.hubwang.com` vs `fan.hubwang.com`) — so the browser never
 * reached the IdP, the IdP session survived, and the next «IAM 로그인» signed the
 * user straight back in (measured live, 2026-10-06 UTC, 23rd AMI window). The
 * store does not have this defect because it navigates with `window.location`.
 *
 * Allowed: relative paths and same-origin URLs (the default's behaviour), plus
 * EXACTLY the issuer's `/connect/logout`. Everything else still falls back to
 * `baseUrl` — this is not an open redirect.
 */
export function redirectCallback(
  { url, baseUrl }: { url: string; baseUrl: string },
  issuerUrl: string = env.oidcIssuerUrl,
): string {
  if (url.startsWith('/')) return `${baseUrl}${url}`;
  let target: URL;
  try {
    target = new URL(url);
  } catch {
    return baseUrl;
  }
  if (target.origin === new URL(baseUrl).origin) return url;
  try {
    const issuer = new URL(issuerUrl);
    const logoutPath = `${issuer.pathname.replace(/\/$/, '')}/connect/logout`;
    if (target.origin === issuer.origin && target.pathname === logoutPath) return url;
  } catch {
    /* unparseable issuer → no cross-origin allowance */
  }
  return baseUrl;
}

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
      // TASK-FAN-FE-027 — tells the `/api/auth/session` wrapper this anonymous
      // answer may be a lost refresh race, not a dead session. A boolean only;
      // no token material.
      ...(token[REFRESH_RACE_LOST_CLAIM] === true && { [REFRESH_RACE_LOST_CLAIM]: true }),
    };
  }
  session.accountId = (token.accountId as string | null | undefined) ?? null;
  session.tenantId = (token.tenantId as string | null | undefined) ?? null;
  session.roles = (token.roles as string[] | undefined) ?? [];
  return session;
}

/**
 * The public session the Auth.js `session` action would answer for this
 * decoded JWT — WITHOUT running the `jwt` callback (TASK-FAN-FE-027).
 *
 * It builds the same default object `@auth/core` builds before calling the
 * `session` callback (`lib/actions/session.js`: `{ user: { name, email,
 * image: picture } }`) and then applies {@link sessionCallback}, so the
 * decode-only readers (middleware, `getFanSession`, `isAuthenticated`) judge a
 * cookie with exactly the rules `/api/auth/session` applies — feed the result
 * to `hasAuthenticatedUser` (`session-shape.ts`), the single judge. A token
 * whose refresh failed (`error`) comes back with `user: undefined` → anonymous.
 */
export function publicSessionFromToken(token: JwtToken): Record<string, unknown> {
  return sessionCallback({
    session: { user: { name: token.name, email: token.email, image: token.picture } },
    token,
  });
}
