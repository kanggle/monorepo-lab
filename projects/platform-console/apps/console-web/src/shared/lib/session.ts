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
/** Domain-facing **assumed** GAP OIDC access token (ADR-MONO-020 D4 / § 2.7).
 *  Minted server-side by the assume-tenant RFC 8693 exchange
 *  (`assume-tenant-exchange.ts`) when the operator switches to a customer
 *  tenant: it carries `tenant_id=<selected>` + `entitled_domains=<selected's
 *  ACTIVE subs>` (re-scoped signed claims — NOT just X-Tenant-Id). It is the
 *  **domain-facing bearer** for the non-GAP domain reads + the cross-domain
 *  overview proxy (see {@link getDomainFacingToken}). Scoped to the CURRENT
 *  {@link TENANT_COOKIE} BY CONSTRUCTION — the `/api/tenant` switch sets BOTH
 *  atomically and clearing the tenant clears this cookie. The grant issues no
 *  refresh token (D2); it is re-assumed on every GAP refresh. Never an
 *  `/api/admin/**` credential (the GAP operator-token boundary § 2.6 is
 *  unchanged). */
export const ASSUMED_TOKEN_COOKIE = 'console_assumed_token';
/** Short-lived PKCE/state cookies used only between /login and /callback. */
export const PKCE_VERIFIER_COOKIE = 'console_pkce_verifier';
export const OAUTH_STATE_COOKIE = 'console_oauth_state';

/**
 * Token / session cookies (access / refresh / operator-token / active-tenant).
 *
 * <p>TASK-BE-311 — uses `SameSite=Lax`. The OAuth callback (`/api/auth/callback`)
 * sets these cookies via Set-Cookie + 302 to the post-login path; the
 * subsequent navigation chain (`/ → /dashboards`) inherits its
 * cross-site initiator from the SAS issuer (auth-service:8081 → callback),
 * so `SameSite=Strict` cookies are NOT sent on that first post-callback
 * page load and the `(console)` layout guard reads `isAuthenticated() ==
 * false`, redirecting to `/login`. `Lax` permits top-level GET
 * navigations (the only context these cookies need to traverse) while
 * still blocking cross-site iframe / fetch / POST contexts where CSRF
 * concerns apply. HttpOnly + Secure already prevent JavaScript and
 * insecure-transport attacks; CSRF for state-changing operations is
 * separately handled via dedicated tokens.
 */
export const tokenCookieOpts = {
  httpOnly: true,
  secure: true,
  sameSite: 'lax' as const,
  path: '/',
};

/**
 * PKCE/state cookies: HttpOnly + Secure + Lax, short maxAge (auth flow window
 * only). Inherits `sameSite: 'lax'` from {@link tokenCookieOpts}; both the
 * transient cookies (read by `/api/auth/callback` after the cross-origin
 * SAS redirect) and the session cookies (read by `(console)` layout after
 * the post-callback redirect chain) need Lax to survive top-level
 * cross-site GET navigations. See `tokenCookieOpts` JSDoc.
 */
export const transientCookieOpts = {
  ...tokenCookieOpts,
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
 * Server-side read of the **assumed** (tenant-scoped) domain-facing token, or
 * null when no active-tenant assumption exists (ADR-MONO-020 D4 / § 2.7).
 *
 * Present ⇔ the operator has switched to a customer tenant via `/api/tenant`
 * (the switch mints it via the assume-tenant exchange and sets it atomically
 * with {@link TENANT_COOKIE}). Absent for a non-switched / single-tenant
 * operator (net-zero — see {@link getDomainFacingToken}). Scoped to the
 * current active tenant BY CONSTRUCTION (never served for a tenant ≠ the
 * active-tenant cookie — both are set/cleared together).
 */
export async function getAssumedToken(): Promise<string | null> {
  const jar = await cookies();
  return jar.get(ASSUMED_TOKEN_COOKIE)?.value ?? null;
}

/**
 * The **domain-facing** GAP-OIDC bearer for every tenant-scoped domain read
 * (the cross-domain overview proxy + the 4 non-GAP domain section clients
 * wms/scm/finance/erp). ADR-MONO-020 D4 / § 2.7 — the central resolver:
 *
 *   - the **assumed** token if an active-tenant assumption exists (the
 *     operator switched to a customer → the signed `tenant_id` +
 *     `entitled_domains` are re-scoped to that customer, so the domain
 *     entitlement gates follow the selection);
 *   - else the base {@link getAccessToken} (a non-switched / single-tenant
 *     operator keeps using the login token EXACTLY as before — **net-zero**;
 *     the existing per-domain credential rule § 2.4.5 is unchanged, only WHICH
 *     GAP OIDC token).
 *
 * This is NOT a GAP `/api/admin/**` credential (that boundary uses
 * {@link getOperatorToken}, § 2.6, unchanged). The GAP-domain clients
 * (accounts/audit/operators/dashboards) MUST NOT use this resolver.
 */
export async function getDomainFacingToken(): Promise<string | null> {
  return (await getAssumedToken()) ?? (await getAccessToken());
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
