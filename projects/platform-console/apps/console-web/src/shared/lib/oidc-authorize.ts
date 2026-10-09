import type { cookies } from 'next/headers';
import {
  generateCodeVerifier,
  deriveCodeChallenge,
  generateState,
} from '@/shared/lib/pkce';
import {
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
  STEP_UP_MARKER_COOKIE,
  transientCookieOpts,
} from '@/shared/lib/session';

/**
 * The ONE place an IAM `/oauth2/authorize` request is started — shared by
 * `GET /api/auth/login` and `GET /api/auth/step-up` (TASK-MONO-771).
 *
 * 🔴 Why one function: contract § 2.6 says the step-up starts «a fresh IAM
 * authorization **exactly as login does** (PKCE, `state`) with `acr_values=mfa`
 * added». Two copies of the PKCE/state wiring is how one of them silently
 * drifts (a forgotten `code_challenge_method`, a state cookie the callback no
 * longer parses). The two routes differ only in the `stepUp` flag here and in
 * what they do with the session first (login clears it — TASK-PC-FE-278;
 * step-up keeps it — § 2.6 «the IAM cookies are kept»).
 *
 * Cookie effects:
 *   - PKCE verifier + `state|<postLoginPath>` (transient, HttpOnly) — as before;
 *   - `stepUp` → {@link STEP_UP_MARKER_COOKIE} = this request's `state` (the
 *     server-side, `state`-bound mark the callback's loop bound reads);
 *   - not `stepUp` → that marker is deleted, so a login started after an
 *     abandoned step-up is never mistaken for a stepped-up one. (The callback
 *     compares the marker to the `state` anyway — this keeps the jar honest.)
 */

type CookieStore = Awaited<ReturnType<typeof cookies>>;

export interface AuthorizeEnv {
  OIDC_ISSUER_URL: string;
  OIDC_CLIENT_ID: string;
  OIDC_REDIRECT_URI: string;
  OIDC_SCOPE: string;
}

/** The `acr_values` token IAM interprets as a step-up request (auth-api.md § 단계 상승). */
export const STEP_UP_ACR_VALUES = 'mfa';

export async function startAuthorization(
  jar: CookieStore,
  env: AuthorizeEnv,
  postLoginPath: string,
  opts: { stepUp: boolean },
): Promise<string> {
  const verifier = generateCodeVerifier();
  const challenge = await deriveCodeChallenge(verifier);
  const state = generateState();

  const authorizeUrl = new URL(`${env.OIDC_ISSUER_URL}/oauth2/authorize`);
  authorizeUrl.searchParams.set('response_type', 'code');
  authorizeUrl.searchParams.set('client_id', env.OIDC_CLIENT_ID);
  authorizeUrl.searchParams.set('redirect_uri', env.OIDC_REDIRECT_URI);
  authorizeUrl.searchParams.set('scope', env.OIDC_SCOPE);
  authorizeUrl.searchParams.set('code_challenge', challenge);
  authorizeUrl.searchParams.set('code_challenge_method', 'S256');
  authorizeUrl.searchParams.set('state', state);
  if (opts.stepUp) authorizeUrl.searchParams.set('acr_values', STEP_UP_ACR_VALUES);

  jar.set(PKCE_VERIFIER_COOKIE, verifier, transientCookieOpts);
  // state cookie carries both the CSRF token and the post-login target.
  jar.set(OAUTH_STATE_COOKIE, `${state}|${postLoginPath}`, transientCookieOpts);
  if (opts.stepUp) {
    jar.set(STEP_UP_MARKER_COOKIE, state, transientCookieOpts);
  } else {
    jar.delete(STEP_UP_MARKER_COOKIE);
  }

  return authorizeUrl.toString();
}
