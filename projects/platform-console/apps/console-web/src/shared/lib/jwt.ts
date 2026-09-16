/**
 * Verification-free read of a single claim from a JWT payload.
 *
 * SAFE here ONLY because the token was just obtained from the trusted GAP
 * `/oauth2/token` endpoint over the completed Authorization-Code exchange —
 * this is **NOT** a signature/security check (the BFF and the federated
 * domains verify the RS256 signature; the console never trusts an unverified
 * claim for authorization). It reads the operator `sub` that keys the
 * remembered tenant selection (`active-tenant-default.ts`, TASK-PC-FE-292).
 * Returns `null` on any malformed input.
 *
 * 🔴 Do not read `tenant_id` from here to choose the active tenant: this
 * client's tokens carry its operational slug (`iam`), not a customer tenant.
 * That was TASK-PC-FE-036's home-tenant default, removed by TASK-PC-FE-292.
 *
 * Runtime: nodejs only (uses `Buffer`).
 */
export function readJwtClaim(token: string, claim: string): unknown {
  return decodeJwtPayload(token)?.[claim] ?? null;
}

/**
 * Verification-free decode of a JWT's full payload object (or `null` for any
 * malformed / null input). Same safety caveat as {@link readJwtClaim}: this is
 * NOT a signature check — used only to surface the operator's own display
 * identity (account menu label + the read-only `/account` page, TASK-PC-FE-041)
 * and the remembered-tenant key (TASK-PC-FE-292), never for an authorization
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
