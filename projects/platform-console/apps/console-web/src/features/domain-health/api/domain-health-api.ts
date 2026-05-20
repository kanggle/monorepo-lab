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
  const base = clientEnv.NEXT_PUBLIC_APP_URL.replace(/\/$/, '');
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
export async function fetchDomainHealth(): Promise<DomainHealth> {
  const res = await fetch(domainHealthUrl(), {
    method: 'GET',
    headers: { Accept: 'application/json' },
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
    const health = await fetchDomainHealth();
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
