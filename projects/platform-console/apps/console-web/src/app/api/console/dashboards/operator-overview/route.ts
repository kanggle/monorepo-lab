import { NextResponse } from 'next/server';
import {
  getAccessToken,
  getOperatorToken,
  getActiveTenant,
} from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin server proxy for the BFF-routed cross-domain operator
 * overview (TASK-PC-FE-011 — `console-integration-contract.md`
 * § 2.4.9.1).
 *
 * The browser NEVER reaches `console-bff` directly. The 3 inbound
 * headers required by the BFF (per § 2.4.9.1):
 *   - `Authorization: Bearer <gap-oidc-access-token>` (inbound principal)
 *   - `X-Operator-Token: <rfc8693-operator-token>` (request-scoped)
 *   - `X-Tenant-Id: <active-tenant>` (forwarded verbatim)
 * are read SERVER-SIDE here from the HttpOnly cookie session
 * (`shared/lib/session`) and forwarded to console-bff. The browser
 * has no JS path to these — server-component first + HttpOnly cookie
 * boundary (frontend-app.md § Authentication; architecture.md
 * § Forbidden Dependencies).
 *
 * READ-ONLY (§ 2.4.9 HARD INVARIANT): GET only, no body, no
 * `Idempotency-Key`, no `X-Operator-Reason`. The BFF route never
 * carries a mutation method; adding one is a contract defect.
 *
 * HTTP outcome map (mirrors BFF + § 2.4.9.1 error envelope):
 *   - inbound tenant absent → 400 NO_ACTIVE_TENANT (BEFORE any
 *     outbound; the BFF would also reject — pre-emptive client-side
 *     fail-closed).
 *   - inbound operator-token / access-token absent → 401
 *     TOKEN_INVALID (the BFF would also reject; we do not call it
 *     in that state).
 *   - BFF 200 → passthrough verbatim (the per-card degrade is INSIDE
 *     the 200 payload as `card.status`; the proxy never re-classifies).
 *   - BFF 400 NO_ACTIVE_TENANT → 400.
 *   - BFF 401 → 401 (the client api-client triggers /api/auth/refresh
 *     and a single retry; on retry-fail it redirects to /login).
 *   - BFF non-2xx other → 502 BAD_GATEWAY (NOT a 503 BFF emit — the
 *     BFF never emits 503 per D5.B; reaching this branch means
 *     transport / parse / unexpected status).
 *   - network/timeout/parse failure → 502 BAD_GATEWAY.
 *
 * No token / source PII is ever logged (only request id + status).
 */

/** Target URL on the console-bff side. Env-overridable; defaults to
 *  `http://console-bff.local` per the local-network convention
 *  (ADR-MONO-001 hostname-based routing). */
function bffUrl(): string {
  const base = (
    process.env.CONSOLE_BFF_URL || 'http://console-bff.local'
  ).replace(/\/$/, '');
  return `${base}/api/console/dashboards/operator-overview`;
}

export async function GET() {
  const requestId = newRequestId();

  const tenant = await getActiveTenant();
  if (!tenant) {
    return NextResponse.json(
      { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
      { status: 400 },
    );
  }

  const accessToken = await getAccessToken();
  const operatorToken = await getOperatorToken();
  if (!accessToken || !operatorToken) {
    // No partial authed state — both tokens required (mirrors
    // `isAuthenticated()` in shared/lib/session.ts).
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session not authenticated' },
      { status: 401 },
    );
  }

  let res: Response;
  try {
    res = await fetch(bffUrl(), {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${accessToken}`,
        'X-Operator-Token': operatorToken,
        'X-Tenant-Id': tenant,
        'X-Request-Id': requestId,
      },
      cache: 'no-store',
    });
  } catch {
    logger.warn('operator_overview_proxy_network_error', { requestId });
    return NextResponse.json(
      { code: 'BAD_GATEWAY', message: 'console-bff unreachable' },
      { status: 502 },
    );
  }

  // Passthrough for the two contractually defined surfaces.
  if (res.status === 200) {
    // The BFF response body IS the wire envelope verbatim — the proxy
    // never re-shapes it (per-card degrade lives inside the payload,
    // not in the HTTP status).
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      logger.warn('operator_overview_proxy_bad_body', { requestId });
      return NextResponse.json(
        { code: 'BAD_GATEWAY', message: 'console-bff returned invalid body' },
        { status: 502 },
      );
    }
    return NextResponse.json(body, { status: 200 });
  }

  if (res.status === 400 || res.status === 401) {
    let envelope: { code?: unknown; message?: unknown } = {};
    try {
      envelope = (await res.json()) as { code?: unknown; message?: unknown };
    } catch {
      /* keep defaults */
    }
    const code =
      typeof envelope.code === 'string'
        ? envelope.code
        : res.status === 400
          ? 'NO_ACTIVE_TENANT'
          : 'TOKEN_INVALID';
    const message =
      typeof envelope.message === 'string'
        ? envelope.message
        : res.status === 400
          ? 'no active tenant selected'
          : 'session expired';
    logger.warn('operator_overview_proxy_4xx', {
      requestId,
      status: res.status,
      code,
    });
    return NextResponse.json({ code, message }, { status: res.status });
  }

  // The BFF never emits 503 (D5.B); 5xx here means transport / proxy
  // / unexpected upstream. Surface as BAD_GATEWAY so the operator
  // sees an "overview unavailable" state without confusing the
  // per-card discipline.
  logger.warn('operator_overview_proxy_unexpected_status', {
    requestId,
    status: res.status,
  });
  return NextResponse.json(
    { code: 'BAD_GATEWAY', message: 'console-bff unexpected response' },
    { status: 502 },
  );
}
