import { redirect } from 'next/navigation';

/**
 * After TASK-FE-067, web-store no longer hosts a self-service signup form —
 * GAP's authorize endpoint handles new-account creation as part of the OIDC
 * flow. We redirect into `signIn('iam')` so the user lands on GAP's signup
 * page (when GAP's authorize URL is told to prefer registration via the
 * `prompt=create` hint, when GAP supports it) or on the login page where
 * they can switch to "create account" themselves.
 *
 * Keeping the route alive (rather than 404'ing) preserves the legacy
 * "/signup" link from old emails / SEO. NextAuth's `/api/auth/signin/gap`
 * URL forwards directly to the GAP authorize URL with PKCE state set.
 */
export default function SignupPage() {
  redirect('/api/auth/signin/gap?callbackUrl=%2F');
}
