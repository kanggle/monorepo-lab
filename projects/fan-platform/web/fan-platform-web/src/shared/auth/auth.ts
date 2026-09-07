import NextAuth, { type NextAuthConfig } from 'next-auth';
import { env } from '@/shared/config/env';
import { jwtCallback, sessionCallback } from '@/shared/auth/auth-callbacks';
import { isPublicPath } from '@/shared/auth/public-paths';

/**
 * next-auth v5 (auth.js) configuration — GAP OIDC + PKCE.
 *
 * GAP OIDC seed (`fan-platform-user-flow-client`) lives in the GAP V0010+
 * Flyway migration; until that seed lands the OIDC discovery + token
 * endpoints will reject the client. The frontend still boots / lints / smoke-
 * tests because the OIDC provider config is deferred to first sign-in attempt
 * (NextAuth fetches the discovery doc lazily).
 *
 * - Tokens are stored in HttpOnly cookies (next-auth session strategy `jwt`).
 * - `tenant_id` claim from GAP is propagated onto the session for downstream
 *   API calls (community-api / artist-api will assert `tenant_id=fan-platform`
 *   server-side regardless).
 * - `authorization_code` + PKCE is forced (`checks: ['pkce', 'state']`).
 * - Phase 4.5 F3: the `jwt` callback performs proactive silent refresh via
 *   `auth-callbacks.ts`; on failure the session degrades to anonymous and
 *   middleware bounces the user to `/login?from=…` (F6 preserved).
 *
 * See:
 *   - projects/iam-platform/specs/features/consumer-integration-guide.md
 *   - projects/fan-platform/specs/integration/iam-integration.md
 *   - .claude/skills/frontend/auth-client/SKILL.md
 */

interface IamOidcProfile {
  sub: string;
  email?: string;
  name?: string;
  preferred_username?: string;
  tenant_id?: string;
  account_id?: string;
  roles?: string[];
}

export const authConfig: NextAuthConfig = {
  // `trustHost` defaults to true under Vercel; we are self-hosted so set it
  // explicitly. Honors `AUTH_TRUST_HOST=true` for non-Vercel deployments.
  trustHost: true,
  session: { strategy: 'jwt' },
  // Fail-fast surface for missing NEXTAUTH_SECRET in dev — surfaces the
  // "FATAL: missing NEXTAUTH_SECRET" message documented in the README.
  secret: process.env.NEXTAUTH_SECRET,
  pages: {
    signIn: '/login',
    error: '/login',
  },
  providers: [
    {
      id: 'iam',
      name: 'IAM',
      type: 'oidc',
      issuer: env.oidcIssuerUrl,
      clientId: env.oidcClientId,
      clientSecret: env.oidcClientSecret,
      authorization: {
        params: {
          scope:
            'openid profile email offline_access fan-platform.community.read fan-platform.community.write fan-platform.artist.read',
        },
      },
      // PKCE + state — required by GAP for `authorization_code` (consumer-
      // integration-guide § Phase 2).
      checks: ['pkce', 'state'],
      profile(profile: IamOidcProfile) {
        return {
          id: profile.sub,
          accountId: profile.account_id ?? profile.sub,
          email: profile.email ?? null,
          name: profile.name ?? profile.preferred_username ?? null,
          tenantId: profile.tenant_id ?? null,
          roles: profile.roles ?? [],
        };
      },
    },
  ],
  callbacks: {
    /**
     * Persist access_token + refresh_token + tenant_id onto the JWT session
     * cookie and perform proactive silent refresh (Phase 4.5 F3).
     * The JWT itself is HttpOnly so client JS never sees the tokens.
     * Logic is extracted to `auth-callbacks.ts` for unit-testability.
     */
    jwt: jwtCallback,
    /**
     * Expose tenant_id / account_id / roles to RSC pages and server actions.
     * `accessToken` deliberately stays only on the JWT (server-side) — a
     * dedicated `getAccessToken()` helper reads it via `auth()` so that no
     * client component ever receives the bearer token (F2).
     * Degrades to anonymous when a silent refresh has failed (F3 fallback).
     */
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    session: sessionCallback as any,
    /**
     * 🔴 공개 경로 목록을 여기 **다시 적지 않는다.** 예전에는 이 콜백과 `middleware.ts` 가
     * 각자 접두사 목록을 들고 있었고, 그 둘은 이미 갈라져 있었다(`/favicon` vs
     * `/favicon.ico`). 공개 브라우징이 늘어나는 목록에서 그 드리프트는 «한쪽만 열린다» 로
     * 나타나고, 그 증상은 조용하다. 두 소비자가 `isPublicPath` 하나를 부른다.
     *
     * 🔵 `Boolean(auth)` 는 여기서는 그대로 둔다 — next-auth 가 이 콜백에 넘기는 `auth` 는
     * `session` 콜백을 이미 통과한 값이라 `middleware.ts` 가 다루는 «설정 오류 본문» 이
     * 올 수 있는 자리가 아니다. 실제 리다이렉트 판정은 미들웨어가 하고, 그쪽은
     * `hasAuthenticatedUser` 를 쓴다.
     */
    authorized({ auth, request }) {
      if (isPublicPath(request.nextUrl.pathname)) return true;
      return Boolean(auth);
    },
  },
};

export const { handlers, auth, signIn, signOut } = NextAuth(authConfig);
