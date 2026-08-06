'use client';

import { useEffect, useRef } from 'react';
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
 */
export default function SignupPage() {
  const { signup } = useAuth();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void signup('/');
  }, [signup]);

  // Shown while the redirect is in flight — and it is the whole page if JS never
  // runs, so the button is the fallback rather than decoration.
  return (
    <AuthCardLayout>
      <h1 className="auth-title">회원가입</h1>
      <p style={{ color: 'var(--color-text-secondary)', marginBottom: 'var(--space-6)' }}>
        회원가입은 Global Account 에서 진행합니다. 잠시만 기다려 주세요.
      </p>
      <button
        type="button"
        onClick={() => void signup('/')}
        className="btn btn-primary btn-lg"
        style={{ width: '100%' }}
      >
        Global Account 로 이동
      </button>
    </AuthCardLayout>
  );
}
