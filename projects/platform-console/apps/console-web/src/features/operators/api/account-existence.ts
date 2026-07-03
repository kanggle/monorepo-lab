/**
 * Operator-create pre-flight: "does this email have a signed-up account in the
 * target tenant?" — the client-side proxy check behind the dangling-operator
 * warning (TASK-PC-FE-179).
 *
 * Why account-existence (not credential-existence): the console CANNOT reach
 * the exact credential probe (`POST /internal/auth/credentials/account-id-by-
 * email` is `/internal/**`, admin-service-only). But signup provisions the
 * account row and the `auth_db.credentials` row together, so an account's
 * presence in the tenant is a reliable proxy for "this email can log in via
 * the unified IAM credential" (ADR-MONO-035 O2). We call the SAME same-origin
 * accounts search BFF the /accounts screen uses — no browser-direct IAM call
 * (architecture.md § Forbidden Dependencies).
 *
 * Fail-soft (ADR-MONO-035 O6 — operator login availability / creation
 * resilience preserved): ANY failure — 401/403 (caller lacks accounts-read),
 * 503/timeout (accounts degraded), non-OK, or a shape we can't parse —
 * resolves to `null` ("unavailable / unknown"), and the caller then shows NO
 * warning and NEVER blocks the create. Only a definitive empty result yields
 * `false`.
 *
 * @returns `true`  — an account with this email exists in the tenant
 *          `false` — no such account (definitive empty page)
 *          `null`  — could not determine (skip the warning; fail-soft)
 */
export async function checkAccountExistsForTenant(
  email: string,
  tenantId: string,
  signal?: AbortSignal,
): Promise<boolean | null> {
  const e = email.trim();
  const t = tenantId.trim();
  if (e === '' || t === '' || t === '*') return null;

  const qs = new URLSearchParams({ email: e, tenantId: t, size: '1' });
  try {
    const res = await fetch(`/api/accounts?${qs.toString()}`, {
      method: 'GET',
      headers: { accept: 'application/json' },
      signal,
    });
    if (!res.ok) return null; // 401/403/400/503 → unknown, skip warning
    const json: unknown = await res.json();
    // The accounts page envelope: { content: [...], totalElements, ... }.
    // Treat any non-empty match as "exists"; only a well-formed empty page is
    // a definitive `false`.
    if (json && typeof json === 'object') {
      const page = json as { content?: unknown; totalElements?: unknown };
      if (Array.isArray(page.content)) {
        if (page.content.length > 0) return true;
        if (typeof page.totalElements === 'number') {
          return page.totalElements > 0;
        }
        return false; // empty content, no count → treat as absent
      }
    }
    return null; // unrecognized shape → unknown
  } catch {
    // AbortError (superseded/unmounted) and network errors alike → unknown.
    return null;
  }
}
