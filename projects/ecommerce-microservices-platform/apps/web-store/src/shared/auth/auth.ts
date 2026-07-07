import NextAuth, { type NextAuthConfig } from 'next-auth';
import {
  type IamOidcProfile,
  OIDC_ISSUER_URL,
  WEB_STORE_CLIENT_ID,
  WEB_STORE_CLIENT_SECRET,
  jwtCallback,
  sessionCallback,
  signInCallback,
} from '@/shared/auth/auth-callbacks';

// Re-export the silent-refresh helper so existing import sites keep working.
export { refreshAccessToken } from '@/shared/auth/auth-callbacks';

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
 * `CUSTOMER`; an operator (whose ecommerce token carries `ECOMMERCE_OPERATOR` / no `CUSTOMER`)
 * is rejected at the sign-in + session callbacks and redirected to
 * `/login?error=account_type_mismatch`. ADR-MONO-035 (4b-1): the storefront guard
 * is **role-based** — the legacy `account_type` claim is being removed in
 * ADR-MONO-032 D5 step 4, so consumer-vs-operator is decided by role presence
 * (`CUSTOMER`), not the deprecated `account_type` partition.
 *
 * Token storage: HttpOnly JWT cookie. The access / refresh / id tokens live
 * ONLY on the encrypted server-side NextAuth JWT and are NEVER serialized onto
 * the public session (read by the client via `/api/auth/session`). The bearer
 * is attached to downstream calls only through the server-only
 * `getWebStoreSession()` helper (`./session.ts`, RSC / server actions) and the
 * same-origin BFF proxy (`/api/bff/[...path]`, client components). Client
 * components receive `tenantId` / `accountId` / `roles` / display `email`·`name`
 * only — consumer-integration-guide § Phase 4.5 F2 (token confidentiality).
 *
 * Silent refresh (Phase 4.5 F3): the OIDC access token is short-lived (30m).
 * When it is at/near expiry the `jwt` callback exchanges the stored
 * `refresh_token` at `${OIDC_ISSUER_URL}/oauth2/token`
 * (grant_type=refresh_token, client_secret_basic) and stores the rotated pair
 * (IAM `reuse-refresh-tokens=false`). On refresh failure the JWT is flagged
 * `error: 'RefreshAccessTokenError'` so the next protected request forces a
 * full re-auth redirect (F1).
 *
 * The pure callback logic + the refresh helper live in `./auth-callbacks.ts`
 * (no `next-auth` import) so they are unit-testable without the `NextAuth()`
 * factory (which transitively imports `next/server`).
 *
 * Lazy provider config (NextAuth v5 default): the OIDC discovery doc is fetched
 * on first sign-in attempt, so dev boot / lint / unit-test do not require GAP
 * to be running.
 *
 * See:
 *   - projects/iam-platform/specs/features/consumer-integration-guide.md § Phase 4.5 (F2/F3/F5)
 *   - projects/ecommerce-microservices-platform/specs/integration/iam-integration.md (TASK-MONO-027)
 *   - projects/fan-platform/web/fan-platform-web/src/shared/auth/auth.ts (reference pattern)
 */

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
      issuer: OIDC_ISSUER_URL,
      clientId: WEB_STORE_CLIENT_ID,
      clientSecret: WEB_STORE_CLIENT_SECRET,
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
        };
      },
    },
  ],
  callbacks: {
    /**
     * Cross-app role guard (ADR-MONO-035 4b-1). Reject the sign-in *before* a
     * valid JWT is issued (so NextAuth redirects through the configured
     * `pages.error` URL) unless the GAP token carries the `CUSTOMER` role.
     */
    async signIn({ profile }) {
      return signInCallback(profile as IamOidcProfile | undefined);
    },
    async jwt({ token, account, profile, user }) {
      return jwtCallback({ token, account, profile, user });
    },
    /**
     * Expose ONLY non-sensitive identity claims (Phase 4.5 F2). Degrades to
     * anonymous on role-mismatch or refresh failure. See `sessionCallback`.
     */
    async session({ session, token }) {
      return sessionCallback({ session, token }) as unknown as typeof session;
    },
    authorized({ auth, request }) {
      const { pathname } = request.nextUrl;
      const isPublic =
        pathname === '/' ||
        pathname.startsWith('/login') ||
        pathname.startsWith('/signup') ||
        pathname.startsWith('/products') ||
        pathname.startsWith('/api/auth') ||
        // The same-origin BFF proxy enforces auth via the server-side session
        // token and returns 401 (→ client re-auth, F1) when absent; it must not
        // be middleware-bounced to /login (that would break XHR re-auth).
        pathname.startsWith('/api/bff') ||
        pathname.startsWith('/_next') ||
        pathname.startsWith('/favicon');
      if (isPublic) return true;
      // Block access if not authenticated OR role mismatch
      // (session callback returns null accountId for non-CONSUMER).
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
