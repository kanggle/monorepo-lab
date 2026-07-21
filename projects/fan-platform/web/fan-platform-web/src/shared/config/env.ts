/**
 * Centralized env access. All process.env reads go through here so unit tests
 * can swap values via vi.stubEnv and so the missing-var fail-fast policy is
 * consistent.
 *
 * Browser-exposed values MUST start with NEXT_PUBLIC_*. The non-public ones
 * are accessed only from server components / server actions / route handlers
 * so leaking into the client bundle is rejected at build time.
 */

export const env = {
  /** Public gateway URL (browser + SSR) — `http://fan-platform.local` in dev. */
  gatewayUrl:
    process.env.NEXT_PUBLIC_GATEWAY_URL ??
    process.env.GATEWAY_URL_INTERNAL ??
    'http://fan-platform.local',
  /** Server-side override for SSR fetches when the in-cluster gateway URL differs. */
  gatewayInternalUrl:
    process.env.GATEWAY_URL_INTERNAL ??
    process.env.NEXT_PUBLIC_GATEWAY_URL ??
    'http://fan-platform.local',
  /** GAP OIDC issuer (server-only). */
  oidcIssuerUrl: process.env.OIDC_ISSUER_URL ?? 'http://iam.local',
  oidcClientId: process.env.OIDC_CLIENT_ID ?? 'fan-platform-user-flow-client',
  oidcClientSecret: process.env.OIDC_CLIENT_SECRET ?? '',
  nextAuthUrl: process.env.NEXTAUTH_URL ?? 'http://localhost:3002',
} as const;

export type Env = typeof env;
