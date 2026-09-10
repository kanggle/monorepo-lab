import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv } from '@/shared/config/env';
import {
  generateCodeVerifier,
  deriveCodeChallenge,
  generateState,
} from '@/shared/lib/pkce';
import {
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
  transientCookieOpts,
  clearFullSession,
} from '@/shared/lib/session';
import { sanitizeReturnPath } from '@/shared/lib/return-path';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * IAM OIDC Authorization Code + PKCE — step 1 (login initiation).
 *
 * Public client `platform-console-web` (no client secret — auth-service
 * V0015 seed, ADR-003 public-client lineage). Generates a PKCE
 * verifier/challenge + anti-CSRF state server-side, stores the verifier+state
 * in short-lived HttpOnly cookies (never client JS — frontend-app.md
 * § Authentication), and 302-redirects the browser to GAP
 * `${OIDC_ISSUER_URL}/oauth2/authorize` (auth-api.md § GET /oauth2/authorize).
 *
 * `redirect` query param (post-login target) is sanitised to a same-site
 * relative path and round-tripped through the OAuth `state` cookie.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const env = getServerEnv();
  const { searchParams } = new URL(req.url);

  // Same-site sanitise via the shared predicate the login page also uses —
  // the single source of "is this redirect safe?" (open-redirect guard,
  // PC-FE-253).
  const postLoginPath = sanitizeReturnPath(searchParams.get('redirect'));

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

  const jar = await cookies();

  // 🔴🔴 TASK-PC-FE-278 — 재로그인을 **시작하는 순간** 옛 세션을 버린다.
  //
  // 계약이 스무 곳에서 요구하는 *"forced whole-session re-login — no partial authed
  // state"* 의 「clear」 가 실제로 일어나는 유일한 자리다. 401 을 만난 서버 컴포넌트는
  // 쿠키를 **못 지운다**(Next 는 Route Handler / Server Action 에서만 쿠키 변경을
  // 허용한다) — 그래서 그 지점들은 마커만 붙여 여기로 보내고, 지우는 일은 여기가 한다.
  //
  // 🔵 마커 유무와 무관하게 지운다. 운영자가 스스로 다시 로그인하는 경우에도 옛 토큰이
  //    남아 있을 이유가 없고, 「마커가 있을 때만」 로 좁히면 그 조건이 틀렸을 때 **다시
  //    조용히** 반쪽 세션이 살아남는다 — 이 티켓이 고치는 결함이 정확히 그 모양이었다.
  //
  // 🔴 순서 주의: PKCE/state 쿠키를 **세운 뒤에** 부르면 방금 만든 것을 지운다.
  //    `clearFullSession` 은 세션 쿠키만 건드리지만, 그 사실에 기대지 말고 여기서 끝낸다.
  clearFullSession(jar);

  jar.set(PKCE_VERIFIER_COOKIE, verifier, transientCookieOpts);
  // state cookie carries both the CSRF token and the post-login target.
  jar.set(
    OAUTH_STATE_COOKIE,
    `${state}|${postLoginPath}`,
    transientCookieOpts,
  );

  logger.info('oidc_login_initiated', { requestId });
  return NextResponse.redirect(authorizeUrl.toString());
}
