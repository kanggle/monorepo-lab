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
  const base = clientEnv.NEXT_PUBLIC_APP_URL.replace(/\/$/, '');
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
export async function fetchOperatorOverview(): Promise<OperatorOverview> {
  const res = await fetch(operatorOverviewUrl(), {
    method: 'GET',
    headers: { Accept: 'application/json' },
    // Same-origin HttpOnly session cookies ride through the proxy.
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
    const overview = await fetchOperatorOverview();
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
