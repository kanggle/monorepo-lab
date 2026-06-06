/**
 * Verification-free read of a single claim from a JWT payload.
 *
 * SAFE here ONLY because the token was just obtained from the trusted GAP
 * `/oauth2/token` endpoint over the completed Authorization-Code exchange —
 * this is **NOT** a signature/security check (the BFF and the federated
 * domains verify the RS256 signature; the console never trusts an unverified
 * claim for authorization). It is used solely to read the operator's home
 * `tenant_id` claim so the console can DEFAULT the active-tenant selection on
 * login (TASK-PC-FE-036). Returns `null` on any malformed input.
 *
 * Runtime: nodejs only (uses `Buffer`); the sole caller is the
 * `/api/auth/callback` route (`runtime = 'nodejs'`).
 */
export function readJwtClaim(token: string, claim: string): unknown {
  return decodeJwtPayload(token)?.[claim] ?? null;
}

/**
 * Verification-free decode of a JWT's full payload object (or `null` for any
 * malformed / null input). Same safety caveat as {@link readJwtClaim}: this is
 * NOT a signature check — used only to surface the operator's own display
 * identity (account menu label + the read-only `/account` page, TASK-PC-FE-041)
 * and the home-tenant default (TASK-PC-FE-036), never for an authorization
 * decision (the BFF + federated domains verify the RS256 signature).
 *
 * Runtime: nodejs only (uses `Buffer`) — server components / `runtime =
 * 'nodejs'` route handlers.
 */
export function decodeJwtPayload(
  token: string | null | undefined,
): Record<string, unknown> | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = Buffer.from(b64, 'base64').toString('utf8');
    const payload = JSON.parse(json) as unknown;
    return payload !== null && typeof payload === 'object'
      ? (payload as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

/**
 * The operator's home tenant from the IAM OIDC access token's `tenant_id`
 * claim — the value the console defaults the active-tenant selection to on
 * login so the tenant-scoped overviews work on first load (TASK-PC-FE-036).
 *
 * Returns `null` when the claim is absent, empty, or the platform-scope
 * sentinel `'*'` (a platform operator has no single home tenant — they MUST
 * explicitly select a customer; the switcher renders an unselected
 * placeholder for them, and the overview/health "select a tenant" gate stands
 * until they pick one).
 */
export function homeTenantFromAccessToken(accessToken: string): string | null {
  const t = readJwtClaim(accessToken, 'tenant_id');
  return typeof t === 'string' && t !== '' && t !== '*' ? t : null;
}
