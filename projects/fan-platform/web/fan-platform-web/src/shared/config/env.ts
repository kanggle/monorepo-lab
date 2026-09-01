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

/**
 * The issuer value that is correct everywhere except Vercel. Named so the
 * fallback and the error message below cannot drift apart.
 */
const LOCAL_ISSUER_FALLBACK = 'http://iam.local';

/**
 * True inside a Vercel build or serverless function. `VERCEL` is a Vercel
 * system variable; TASK-MONO-611 verified it is actually injected into this
 * project's production environment (alongside `VERCEL_ENV` /
 * `VERCEL_TARGET_ENV`) rather than assuming the docs.
 *
 * 🔴 `NODE_ENV === 'production'` would be the WRONG predicate here: the demo
 * host also runs a production build, and there `LOCAL_ISSUER_FALLBACK` is the
 * correct value.
 */
const ON_VERCEL = Boolean(process.env.VERCEL);

/**
 * 🔴 Names the missing variable at process start.
 *
 * Before TASK-MONO-611 the absence of `OIDC_ISSUER_URL` produced NO signal at
 * all: `kanggle-fan` production holds four variables and none of them is this
 * one, so every sign-in attempt went to `http://iam.local`, which a Vercel
 * function cannot resolve, and the only trace was an unattributed
 * `[auth][error] TypeError: fetch failed`.
 */
if (ON_VERCEL && !process.env.OIDC_ISSUER_URL) {
  console.error(
    '[env] OIDC_ISSUER_URL is not set while running on Vercel. OIDC sign-in ' +
      `will fail: the fallback (${LOCAL_ISSUER_FALLBACK}) resolves nowhere ` +
      'from a serverless function. See TASK-MONO-610 for the public IdP name.',
  );
}

/**
 * Throws when `OIDC_ISSUER_URL` is absent AND we are on Vercel, where no
 * fallback can work. Call this on the paths that are about to reach the IdP
 * over the network; it turns `TypeError: fetch failed` into a message that
 * names the variable.
 *
 * 🔴🔴 **Why this is a function and not a throw at module scope.**
 * TASK-MONO-611 measured the naive version: putting the throw in the object
 * literal above makes `next build` fail with
 *
 *     Failed to collect page data for /api/auth/[...nextauth]
 *
 * (rc=1, against a control build that is rc=0 with the same environment).
 * `middleware.ts`, `widgets/header/Header.tsx` and the auth route all import
 * `shared/auth/auth.ts`, which imports this module, and Next evaluates those
 * during "Collecting page data". So the naive fail-fast does not fail the
 * broken login -- it fails EVERY future deployment of the whole app, including
 * changes that have nothing to do with auth. The ticket's own Failure Scenario
 * only warned about local and CI; the deploy pipeline was the larger blast
 * radius, and only building it showed that.
 *
 * 🔵 Not covered: a direct `POST /api/auth/signin/iam` (Auth.js's own
 * endpoint, bypassing the login form's server action) still fails as
 * `fetch failed`. Guarding the catch-all route handler would also guard
 * `/api/auth/session`, which every page depends on -- that trade was not
 * worth it for a path the UI never takes. Untested, not passing.
 */
export function assertOidcIssuerConfigured(): void {
  if (process.env.OIDC_ISSUER_URL) return;
  if (!ON_VERCEL) return;
  throw new Error(
    'OIDC_ISSUER_URL is not set. On Vercel there is no usable fallback: the ' +
      `local default (${LOCAL_ISSUER_FALLBACK}) resolves nowhere from a ` +
      'serverless function, so OIDC discovery fails as an unattributed ' +
      '"fetch failed". Set OIDC_ISSUER_URL to the public IdP origin. Do not ' +
      'pin a boot-scoped IP: TASK-MONO-611 measured a sibling project doing ' +
      'exactly that and the value was dead three days later.',
  );
}

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
  /**
   * GAP OIDC issuer (server-only).
   *
   * 🔴 The fallback is deliberate and must stay: `http://iam.local` is a REAL
   * value. The local Traefik resolves it, and so does the demo host -- which
   * runs a production build. It is meaningless in exactly one place, a Vercel
   * function, where nothing resolves `*.local`.
   *
   * That is why the deficiency is not caught here. See
   * `assertOidcIssuerConfigured()` below for where it IS caught, and why not
   * at module scope.
   */
  oidcIssuerUrl: process.env.OIDC_ISSUER_URL ?? LOCAL_ISSUER_FALLBACK,
  oidcClientId: process.env.OIDC_CLIENT_ID ?? 'fan-platform-user-flow-client',
  /**
   * 🔴 Same swallow-shape as `oidcIssuerUrl` had, and deliberately NOT changed
   * by TASK-MONO-611: `kanggle-fan` production DOES carry `OIDC_CLIENT_SECRET`
   * (measured 2026-09-01), so this default is not a live hole. Fixing it is a
   * different decision anyway -- the honest local default is the seeded dev
   * secret (`fan-platform-dev`, supplied by docker-compose), not `''`, and
   * changing that changes local behaviour. Named here so the next reader knows
   * it was seen, not missed.
   */
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
