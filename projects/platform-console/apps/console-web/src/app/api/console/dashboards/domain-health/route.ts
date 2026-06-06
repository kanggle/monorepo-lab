import { NextResponse } from 'next/server';
import { getAccessToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin server proxy for the BFF-routed Phase 7 "Domain Health
 * Overview" composition (TASK-PC-FE-013 — `console-integration-contract.md`
 * § 2.4.9.2).
 *
 * The browser NEVER reaches `console-bff` directly. The 2 inbound headers
 * this route forwards (per § 2.4.9.2 Auth flow):
 *   - `Authorization: Bearer <gap-oidc-access-token>` (inbound principal,
 *     RS256 / IAM issuer — Spring Security on the BFF validates).
 *   - `X-Tenant-Id: <active-tenant>` (forwarded for log MDC + audit
 *     traceability; the BFF's outbound actuator legs do NOT consume it).
 *
 * **`X-Operator-Token` is intentionally NOT forwarded** — this divergence
 * from § 2.4.9.1 is explicit in § 2.4.9.2: the BFF does not require it
 * (no outbound leg consumes it; the D4 sealed-switch is never invoked
 * because public actuator endpoints are outside D4's scope per the
 * § D4 scope clarification). Sending it would be misleading.
 *
 * READ-ONLY (§ 2.4.9 HARD INVARIANT): GET only, no body, no
 * `Idempotency-Key`, no `X-Operator-Reason`. The BFF route never
 * carries a mutation method; adding one is a contract defect.
 *
 * HTTP outcome map (mirrors BFF + § 2.4.9.2 error envelope):
 *   - inbound tenant absent → 400 NO_ACTIVE_TENANT (BEFORE any outbound;
 *     the BFF also enforces it).
 *   - inbound IAM access-token absent → 401 TOKEN_INVALID (the BFF
 *     would also reject; we do not call it in that state).
 *   - BFF 200 → passthrough verbatim (per-card degrade is INSIDE the
 *     200 payload as `card.status`; the proxy never re-classifies).
 *   - BFF 400 NO_ACTIVE_TENANT → 400.
 *   - BFF 401 → 401 (client api-client triggers /api/auth/refresh and a
 *     single retry; on retry-fail it redirects to /login).
 *   - BFF non-2xx other → 502 BAD_GATEWAY (the BFF never emits 503 per
 *     § 2.4.9.2 error envelope; reaching this branch means transport /
 *     parse / unexpected status).
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
  return `${base}/api/console/dashboards/domain-health`;
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
  if (!accessToken) {
    // Inbound principal absent — the BFF would also reject (Spring
    // Security 401). We do not call it in that state.
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
        'X-Tenant-Id': tenant,
        'X-Request-Id': requestId,
      },
      cache: 'no-store',
    });
  } catch {
    logger.warn('domain_health_proxy_network_error', { requestId });
    return NextResponse.json(
      { code: 'BAD_GATEWAY', message: 'console-bff unreachable' },
      { status: 502 },
    );
  }

  if (res.status === 200) {
    // The BFF response body IS the wire envelope verbatim — the proxy
    // never re-shapes it (per-card degrade lives inside the payload,
    // not in the HTTP status).
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      logger.warn('domain_health_proxy_bad_body', { requestId });
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
    logger.warn('domain_health_proxy_4xx', {
      requestId,
      status: res.status,
      code,
    });
    return NextResponse.json({ code, message }, { status: res.status });
  }

  logger.warn('domain_health_proxy_unexpected_status', {
    requestId,
    status: res.status,
  });
  return NextResponse.json(
    { code: 'BAD_GATEWAY', message: 'console-bff unexpected response' },
    { status: 502 },
  );
}
