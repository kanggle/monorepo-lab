import { ApiError } from '@/shared/api/errors';
import { DomainHealthSchema, type DomainHealth } from './types';

/**
 * Domain-health fetcher (TASK-PC-FE-013 — `console-integration-contract.md`
 * § 2.4.9.2).
 *
 * Sibling of `features/operator-overview/api/operator-overview-api.ts`
 * (TASK-PC-FE-011 / § 2.4.9.1) — same posture, distinct route.
 *
 * Both callers below go through the SAME-ORIGIN Next.js proxy route
 * (`/api/console/dashboards/domain-health`), which forwards
 * `Authorization` + `X-Tenant-Id` to console-bff server-side. The
 * BROWSER NEVER reaches console-bff directly; client JS NEVER reads
 * a session token (HttpOnly cookie + server proxy = trust-boundary
 * invariant of the platform — frontend-app.md § Authentication).
 *
 * **Header divergence from § 2.4.9.1** (intentional): the proxy
 * forwards ONLY `Authorization` + `X-Tenant-Id`. It does NOT forward
 * `X-Operator-Token` — the BFF does not consume it on this route
 * (the D4 sealed-switch is never invoked; actuator legs are public
 * per the § D4 scope clarification). Sending it would be misleading.
 *
 *   - {@link fetchDomainHealth} — client-side caller used by the
 *     React Query hook (`<RetryButton>` only). Uses the RELATIVE
 *     same-origin path and sets `credentials: 'include'` so the
 *     HttpOnly session cookies ride.
 *   - `getDomainHealthState` — server-side caller for the SSR route
 *     entry (`(console)/dashboards/health/page.tsx`). It lives in the
 *     sibling `domain-health-state.ts` because it needs this app's
 *     absolute origin, and THAT module must never be reachable from
 *     the client graph (TASK-MONO-585 — see `shared/config/env.ts`).
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
export async function fetchDomainHealth(
  /**
   * Optional Cookie header for server-side callers (the SSR
   * {@link getDomainHealthState} wrapper passes the page's request cookies
   * verbatim here). On the client, `credentials: 'include'` lets the browser
   * attach the HttpOnly cookies natively, so this stays undefined.
   *
   * <p>TASK-PC-FE-037 (mirrors the TASK-PC-FE-011/030 fix on the
   * operator-overview sibling) — Node `fetch` in a server component has no
   * cookie jar, and `credentials: 'include'` is a browser-only directive.
   * Without this explicit forward, the in-process proxy route's `cookies()`
   * read returns empty → the proxy bails with `400 NO_ACTIVE_TENANT` → the
   * 도메인 상태 개요 page shows "select a tenant" on EVERY load even after
   * the operator has selected one (the bug this closes).
   */
  cookieHeader?: string,
  /**
   * Absolute origin to prefix the same-origin path with — server callers ONLY.
   *
   * <p>TASK-MONO-585. In the browser this stays undefined and the fetch uses the
   * RELATIVE path: same-origin by definition, and no base MAY be used, because
   * `NEXT_PUBLIC_APP_URL` is inlined at BUILD time (TASK-MONO-358) — a prebuilt
   * artifact would send the browser to whatever host the build knew about
   * (`console.local`) instead of the one it is being served from.
   *
   * <p>🔴 It is a PARAMETER rather than something this module resolves itself
   * for one reason: this module is in the CLIENT graph (the React Query hook
   * imports it), so anything it imports ships to the browser. The server caller
   * (`domain-health-state.ts`) resolves the origin and passes it in.
   */
  baseUrl?: string,
): Promise<DomainHealth> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (cookieHeader) {
    headers.Cookie = cookieHeader;
  }
  // DEMO-URL-EXEMPT: same-origin — 이 앱 자신의 라우트 핸들러다(백엔드가 아니다).
  //   브라우저 경로는 상대경로, 서버 경로는 호출자가 건넨 `selfOrigin()`.
  const res = await fetch(`${baseUrl ?? ''}${DOMAIN_HEALTH_PATH}`, {
    method: 'GET',
    headers,
    // Same-origin HttpOnly session cookies ride through the proxy on the
    // browser path; on the server path the `Cookie` header above carries them
    // explicitly (Node fetch has no implicit cookie jar).
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
