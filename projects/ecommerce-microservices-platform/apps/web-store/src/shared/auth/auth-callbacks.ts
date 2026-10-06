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

/**
 * TASK-FE-106 — the claim the `jwt` callback sets (and the `session` callback
 * surfaces) when ITS refresh attempt got IAM's rotation-shaped refusal. Read
 * by `session-route.ts` only; never meaningful to client code.
 */
export const REFRESH_RACE_LOST_CLAIM = 'refreshRaceLost';

export interface RefreshedTokens {
  accessToken: string;
  refreshToken: string;
  idToken?: string;
  expiresAt?: number;
}

/**
 * The outcome of one refresh_token grant (TASK-FE-106).
 *
 *   - `ok`               → IAM rotated the pair.
 *   - `rotation_suspect` → IAM `400 invalid_grant`. 🔴 Since iam `TASK-BE-606`
 *     this is ALSO what the LOSER of a same-token concurrent refresh receives
 *     within the 30s grace window ("refused, nothing revoked") — by error shape
 *     alone it cannot be told apart from a genuinely dead refresh token, so the
 *     caller must not conclude "dead" from it before looking again (see
 *     `session-route.ts`). Same classification console-web uses
 *     (`session-refresh.ts` `rotationSuspect`, TASK-PC-FE-300).
 *   - `failed`           → anything else (other 4xx, 5xx, network, malformed body).
 */
export type RefreshGrantResult =
  | { kind: 'ok'; tokens: RefreshedTokens }
  | { kind: 'rotation_suspect' }
  | { kind: 'failed' };

/**
 * The ONE "does this session JWT still hold a usable access token" judge
 * (TASK-FE-106). Used by the `jwt` callback to decide whether to refresh AND by
 * the `/api/auth/session` retry hop to decide whether a concurrent winner's
 * rotated cookie has landed — a second copy of this predicate is how the two
 * would quietly disagree (console-web TASK-PC-FE-300 Failure Scenario 1).
 */
export function hasFreshAccessToken(token: { expiresAt?: unknown }, nowMs: number = Date.now()): boolean {
  const expiresAt = token.expiresAt;
  return typeof expiresAt === 'number' && nowMs < (expiresAt - REFRESH_MARGIN_SECONDS) * 1000;
}

/**
 * Exchange a stored `refresh_token` for a rotated access/refresh pair at the
 * IAM `/oauth2/token` endpoint (RFC 6749 § 6, `client_secret_basic`), and say
 * which kind of failure it was when it fails ({@link RefreshGrantResult}).
 * Server-only — invoked from the `jwt` callback which runs on the server.
 */
export async function refreshTokenGrant(refreshToken: string): Promise<RefreshGrantResult> {
  try {
    const basic = Buffer.from(
      `${WEB_STORE_CLIENT_ID}:${WEB_STORE_CLIENT_SECRET}`,
    ).toString('base64');
    // DEMO-URL-EXEMPT: oidc-issuer — the issuer is NOT derived from the demo IP.
    //   ADR-MONO-069 (C2) pinned a STABLE name (`https://auth.hubwang.com`), and this
    //   string is compared CHARACTER-BY-CHARACTER against the token's `iss` claim ⇒
    //   silently rewriting it breaks login. In the containerised demo the value is
    //   already injected in `DEMO_DOMAIN` form by `demo.env`.
    //   TASK-MONO-623 — the same declaration console-web carries on its four
    //   OIDC legs; this marker is what `scripts/check-fetch-resolution.mjs` reads.
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
        // Rotation: IAM returns a NEW refresh token (reuse-refresh-tokens=false).
        // If a token endpoint ever omits it, fall back to the one we sent so the
        // session does not silently lose its refresh capability.
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
 * Tokens-or-null view of {@link refreshTokenGrant} (kept for existing import
 * sites — `auth.ts` re-exports it). Returns null on ANY failure, including a
 * rotation-race refusal; callers that must tell those apart use
 * {@link refreshTokenGrant}.
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
  // token is held.
  //
  // 🔴 TASK-FE-106 — there is NO in-flight dedupe here. The old comment claimed
  // "NextAuth serializes the jwt callback per session token"; nothing does —
  // not across parallel requests of one browser, not across tabs, and certainly
  // not across serverless instances. Several requests carrying the SAME expired
  // session cookie each send the SAME refresh token; IAM rotates for the first
  // and refuses the rest within its 30s grace window (iam TASK-BE-606/608:
  // `400 invalid_grant`, nothing revoked). A loser cannot learn the winner's
  // rotated pair from here (it lives in the winner's response cookie), so it
  // must neither keep the old refresh token as if nothing happened (re-sending
  // it after 30s IS reuse → family revoke) nor be allowed to overwrite the
  // winner's cookie. It flags the token terminally (same `error` as before, so
  // if this token's cookie is ever written the result is today's logged-out
  // state, never a replay) and marks it `refreshRaceLost` so the
  // `/api/auth/session` wrapper (`session-route.ts`) discards this response —
  // cookie included — and lets the browser ask again with whatever cookie it
  // holds by then.
  if (!account) {
    // The marker is per-call: a token whose cookie escaped with it must not
    // keep re-triggering the retry hop on every later read.
    delete token[REFRESH_RACE_LOST_CLAIM];
    const refreshToken = token.refreshToken as string | undefined;
    if (!hasFreshAccessToken(token) && refreshToken && !token.error) {
      const result = await refreshTokenGrant(refreshToken);
      if (result.kind === 'ok') {
        const refreshed = result.tokens;
        token.accessToken = refreshed.accessToken;
        token.refreshToken = refreshed.refreshToken;
        if (refreshed.idToken) token.idToken = refreshed.idToken;
        if (typeof refreshed.expiresAt === 'number') token.expiresAt = refreshed.expiresAt;
        delete token.error;
      } else {
        // Refresh failed → flag so session()/BFF force a full re-auth (F1).
        token.error = 'RefreshAccessTokenError';
        if (result.kind === 'rotation_suspect') token[REFRESH_RACE_LOST_CLAIM] = true;
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
      // TASK-FE-106 — tells the `/api/auth/session` wrapper this anonymous
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
 * The `signIn` callback body — reject (redirect to the role_denied error) any
 * account lacking the `CUSTOMER` role before a JWT is issued.
 */
export function signInCallback(profile: IamOidcProfile | undefined): true | string {
  if (!hasConsumerRole(profile?.roles)) {
    return '/login?error=account_type_mismatch';
  }
  return true;
}
