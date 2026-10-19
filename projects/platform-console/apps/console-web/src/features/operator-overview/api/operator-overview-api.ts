import { ApiError } from '@/shared/api/errors';
import {
  OperatorOverviewSchema,
  type OperatorOverview,
} from './operator-overview-types';

/**
 * Operator-overview fetcher (TASK-PC-FE-011 — ADR-MONO-017 § D8 Phase 7
 * MVP / `console-integration-contract.md` § 2.4.9.1).
 *
 * The envelope is composed server-side from the session's tokens by
 * `composeOperatorOverview()` (`shared/composition/operator-overview-composition.ts`).
 * The BROWSER NEVER reaches a domain directly; client JS NEVER reads a session
 * token (the HttpOnly cookie + server route are the trust-boundary invariant of
 * the platform — frontend-app.md § Authentication). Two callers:
 *
 *   - {@link fetchOperatorOverview} (this module) — client-side caller used by
 *     the React Query hook (`<RetryButton>` only). GETs the SAME-ORIGIN route
 *     (`/api/console/dashboards/operator-overview`) on the RELATIVE path with
 *     `credentials: 'include'` so the HttpOnly session cookies ride.
 *   - `getOperatorOverviewState` — server-side caller for the SSR route entry
 *     (`(console)/dashboards/overview/page.tsx`). Since TASK-PC-FE-350 it calls
 *     the composition IN-PROCESS — it no longer fetches the route. It lives in
 *     the sibling `operator-overview-state.ts` because it reads the session, and
 *     THAT module must never be reachable from the client graph (TASK-MONO-585 —
 *     see `shared/config/env.ts`).
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
 * it imports ships to the browser. The server SSR caller is
 * `getOperatorOverviewState` (sibling `operator-overview-state.ts`), which
 * composes in-process and maps the answer for redirects.
 */
export async function fetchOperatorOverview(): Promise<OperatorOverview> {
  // TASK-PC-FE-350 — browser-only. The `cookieHeader` / `baseUrl` parameters the
  // SSR path used (TASK-PC-FE-030 / TASK-MONO-585) are gone with that path: the
  // server page composes in-process (`operator-overview-state.ts`), so nothing on
  // the server fetches this route any more. A new server caller would be the hop
  // 350 removed — call `composeOperatorOverview()` instead.
  // DEMO-URL-EXEMPT: same-origin — 이 앱 자신의 라우트 핸들러다(백엔드가 아니다). 브라우저 전용 상대경로.
  const res = await fetch(OPERATOR_OVERVIEW_PATH, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    // Same-origin HttpOnly session cookies ride on the browser path.
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
