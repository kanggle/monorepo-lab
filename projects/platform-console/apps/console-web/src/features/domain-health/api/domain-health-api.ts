import { clientEnv } from '@/shared/config/env';
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
 *     React Query hook (`<RetryButton>` only). Builds an absolute
 *     same-origin URL via `clientEnv.NEXT_PUBLIC_APP_URL` and sets
 *     `credentials: 'include'` so the HttpOnly session cookies ride.
 *   - {@link getDomainHealthState} — server-side caller for the
 *     SSR route entry (`(console)/dashboards/health/page.tsx`).
 *     Calls the SAME proxy URL server-to-server (Next.js runs the
 *     route handler in-process); cookies are read server-side by the
 *     proxy from `next/headers`.
 *
 * Both paths share `DomainHealthSchema` for runtime validation — the
 * contract is byte-verbatim from § 2.4.9.2 and the BE
 * `DomainHealthResponse` Java record.
 *
 * READ-ONLY (§ 2.4.9): GET only; no body, no `Idempotency-Key`, no
 * `X-Operator-Reason`. The hard invariant the BE asserts is mirrored
 * here at the fetch boundary.
 */

const DOMAIN_HEALTH_PATH = '/api/console/dashboards/domain-health';

function domainHealthUrl(): string {
  // In the browser this call is same-origin by definition, so no base is
  // needed — and none may be used: `NEXT_PUBLIC_APP_URL` is inlined at BUILD
  // time (TASK-MONO-358), so a prebuilt image would send the browser to
  // whatever host the build knew about (`console.local`) instead of the one
  // it is actually being served from. Relative is both simpler and correct.
  if (typeof window !== 'undefined') return DOMAIN_HEALTH_PATH;
  // Server-side (SSR route entry): fetch() needs an absolute URL. Prefer the
  // runtime-resolvable origin; fall back to the build-time value.
  const base = (
    process.env.CONSOLE_PUBLIC_ORIGIN ?? clientEnv.NEXT_PUBLIC_APP_URL
  ).replace(/\/$/, '');
  return `${base}${DOMAIN_HEALTH_PATH}`;
}

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
): Promise<DomainHealth> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (cookieHeader) {
    headers.Cookie = cookieHeader;
  }
  const res = await fetch(domainHealthUrl(), {
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

/**
 * Server-side discriminated state for the SSR route entry. Mirrors
 * `features/operator-overview/api/operator-overview-api.ts`
 * `OperatorOverviewState` so the page handles the same three outcomes
 * uniformly:
 *
 *   - `noTenant: true` — proxy fast-failed with 400 NO_ACTIVE_TENANT.
 *   - `unauthorized: true` — proxy returned 401 (Spring Security
 *     rejected the inbound `Authorization` bearer).
 *   - `health` present — render `<DomainHealthScreen>`.
 *
 * Per-card degrade lives INSIDE `health.cards[i].status` (the 200
 * payload); it is NEVER a state field here. A whole-fan-out failure
 * (proxy 502 BAD_GATEWAY) surfaces as `bffUnavailable: true`.
 */
export interface DomainHealthState {
  health: DomainHealth | null;
  noTenant: boolean;
  unauthorized: boolean;
  bffUnavailable: boolean;
}

export async function getDomainHealthState(): Promise<DomainHealthState> {
  try {
    // TASK-PC-FE-037 — forward the page's request cookies to the in-process
    // proxy fetch (the operator-overview sibling already does this via
    // TASK-PC-FE-030). Next.js Node `fetch` does NOT auto-forward cookies on
    // internal calls (`credentials: 'include'` is browser-only), so without
    // this explicit header the proxy's `cookies()` reads empty → 400
    // NO_ACTIVE_TENANT → `noTenant: true` even when the session HAS an active
    // tenant. Lazy-imported so the browser path (`fetchDomainHealth` via the
    // React Query hook) never pulls in `next/headers`.
    const { cookies } = await import('next/headers');
    const cookieHeader = (await cookies()).toString();
    const health = await fetchDomainHealth(cookieHeader);
    return {
      health,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    };
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.status === 400 && err.code === 'NO_ACTIVE_TENANT') {
        return {
          health: null,
          noTenant: true,
          unauthorized: false,
          bffUnavailable: false,
        };
      }
      if (err.status === 401) {
        return {
          health: null,
          noTenant: false,
          unauthorized: true,
          bffUnavailable: false,
        };
      }
    }
    return {
      health: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    };
  }
}
