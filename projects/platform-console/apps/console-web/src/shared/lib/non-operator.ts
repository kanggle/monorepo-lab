/**
 * TASK-MONO-773 S6 — the non-operator shell's path rules (console-integration-contract § 2.6.4
 * «Path allow-list (non-operator state)»).
 *
 * One module so the `(console)` guard, the callback and the idle-refresh hop read the SAME list — a copy in
 * one of them is how one side quietly opens (or closes) a page the others do not.
 *
 * Exactly two pages are rendered to a non-operator: the tenant-creation screen and the global guide. Matching
 * is **path equality** (a query string is allowed — `/new-tenant?step=1` is `/new-tenant`); `/guide/x`,
 * `/new-tenant-x` and `/` are NOT on the list. Everything else lands on {@link NON_OPERATOR_LANDING}, so no
 * operator screen is ever rendered to call its operator API (773 AC-0 § 3: each direct URL used to bounce
 * through `401 → /login?error=session_expired → silent SSO → back`).
 */

/** The non-operator landing — the one «테넌트 생성» screen. */
export const NON_OPERATOR_LANDING = '/new-tenant';

/** The pages a non-operator may see (path equality). */
export const NON_OPERATOR_ALLOWED_PATHS: readonly string[] = [NON_OPERATOR_LANDING, '/guide'];

/** The path part of `pathname[?search]` (no query, no fragment). */
function pathOf(raw: string): string {
  const cut = raw.search(/[?#]/);
  return cut === -1 ? raw : raw.slice(0, cut);
}

/** Is `raw` (`pathname[?search]`) a page the non-operator shell renders? */
export function isNonOperatorAllowedPath(raw: string | null | undefined): boolean {
  if (!raw) return false;
  return NON_OPERATOR_ALLOWED_PATHS.includes(pathOf(raw));
}

/**
 * Where a non-operator goes when they asked for `raw`: `raw` itself when it is on the allow-list, otherwise
 * {@link NON_OPERATOR_LANDING}. Callers pass an already-sanitised same-site path (the callback's state cookie
 * value, the refresh route's `resolveRefreshReturnPath` result, the guard's trusted `x-pathname`).
 */
export function nonOperatorLandingFor(raw: string | null | undefined): string {
  return raw && isNonOperatorAllowedPath(raw) ? raw : NON_OPERATOR_LANDING;
}
