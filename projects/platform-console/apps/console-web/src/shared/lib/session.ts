import { cookies } from 'next/headers';

/**
 * HttpOnly cookie session contract (single source of cookie names + options).
 *
 * Tokens live ONLY in these HttpOnly Secure SameSite=Strict cookies — never
 * localStorage / sessionStorage (frontend-app.md § Authentication forbidden
 * pattern; architecture.md § Forbidden Dependencies). Client JavaScript can
 * never read the access/refresh token.
 */

/** GAP OIDC `platform-console-web` access token. NEVER an `/api/admin/**`
 *  credential — it is only the `subject_token` input to the operator-token
 *  exchange (console-integration-contract § 2.1 trust-boundary invariant /
 *  ADR-MONO-014). */
export const ACCESS_COOKIE = 'console_access_token';
export const REFRESH_COOKIE = 'console_refresh_token';
/** admin-service operator token (`token_type=admin`, `iss=admin-service`)
 *  obtained server-side via the RFC 8693 exchange (§ 2.6). This — and only
 *  this — is the credential for every `/api/admin/**` call. Re-exchanged on
 *  every GAP refresh (no operator-refresh state — ADR-MONO-014 D2). */
export const OPERATOR_COOKIE = 'console_operator_token';
/** Active tenant for tenant-scoped server-side domain calls (multi-tenant). */
export const TENANT_COOKIE = 'console_active_tenant';
/** Short-lived PKCE/state cookies used only between /login and /callback. */
export const PKCE_VERIFIER_COOKIE = 'console_pkce_verifier';
export const OAUTH_STATE_COOKIE = 'console_oauth_state';

export const tokenCookieOpts = {
  httpOnly: true,
  secure: true,
  sameSite: 'strict' as const,
  path: '/',
};

/**
 * PKCE/state cookies: HttpOnly, short maxAge (auth flow window only).
 *
 * <p>TASK-BE-311 — must use `SameSite=Lax`, NOT `Strict`. The OAuth callback
 * URL (`/api/auth/callback`) is reached via a top-level cross-origin redirect
 * from the SAS issuer (`http://auth-service:8081/...`); `SameSite=Strict`
 * cookies are NOT sent on top-level navigations originating from another
 * site, so the callback handler reads `undefined` for both
 * {@link PKCE_VERIFIER_COOKIE} and {@link OAUTH_STATE_COOKIE} and bails
 * with `invalid_state`. `Lax` is the OAuth/OIDC-recommended default: it
 * permits cookies on top-level GET navigations (which OAuth callbacks
 * always are) while still blocking CSRF-style cross-site POSTs. Session
 * cookies (`tokenCookieOpts` above) stay `Strict` — they aren't expected
 * to survive cross-origin redirects.
 */
export const transientCookieOpts = {
  ...tokenCookieOpts,
  sameSite: 'lax' as const,
  maxAge: 600, // 10 min — bounded login round-trip
};

/**
 * Server-side read of the GAP OIDC access token (or null).
 *
 * SCOPE: this token is exclusively the `subject_token` input to the
 * operator-token exchange (`operator-token-exchange.ts`) and the OAuth
 * refresh/revoke flows. It MUST NOT be used as an `/api/admin/**` bearer —
 * use {@link getOperatorToken} for that (console-integration-contract § 2.1
 * trust-boundary invariant; this is the #569 defect being closed).
 */
export async function getAccessToken(): Promise<string | null> {
  const jar = await cookies();
  return jar.get(ACCESS_COOKIE)?.value ?? null;
}

/**
 * Server-side read of the exchanged admin-service operator token (or null).
 * This is the ONLY credential valid for `/api/admin/**`
 * (console-integration-contract § 2.2 / § 2.6).
 */
export async function getOperatorToken(): Promise<string | null> {
  const jar = await cookies();
  return jar.get(OPERATOR_COOKIE)?.value ?? null;
}

/** Server-side read of the active tenant (or null when none selected). */
export async function getActiveTenant(): Promise<string | null> {
  const jar = await cookies();
  return jar.get(TENANT_COOKIE)?.value ?? null;
}

/**
 * A usable operator session requires BOTH the GAP OIDC session (so refresh
 * can re-exchange — ADR-MONO-014 D2) AND a present operator token (the
 * `/api/admin/**` credential). A GAP-token-only state is NOT authenticated
 * for operator actions: the exchange having failed must never leave a
 * half-working authed state (console-integration-contract § 2.6 fail-closed;
 * task Failure Scenario "Silent partial authed state").
 */
export async function isAuthenticated(): Promise<boolean> {
  return (await getAccessToken()) !== null && (await getOperatorToken()) !== null;
}
