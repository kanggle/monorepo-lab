import { ApiError } from '@/shared/api/errors';
import { DomainHealthSchema, type DomainHealth } from './types';

/**
 * Domain-health fetcher (TASK-PC-FE-013 — `console-integration-contract.md`
 * § 2.4.9.2).
 *
 * Sibling of `features/operator-overview/api/operator-overview-api.ts`
 * (TASK-PC-FE-011 / § 2.4.9.1) — same posture, distinct route.
 *
 * The health envelope is composed server-side by `composeDomainHealth()`
 * (`shared/composition/domain-health-composition.ts`). The
 * BROWSER NEVER reaches a domain directly; client JS NEVER reads
 * a session token (HttpOnly cookie + server route = trust-boundary
 * invariant of the platform — frontend-app.md § Authentication).
 *
 * **Divergence from § 2.4.9.1** (intentional): the health legs carry NO
 * credential and NO tenant header — actuator legs are public per the
 * § D4 scope clarification. The route still checks for a tenant and a
 * session before any leg.
 *
 *   - {@link fetchDomainHealth} — client-side caller used by the
 *     React Query hook (`<RetryButton>` only). GETs the SAME-ORIGIN route
 *     (`/api/console/dashboards/domain-health`) on the RELATIVE path and
 *     sets `credentials: 'include'` so the HttpOnly session cookies ride.
 *   - `getDomainHealthState` — server-side caller for the SSR route
 *     entry (`(console)/dashboards/health/page.tsx`). Since TASK-PC-FE-350
 *     it calls the composition IN-PROCESS — it no longer fetches the route.
 *     It lives in the sibling `domain-health-state.ts` because it reads the
 *     session, and THAT module must never be reachable from the client graph
 *     (TASK-MONO-585 — see `shared/config/env.ts`).
 *
 * Both paths share `DomainHealthSchema` for runtime validation — the
 * contract is byte-verbatim from § 2.4.9.2 and the BE
 * `DomainHealthResponse` Java record.
 *
 * READ-ONLY (§ 2.4.9): GET only; no body, no `Idempotency-Key`, no
 * `X-Operator-Reason`. The hard invariant the BE asserts is mirrored
 * here at the fetch boundary.
 */

export const DOMAIN_HEALTH_PATH = '/api/console/dashboards/domain-health';

/** Parses the proxy's error envelope `{ code, message }` defensively. */
async function readErrorEnvelope(
  res: Response,
): Promise<{ code: string; message: string }> {
  let code = `HTTP_${res.status}`;
  let message = res.statusText || 'domain health request failed';
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
 * Fetches the composed domain-health envelope from the same-origin
 * BFF proxy route. Throws `ApiError(status, code, message)` for any
 * non-2xx response; returns the parsed/validated envelope otherwise.
 */
export async function fetchDomainHealth(): Promise<DomainHealth> {
  // TASK-PC-FE-350 — browser-only. The `cookieHeader` / `baseUrl` parameters the
  // SSR path used (TASK-PC-FE-037 / TASK-MONO-585) are gone with that path: the
  // server pages compose in-process (`domain-health-state.ts`), so nothing on the
  // server fetches this route any more. A new server caller would be the hop 350
  // removed — call `composeDomainHealth()` instead.
  // DEMO-URL-EXEMPT: same-origin — 이 앱 자신의 라우트 핸들러다(백엔드가 아니다). 브라우저 전용 상대경로.
  const res = await fetch(DOMAIN_HEALTH_PATH, {
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
  return DomainHealthSchema.parse(raw);
}
