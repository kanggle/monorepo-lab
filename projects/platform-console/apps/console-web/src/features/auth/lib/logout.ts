/**
 * RP-initiated OIDC logout (TASK-PC-FE-033; extracted from the former
 * `LogoutButton` by TASK-PC-FE-041 so both a button and the account-menu item
 * share one code path — behaviour verbatim).
 *
 * POSTs to the `/api/auth/logout` server route (IAM revoke + clear ALL session
 * cookies), then hard-navigates to the URL it returns. That URL is the OIDC
 * end_session endpoint (`/connect/logout`) so the IdP terminates its OWN
 * session — without it the next login silently re-authenticates with no
 * credential form. Falls back to `/login` if the route returns no `logoutUrl`.
 * No client-side token handling. Cookie clearing server-side is the source of
 * truth for "logged out" even if the IAM revoke fails.
 */
export async function performLogout(): Promise<void> {
  let logoutUrl = '/login';
  try {
    const res = await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
    });
    const body = (await res.json().catch(() => ({}))) as {
      logoutUrl?: string;
    };
    if (body.logoutUrl) logoutUrl = body.logoutUrl;
  } finally {
    window.location.assign(logoutUrl);
  }
}
