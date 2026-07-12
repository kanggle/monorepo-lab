import { clientEnv } from '@/shared/config/env';
import { ApiError } from '@/shared/api/errors';
import {
  OperatorOverviewSchema,
  type OperatorOverview,
} from './operator-overview-types';

/**
 * Operator-overview fetcher (TASK-PC-FE-011 — ADR-MONO-017 § D8 Phase 7
 * MVP / `console-integration-contract.md` § 2.4.9.1).
 *
 * This module exports two callers — both go through the SAME-ORIGIN
 * Next.js proxy route (`/api/console/dashboards/operator-overview`),
 * which forwards `Authorization` + `X-Operator-Token` + `X-Tenant-Id`
 * to console-bff server-side. The BROWSER NEVER reaches console-bff
 * directly; client JS NEVER reads a session token (the HttpOnly cookie
 * + server proxy are the trust-boundary invariant of the platform —
 * frontend-app.md § Authentication).
 *
 *   - {@link fetchOperatorOverview} — client-side caller used by the
 *     React Query hook (`<RetryButton>` only). Builds an absolute
 *     same-origin URL via `clientEnv.NEXT_PUBLIC_APP_URL` and sets
 *     `credentials: 'include'` so the HttpOnly session cookies ride.
 *   - {@link getOperatorOverviewState} — server-side caller for the
 *     SSR route entry (`(console)/dashboards/overview/page.tsx`).
 *     Calls the SAME proxy URL server-to-server (Next.js runs the
 *     route handler in-process); cookies are read server-side by the
 *     proxy from `next/headers`. Returns a discriminated result the
 *     page maps to redirect / no-tenant / overview rendering.
 *
 * Both paths share `OperatorOverviewSchema` for runtime validation —
 * the contract is byte-verbatim from § 2.4.9.1 and the BE `OperatorOverviewResponse`
 * Java record.
 *
 * READ-ONLY (§ 2.4.9): GET only; no body, no `Idempotency-Key`, no
 * `X-Operator-Reason`. The hard invariant the BE asserts is mirrored
 * here at the fetch boundary.
 */

const OPERATOR_OVERVIEW_PATH = '/api/console/dashboards/operator-overview';

/**
 * Builds the absolute same-origin URL for the fetch. On the server-side
 * render path Node's `fetch` requires an absolute URL; on the
 * client-side the same path resolves correctly (the URL is still
 * same-origin). Using `clientEnv.NEXT_PUBLIC_APP_URL` keeps the URL
 * valid in both contexts without leaking server-only env.
 */
function operatorOverviewUrl(): string {
  // Same-origin in the browser → relative. `NEXT_PUBLIC_APP_URL` is inlined at
  // BUILD time (TASK-MONO-358), so an absolute base baked into the bundle
  // points at the build host, not the serving host. See domain-health-api.ts.
  if (typeof window !== 'undefined') return OPERATOR_OVERVIEW_PATH;
  const base = (
    process.env.CONSOLE_PUBLIC_ORIGIN ?? clientEnv.NEXT_PUBLIC_APP_URL
  ).replace(/\/$/, '');
  return `${base}${OPERATOR_OVERVIEW_PATH}`;
}

/** Parses the proxy's error envelope `{ code, message }` defensively. */
async function readErrorEnvelope(
  res: Response,
): Promise<{ code: string; message: string }> {
  let code = `HTTP_${res.status}`;
  let message = res.statusText || 'operator overview request failed';
  try {
    const body = (await res.json()) as { code?: unknown; message?: unknown };
    if (typeof body?.code === 'string') code = body.code;
    if (typeof body?.message === 'string') message = body.message;
  } catch {
    /* keep defaults — never crash on a non-JSON error body */
  }
  return { code, message };
}

/**
 * Fetches the composed operator overview envelope from the same-origin
 * BFF proxy route. Throws `ApiError(status, code, message)` for any
 * non-2xx response; returns the parsed/validated envelope otherwise.
 *
 * Used by the React Query hook (client-side) — that's why this
 * function does NOT import server-only modules (`shared/lib/session`,
 * `next/headers`) and uses only `clientEnv`. The server SSR caller
 * uses {@link getOperatorOverviewState} which can react to ApiError
 * for redirects.
 */
export async function fetchOperatorOverview(
  /**
   * Optional Cookie header for server-side callers (the SSR
   * `getOperatorOverviewState` wrapper passes the page's request cookies
   * verbatim here). On the client, `credentials: 'include'` lets the
   * browser attach HttpOnly cookies natively, so this stays undefined.
   *
   * <p>TASK-PC-FE-030 — Node `fetch` in a server component has no cookie
   * jar, and `credentials: 'include'` is a browser-only directive. Without
   * this explicit forward, the in-process proxy route's `cookies()` read
   * returns empty → the proxy bails with `400 NO_ACTIVE_TENANT` → the
   * page sees `noTenant: true` even when the browser HAS the cookie.
   */
  cookieHeader?: string,
): Promise<OperatorOverview> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (cookieHeader) {
    headers.Cookie = cookieHeader;
  }
  const res = await fetch(operatorOverviewUrl(), {
    method: 'GET',
    headers,
    // Same-origin HttpOnly session cookies ride through the proxy on the
    // browser path; on the server path the `Cookie` header above carries
    // them explicitly (Node fetch has no implicit cookie jar).
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    const { code, message } = await readErrorEnvelope(res);
    throw new ApiError(res.status, code, message);
  }

  const raw = (await res.json()) as unknown;
  return OperatorOverviewSchema.parse(raw);
}

/**
 * Server-side discriminated state for the SSR route entry. Mirrors
 * the existing `features/dashboards/api/overview-state.ts` shape so
 * the page handles the same three outcomes uniformly:
 *
 *   - `noTenant: true` — render the "select a tenant" gate (BFF
 *     proxy fast-failed with 400 NO_ACTIVE_TENANT before any outbound).
 *   - `unauthorized: true` — the page calls `redirect('/login')`
 *     (BFF returned 401 TOKEN_INVALID; no partial authed state).
 *   - `overview` present — render `<OperatorOverviewScreen>`.
 *
 * Per-card degrade lives INSIDE `overview.cards[i].status` (the 200
 * payload); it is NEVER a state field here. A whole-fan-out failure
 * (proxy 502 BAD_GATEWAY) surfaces as `bffUnavailable: true`.
 */
export interface OperatorOverviewState {
  overview: OperatorOverview | null;
  noTenant: boolean;
  unauthorized: boolean;
  bffUnavailable: boolean;
}

/**
 * Server-side SSR fetch wrapper. Calls the same Next.js proxy URL
 * server-to-server (the proxy is in-process); reads the result and
 * maps non-2xx into the discriminated state. Used by the page entry
 * only.
 */
export async function getOperatorOverviewState(): Promise<OperatorOverviewState> {
  try {
    // TASK-PC-FE-030 — forward the page's request cookies to the
    // in-process proxy fetch. Next.js Node `fetch` does NOT auto-forward
    // cookies on internal calls (`credentials: 'include'` is browser-only),
    // so without this explicit header the proxy's `cookies()` reads empty
    // → 400 NO_ACTIVE_TENANT → `noTenant: true` even when the browser
    // session has the active-tenant cookie. Lazy-imported to keep this
    // module callable from the browser path (the React Query hook uses
    // `fetchOperatorOverview` directly with no cookie arg).
    const { cookies } = await import('next/headers');
    const cookieHeader = (await cookies()).toString();
    const overview = await fetchOperatorOverview(cookieHeader);
    return {
      overview,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    };
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.status === 400 && err.code === 'NO_ACTIVE_TENANT') {
        return {
          overview: null,
          noTenant: true,
          unauthorized: false,
          bffUnavailable: false,
        };
      }
      if (err.status === 401) {
        return {
          overview: null,
          noTenant: false,
          unauthorized: true,
          bffUnavailable: false,
        };
      }
    }
    return {
      overview: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    };
  }
}
