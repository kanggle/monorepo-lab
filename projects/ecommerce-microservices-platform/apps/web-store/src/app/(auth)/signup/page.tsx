import { redirect } from 'next/navigation';

/**
 * After TASK-FE-067, web-store no longer hosts a self-service signup form —
 * IAM's authorize endpoint handles new-account creation as part of the OIDC
 * flow. We redirect into `signIn('iam')` so the user lands on IAM's signup
 * page (when IAM's authorize URL is told to prefer registration via the
 * `prompt=create` hint, when IAM supports it) or on the login page where
 * they can switch to "create account" themselves.
 *
 * Keeping the route alive (rather than 404'ing) preserves the legacy
 * "/signup" link from old emails / SEO. NextAuth's `/api/auth/signin/iam`
 * URL forwards directly to the IAM authorize URL with PKCE state set.
 * (The provider id is `iam` — see auth.ts; the earlier `gap` slug was renamed
 * in TASK-MONO-180 but this redirect string was missed until now.)
 */
export default function SignupPage() {
  redirect('/api/auth/signin/iam?callbackUrl=%2F');
}
