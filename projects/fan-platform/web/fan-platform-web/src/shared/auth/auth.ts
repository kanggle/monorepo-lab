import NextAuth, { type NextAuthConfig } from 'next-auth';
import { env } from '@/shared/config/env';

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
 *
 * See:
 *   - projects/global-account-platform/specs/features/consumer-integration-guide.md
 *   - projects/fan-platform/specs/integration/gap-integration.md
 *   - .claude/skills/frontend/auth-client/SKILL.md
 */

interface GapOidcProfile {
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
      id: 'gap',
      name: 'Global Account Platform',
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
      profile(profile: GapOidcProfile) {
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
     * cookie. The JWT itself is HttpOnly so client JS never sees the tokens.
     */
    async jwt({ token, account, profile, user }) {
      if (account) {
        token.accessToken = account.access_token;
        token.refreshToken = account.refresh_token;
        token.expiresAt = account.expires_at;
        // Keep the id_token server-side as the RP-initiated-logout id_token_hint
        // (GAP end_session). Never surfaced to the public session.
        token.idToken = account.id_token;
      }
      if (profile) {
        const p = profile as GapOidcProfile;
        token.tenantId = p.tenant_id ?? token.tenantId;
        token.accountId = p.account_id ?? token.accountId;
        token.roles = p.roles ?? token.roles;
      }
      if (user && 'accountId' in user) {
        const u = user as { accountId?: string; tenantId?: string | null; roles?: string[] };
        token.accountId = u.accountId ?? token.accountId;
        token.tenantId = u.tenantId ?? token.tenantId;
        token.roles = u.roles ?? token.roles;
      }
      return token;
    },
    /**
     * Expose tenant_id / account_id / roles to RSC pages and server actions.
     * `accessToken` deliberately stays only on the JWT (server-side) — a
     * dedicated `getAccessToken()` helper reads it via `auth()` so that no
     * client component ever receives the bearer token.
     */
    async session({ session, token }) {
      session.tenantId = (token.tenantId as string | null | undefined) ?? null;
      session.accountId = (token.accountId as string | null | undefined) ?? null;
      session.roles = (token.roles as string[] | undefined) ?? [];
      return session;
    },
    authorized({ auth, request }) {
      const { pathname } = request.nextUrl;
      const isProtected =
        !pathname.startsWith('/login') &&
        !pathname.startsWith('/api/auth') &&
        !pathname.startsWith('/_next') &&
        !pathname.startsWith('/favicon');
      if (!isProtected) return true;
      return Boolean(auth);
    },
  },
};

export const { handlers, auth, signIn, signOut } = NextAuth(authConfig);
