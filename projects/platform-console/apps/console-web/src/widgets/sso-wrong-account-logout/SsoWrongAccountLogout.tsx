'use client';

import { useState } from 'react';
import { performLogout } from '@/features/auth';

/**
 * TASK-PC-FE-324 — rendered only on the `/login?error=sso_wrong_account`
 * landing (a same-browser store/consumer-pool SSO session blocked the
 * console's token exchange — see `(auth)/login/page.tsx` ERROR_MESSAGES and
 * `api/auth/callback/route.ts`'s `isConsumerPoolSsoRefusal`).
 *
 * Reuses the SAME RP-initiated logout (`performLogout`, TASK-PC-FE-033) the
 * account-menu's 로그아웃 item already uses — not a new mechanism.
 *
 * -----------------------------------------------------------------------------
 * 🔴🔴 Why this button cannot, by itself, end the session that is blocking login
 * -----------------------------------------------------------------------------
 * This failure happens at the IAM **token endpoint**, before the console has
 * ever received any token for this login attempt — there is no `id_token`
 * cookie here for `/api/auth/logout` to build an `id_token_hint` from.
 * Decompiled `spring-security-oauth2-authorization-server-1.4.1.jar`
 * (2026-10-09, `OidcLogoutAuthenticationProvider.authenticate` bytecode):
 * it unconditionally calls
 * `authorizationService.findByToken(idTokenHint, ID_TOKEN_TOKEN_TYPE)` and,
 * on a null result (which an absent/blank hint always produces), throws
 * `invalid_token` immediately — there is no session-registry fallback for a
 * missing `id_token_hint` in this version. So clicking this button only
 * clears whatever console-local cookies exist (there may be none) and lands
 * back on `/login`; it does NOT end the `auth.hubwang.com` session the store
 * login created (that session's `id_token` lives only in the store tab's own
 * cookie jar, a different origin the console cannot read). The copy next to
 * this button tells the operator to also log out of the store tab — the one
 * place that CAN drive a real `end_session` here.
 */
export function SsoWrongAccountLogout() {
  const [busy, setBusy] = useState(false);

  return (
    <button
      type="button"
      data-testid="sso-wrong-account-logout"
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        await performLogout();
      }}
      className="mt-3 inline-flex w-full items-center justify-center rounded-md border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:opacity-50"
    >
      로그아웃
    </button>
  );
}
