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
 *     React Query hook (`<RetryButton>` only). Uses the RELATIVE
 *     same-origin path and sets `credentials: 'include'` so the
 *     HttpOnly session cookies ride.
 *   - `getOperatorOverviewState` — server-side caller for the SSR
 *     route entry (`(console)/dashboards/overview/page.tsx`). It lives
 *     in the sibling `operator-overview-state.ts` because it needs this
 *     app's absolute origin, and THAT module must never be reachable
 *     from the client graph (TASK-MONO-585 — see `shared/config/env.ts`).
 *
 * Both paths share `OperatorOverviewSchema` for runtime validation —
 * the contract is byte-verbatim from § 2.4.9.1 and the BE `OperatorOverviewResponse`
 * Java record.
 *
 * READ-ONLY (§ 2.4.9): GET only; no body, no `Idempotency-Key`, no
 * `X-Operator-Reason`. The hard invariant the BE asserts is mirrored
 * here at the fetch boundary.
 */

export const OPERATOR_OVERVIEW_PATH =
  '/api/console/dashboards/operator-overview';

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
 * `next/headers`, `shared/config/*`) — it is in the CLIENT graph, so anything
 * it imports ships to the browser. The server SSR caller uses
 * `getOperatorOverviewState` (sibling `operator-overview-state.ts`), which can
 * react to ApiError for redirects.
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
  /**
   * Absolute origin to prefix the same-origin path with — server callers ONLY.
   * Undefined in the browser, where the RELATIVE path is both correct and the
   * only safe choice (`NEXT_PUBLIC_APP_URL` is build-time inlined,
   * TASK-MONO-358). See the sibling `domain-health-api.ts` for why this is a
   * parameter instead of something this module resolves (TASK-MONO-585).
   */
  baseUrl?: string,
): Promise<OperatorOverview> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (cookieHeader) {
    headers.Cookie = cookieHeader;
  }
  // DEMO-URL-EXEMPT: same-origin — 이 앱 자신의 라우트 핸들러다(백엔드가 아니다).
  //   브라우저 경로는 상대경로, 서버 경로는 호출자가 건넨 `selfOrigin()`.
  const res = await fetch(`${baseUrl ?? ''}${OPERATOR_OVERVIEW_PATH}`, {
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
