import NextAuth, { type NextAuthConfig } from 'next-auth';

/**
 * NextAuth v5 (auth.js) configuration — GAP OIDC + PKCE for web-store (consumer).
 *
 * GAP V0012 seed registers:
 *   - client_id:     `ecommerce-web-store-client`
 *   - client_secret: `ecommerce-dev` (plain, dev-only)
 *   - redirect_uris: http://localhost:3000/api/auth/callback/iam,
 *                    http://web.ecommerce.local/api/auth/callback/iam
 *   - scopes:        openid profile email tenant.read ecommerce.consumer
 *
 * web-store is the consumer-facing storefront. The token's `roles` must include
 * `CUSTOMER`; an operator (whose ecommerce token carries `ADMIN` / no `CUSTOMER`)
 * is rejected at the sign-in + session callbacks and redirected to
 * `/login?error=account_type_mismatch`. ADR-MONO-035 (4b-1): the storefront guard
 * is **role-based** — the legacy `account_type` claim is being removed in
 * ADR-MONO-032 D5 step 4, so consumer-vs-operator is decided by role presence
 * (`CUSTOMER`), not the deprecated `account_type` partition.
 *
 * Token storage: HttpOnly JWT cookie. The bearer token is exposed only via the
 * server-side `getSession()` helper in `./session.ts`. Client components
 * receive `tenantId` / `accountId` / `roles` only.
 *
 * Lazy provider config (NextAuth v5 default): the OIDC discovery doc is fetched
 * on first sign-in attempt, so dev boot / lint / unit-test do not require GAP
 * to be running.
 *
 * See:
 *   - projects/iam-platform/specs/features/consumer-integration-guide.md § Phase 4 (PKCE)
 *   - projects/ecommerce-microservices-platform/specs/integration/iam-integration.md (TASK-MONO-027)
 *   - projects/fan-platform/web/fan-platform-web/src/shared/auth/auth.ts (reference pattern)
 */

interface IamOidcProfile {
  sub: string;
  email?: string;
  name?: string;
  preferred_username?: string;
  tenant_id?: string;
  account_id?: string;
  account_type?: 'CONSUMER' | 'OPERATOR' | string;
  roles?: string[];
}

/**
 * ADR-MONO-035 (4b-1): the storefront requires the `CUSTOMER` role. Consumers
 * carry it (RoleSeedPolicy `ecommerce → CUSTOMER`, or a stored `account_roles`
 * grant); operators carry `ADMIN` / no `CUSTOMER`. Role-based replaces the
 * legacy `account_type === 'CONSUMER'` check (ADR-MONO-032 D5 step 4 removes
 * `account_type`).
 */
const REQUIRED_CONSUMER_ROLE = 'CUSTOMER';

function hasConsumerRole(roles: string[] | undefined | null): boolean {
  return Array.isArray(roles) && roles.includes(REQUIRED_CONSUMER_ROLE);
}

export const authConfig: NextAuthConfig = {
  // Self-hosted (no Vercel) — honor `AUTH_TRUST_HOST=true` for non-Vercel
  // deployments and during dev when running behind Traefik.
  trustHost: true,
  session: { strategy: 'jwt' },
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
      issuer: process.env.OIDC_ISSUER_URL ?? 'http://iam.local',
      clientId: process.env.ECOMMERCE_WEB_STORE_CLIENT_ID ?? 'ecommerce-web-store-client',
      clientSecret: process.env.ECOMMERCE_WEB_STORE_CLIENT_SECRET ?? '',
      authorization: {
        params: {
          scope: 'openid profile email tenant.read ecommerce.consumer',
        },
      },
      // PKCE + state — required by GAP for `authorization_code` flow
      checks: ['pkce', 'state'],
      profile(profile: IamOidcProfile) {
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
     * Cross-app role guard (ADR-MONO-035 4b-1). Reject the sign-in *before* a
     * valid JWT is issued (so NextAuth redirects through the configured
     * `pages.error` URL) unless the GAP token carries the `CUSTOMER` role. An
     * operator's ecommerce token carries `ADMIN` / no `CUSTOMER` → rejected.
     * session() also keeps a defense-in-depth degraded-session branch for stale
     * cookies. (Role-based: the `account_type` claim is removed in ADR-032 D5
     * step 4 — gating on its absence would no-op and admit operators.)
     */
    async signIn({ profile }) {
      const p = profile as IamOidcProfile | undefined;
      if (!hasConsumerRole(p?.roles)) {
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
        const p = profile as IamOidcProfile;
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
    /**
     * Expose tenant_id / account_id / roles / accountType to RSC pages and
     * server actions. `accessToken` deliberately stays only on the JWT —
     * server-only helpers (see `./session.ts`) read it via `auth()` so client
     * components never receive the bearer token.
     *
     * If the token's `roles` do not include `CUSTOMER`, return a degraded
     * (anonymous) session so NextAuth treats it as unauthenticated. Combined
     * with the `/login` error page, the user sees the mismatch banner.
     * (ADR-MONO-035 4b-1: role-based — replaces the `account_type !== CONSUMER`
     * check, which would no-op once `account_type` is dropped.)
     */
    async session({ session, token }) {
      if (!hasConsumerRole(token.roles as string[] | undefined)) {
        // Surface as anonymous — middleware will redirect to /login.
        // (Returning the augmented session anyway would let an operator
        // browse the storefront with the backend rejecting every API call.)
        return {
          ...session,
          user: undefined as unknown as typeof session.user,
          accountId: null,
          tenantId: null,
          roles: [],
          accessToken: undefined,
          accountType: (token.accountType as string | null | undefined) ?? null,
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
        pathname === '/' ||
        pathname.startsWith('/login') ||
        pathname.startsWith('/signup') ||
        pathname.startsWith('/products') ||
        pathname.startsWith('/api/auth') ||
        pathname.startsWith('/_next') ||
        pathname.startsWith('/favicon');
      if (isPublic) return true;
      // Block access if not authenticated OR account_type mismatch
      // (session callback returns null user for non-CONSUMER).
      return Boolean(auth && auth.accountId);
    },
  },
};

// Re-export each value with explicit type re-cast to avoid TS2742
// "inferred type cannot be named" when the NextAuth v5 destructured exports
// reference @auth/core internal types not exposed through the package
// surface.
const nextAuth = NextAuth(authConfig);
export const handlers = nextAuth.handlers;
export const auth: typeof nextAuth.auth = nextAuth.auth;
export const signIn: typeof nextAuth.signIn = nextAuth.signIn;
export const signOut: typeof nextAuth.signOut = nextAuth.signOut;
