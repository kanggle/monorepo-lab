'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { AuthCardLayout, useAuth } from '@/features/auth';

/**
 * web-store hosts no signup form of its own — IAM owns identity (ADR-MONO-040)
 * and new accounts are created on IAM's pages inside the OIDC flow. This route
 * exists so the legacy `/signup` link (old emails, SEO, the login page footer)
 * starts that flow instead of 404'ing.
 *
 * <h2>Why not just link to a URL (TASK-FE-097)</h2>
 *
 * It used to `redirect('/api/auth/signin/iam')`, which was next-auth v4's way to
 * jump straight at a provider. This app is on v5, which does not serve that
 * action — every click landed on `/login?error=Configuration`, with
 * `UnknownAction` in the server log and error copy that blamed the auth server
 * for what was a routing mistake on our side. The comment that used to sit here,
 * claiming that URL "forwards directly to the IAM authorize URL", was true under
 * v4 and did not travel with the upgrade.
 *
 * Linking straight at IAM's `/signup` is also wrong, and not cosmetically:
 * `SavedRequestTenantResolver` derives the new account's tenant from the saved
 * `/oauth2/authorize` request and deliberately trusts nothing else (a `client_id`
 * on any other saved URL is ignored by design). Arrive without having gone
 * through authorize and the account is born in `fan-platform`, which the
 * ecommerce edge will not admit.
 *
 * So this goes through the same `signIn('iam')` the login button uses — one
 * mechanism, so the two cannot drift — which reaches IAM carrying the saved
 * authorize request.
 *
 * <h2>Landing on the form, not one click from it (TASK-BE-578)</h2>
 *
 * This used to reach IAM's *login* page, where the user had to press "회원가입"
 * a second time; the comment here said skipping that click needed IAM to change,
 * and BE-578 is that change. `signup()` sends the OIDC standard `prompt=create`
 * on the authorize request and IAM's entry point routes it to its signup form.
 * The authorize request itself is otherwise identical, which is what keeps the
 * new account in the `ecommerce` tenant.
 *
 * <h2>One flow per page, not one per press (TASK-FE-108)</h2>
 *
 * The button used to stay live while the automatic redirect was in flight
 * (about a second). Pressing it started a **second** `signIn`: that call
 * rewrote the store's `state` and PKCE cookies, but the browser was already on
 * its way to IAM with the **first** authorize request, which is the one IAM
 * saved and later resumed. The callback then carried the first `state` against
 * a cookie holding the second, and Auth.js refused it — `CallbackRouteError` →
 * `unexpected "state" response parameter value` — shown to the user as
 * «인증 서버 설정에 문제가 있습니다» (measured live, 2026-10-11 UTC). A retry from
 * `/login` worked because it was a single press. It is a race, so it fails only
 * sometimes, which is why it looked like «the first login after signup».
 *
 * So the automatic start and the button share one guard, the button is
 * disabled once the flow has started, and only a failed start re-arms it. A
 * back-navigation that restores this page from the back/forward cache re-arms
 * it too, or the button would stay «이동 중...» on a page that is not moving.
 */
export default function SignupPage() {
  const { signup } = useAuth();
  const started = useRef(false);
  const [starting, setStarting] = useState(false);

  const start = useCallback(() => {
    if (started.current) return;
    started.current = true;
    setStarting(true);
    Promise.resolve(signup('/')).catch(() => {
      started.current = false;
      setStarting(false);
    });
  }, [signup]);

  useEffect(() => {
    start();
  }, [start]);

  useEffect(() => {
    const onPageShow = (e: PageTransitionEvent) => {
      if (!e.persisted) return;
      started.current = false;
      setStarting(false);
    };
    window.addEventListener('pageshow', onPageShow);
    return () => window.removeEventListener('pageshow', onPageShow);
  }, []);

  // Shown while the redirect is in flight — and it is the whole page if JS never
  // runs, so the button is the fallback rather than decoration.
  return (
    <AuthCardLayout>
      <h1 className="auth-title">회원가입</h1>
      <p style={{ color: 'var(--color-text-secondary)', marginBottom: 'var(--space-6)' }}>
        회원가입은 IAM 에서 진행합니다. 잠시만 기다려 주세요.
      </p>
      <button
        type="button"
        onClick={start}
        className="btn btn-primary btn-lg"
        style={{ width: '100%' }}
        disabled={starting}
      >
        {starting ? '이동 중...' : 'IAM 으로 이동'}
      </button>
    </AuthCardLayout>
  );
}
