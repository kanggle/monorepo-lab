import { publicEnv } from '@/shared/config/public-env';

/**
 * Centralized env access. All process.env reads go through here so unit tests
 * can swap values via vi.stubEnv and so the missing-var fail-fast policy is
 * consistent.
 *
 * 🔴 SERVER-ONLY. Do not import this module from a client component.
 *
 * The previous version of this comment claimed that "leaking into the client
 * bundle is rejected at build time". That was WRONG, and TASK-MONO-586 measured
 * it: the build withholds the VALUE of a non-public env var, not the MODULE. A
 * single client-component import pulls this whole object literal into the
 * browser chunk, fallback strings included -- chunk 992 carried
 * `nextAuthUrl: ... ? o : "http://localhost:3002"` even though NEXTAUTH_URL is
 * not NEXT_PUBLIC_. TASK-MONO-565 found the same thing for `iam.local` first.
 *
 * Client components import `public-env.ts` instead; that module holds only
 * NEXT_PUBLIC_* values and is the ONLY config module the browser may see.
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
  /**
   * PortOne V2 public keys -- re-exported from `public-env.ts` so server code
   * keeps one place to read env from. 🔴 The browser must import `publicEnv`
   * directly; importing THIS module from a client component drags every
   * fallback literal above into the chunk.
   */
  portoneStoreId: publicEnv.portoneStoreId,
  portoneChannelKey: publicEnv.portoneChannelKey,
} as const;

export type Env = typeof env;
