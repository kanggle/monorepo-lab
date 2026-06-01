import NextAuth, { type NextAuthConfig } from 'next-auth';

/**
 * NextAuth v5 (auth.js) configuration — GAP OIDC + PKCE for admin-dashboard
 * (operator).
 *
 * GAP V0012 seed registers:
 *   - client_id:     `ecommerce-admin-dashboard-client`
 *   - client_secret: `ecommerce-dev` (plain, dev-only)
 *   - redirect_uris: http://localhost:3001/api/auth/callback/gap,
 *                    http://admin.ecommerce.local/api/auth/callback/gap
 *   - scopes:        openid profile email tenant.read ecommerce.operator
 *
 * admin-dashboard is the internal operations console. The `account_type`
 * claim from GAP must be `OPERATOR`; a `CONSUMER` who completes GAP login is
 * rejected and bounced back to `/login?error=account_type_mismatch`.
 *
 * See:
 *   - projects/global-account-platform/specs/features/consumer-integration-guide.md § Phase 4
 *   - projects/ecommerce-microservices-platform/specs/integration/gap-integration.md (TASK-MONO-027)
 *   - projects/fan-platform/web/fan-platform-web/src/shared/auth/auth.ts (reference pattern)
 */

interface GapOidcProfile {
  sub: string;
  email?: string;
  name?: string;
  preferred_username?: string;
  tenant_id?: string;
  account_id?: string;
  account_type?: 'CONSUMER' | 'OPERATOR' | string;
  roles?: string[];
}

const ALLOWED_ACCOUNT_TYPE = 'OPERATOR';

export const authConfig: NextAuthConfig = {
  trustHost: true,
  session: { strategy: 'jwt' },
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
      issuer: process.env.OIDC_ISSUER_URL ?? 'http://gap.local',
      clientId: process.env.ECOMMERCE_ADMIN_DASHBOARD_CLIENT_ID ?? 'ecommerce-admin-dashboard-client',
      clientSecret: process.env.ECOMMERCE_ADMIN_DASHBOARD_CLIENT_SECRET ?? '',
      authorization: {
        params: {
          scope: 'openid profile email tenant.read ecommerce.operator',
        },
      },
      checks: ['pkce', 'state'],
      profile(profile: GapOidcProfile) {
        return {
          id: profile.sub,
          accountId: profile.account_id ?? profile.sub,
          email: profile.email ?? null,
          name: profile.name ?? profile.preferred_username ?? null,
          tenantId: profile.tenant_id ?? null,
          roles: profile.roles ?? [],
          accountType: profile.account_type ?? null,
        };
      },
    },
  ],
  callbacks: {
    /**
     * Cross-app account_type guard — reject CONSUMER before JWT issuance so the
     * `/login?error=account_type_mismatch` URL is set for the LoginForm banner.
     */
    async signIn({ profile }) {
      const p = profile as GapOidcProfile | undefined;
      if (p?.account_type && p.account_type !== ALLOWED_ACCOUNT_TYPE) {
        return '/login?error=account_type_mismatch';
      }
      return true;
    },
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
        token.accountType = p.account_type ?? token.accountType;
      }
      if (user && 'accountId' in user) {
        const u = user as {
          accountId?: string;
          tenantId?: string | null;
          roles?: string[];
          accountType?: string | null;
        };
        token.accountId = u.accountId ?? token.accountId;
        token.tenantId = u.tenantId ?? token.tenantId;
        token.roles = u.roles ?? token.roles;
        token.accountType = u.accountType ?? token.accountType;
      }
      return token;
    },
    async session({ session, token }) {
      if (token.accountType && token.accountType !== ALLOWED_ACCOUNT_TYPE) {
        return {
          ...session,
          user: undefined as unknown as typeof session.user,
          accountId: null,
          tenantId: null,
          roles: [],
          accessToken: undefined,
          accountType: token.accountType as string,
        };
      }
      session.tenantId = (token.tenantId as string | null | undefined) ?? null;
      session.accountId = (token.accountId as string | null | undefined) ?? null;
      session.roles = (token.roles as string[] | undefined) ?? [];
      session.accessToken = (token.accessToken as string | undefined) ?? undefined;
      session.accountType = (token.accountType as string | null | undefined) ?? null;
      return session;
    },
    authorized({ auth, request }) {
      const { pathname } = request.nextUrl;
      const isPublic =
        pathname.startsWith('/login') ||
        pathname.startsWith('/api/auth') ||
        pathname.startsWith('/_next') ||
        pathname.startsWith('/favicon');
      if (isPublic) return true;
      // admin-dashboard 의 모든 페이지는 OPERATOR 인증을 요구한다.
      return Boolean(auth && auth.accountId);
    },
  },
};

// Re-export each value with an explicit `any` re-cast to avoid TS2742
// "inferred type cannot be named" when `declaration: true` is on. NextAuth
// v5's return shape includes `@auth/core` internal types that are not
// exported through the package surface, so the bundler-resolved types are
// not portable for `.d.ts` emission. We don't actually emit `.d.ts` here
// (next.js builds JS only), but tsc --noEmit still validates the inference.
const nextAuth = NextAuth(authConfig);
export const handlers = nextAuth.handlers;
export const auth: typeof nextAuth.auth = nextAuth.auth;
export const signIn: typeof nextAuth.signIn = nextAuth.signIn;
export const signOut: typeof nextAuth.signOut = nextAuth.signOut;
