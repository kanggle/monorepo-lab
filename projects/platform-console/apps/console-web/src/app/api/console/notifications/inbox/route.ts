import { NextRequest, NextResponse } from 'next/server';
import {
  getDomainFacingToken,
  getOperatorToken,
  getActiveTenant,
} from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin server proxy for the console-bff **notification aggregator**
 * inbox (ADR-MONO-043 P3b — the shared-shell bell's data source). The browser
 * NEVER reaches console-bff directly; the credential is attached server-side
 * from the HttpOnly cookie session.
 *
 * Forwarded inbound headers:
 *   - `Authorization: Bearer <domain-facing GAP/IAM OIDC token>` — the inbound
 *     principal the BFF dispatches per-domain (D6; the erp leg uses this token
 *     and sends NO `X-Tenant-Id` — erp reads tenant from the JWT claim).
 *   - `X-Operator-Token` — forwarded when present (forward-compat for a future
 *     IAM leg; the erp Phase-1 leg does not use it).
 *   - `X-Tenant-Id` — forwarded when an active tenant is selected (the BFF
 *     does NOT require it for the notification aggregator; absent is fine).
 *
 * READ-ONLY: GET only, no body. The aggregator always returns HTTP 200 (D5
 * failure isolation — a downed domain appears in `degradedDomains`, never a
 * 5xx), so the 200 passthrough is the main path.
 *
 * Outcome map:
 *   - no domain-facing token → 401 TOKEN_INVALID (we do not call the BFF).
 *   - BFF 200 → passthrough verbatim (degradedDomains live inside the payload).
 *   - BFF 401 → 401 (api-client refresh + single retry).
 *   - BFF non-2xx / network / parse → 502 BAD_GATEWAY.
 *
 * No token is ever logged (only request id + status).
 */

/** Target URL on the console-bff side — the docker network, not the Traefik edge
 *  (TASK-MONO-362: console-bff holds no edge router). */
function bffUrl(search: string): string {
  const base = (
    process.env.CONSOLE_BFF_URL || 'http://console-bff:8080'
  ).replace(/\/$/, '');
  return `${base}/api/console/notifications/inbox${search}`;
}

export async function GET(req: NextRequest) {
  const requestId = newRequestId();

  const domainFacingToken = await getDomainFacingToken();
  if (!domainFacingToken) {
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session not authenticated' },
      { status: 401 },
    );
  }

  const outboundHeaders: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${domainFacingToken}`,
    'X-Request-Id': requestId,
  };
  const operatorToken = await getOperatorToken();
  if (operatorToken) outboundHeaders['X-Operator-Token'] = operatorToken;
  const tenant = await getActiveTenant();
  if (tenant) outboundHeaders['X-Tenant-Id'] = tenant;

  // Forward the inbox query (page/size/unread) verbatim.
  const search = req.nextUrl.search ?? '';

  let res: Response;
  try {
    res = await fetch(bffUrl(search), {
      method: 'GET',
      headers: outboundHeaders,
      cache: 'no-store',
    });
  } catch {
    logger.warn('notification_inbox_proxy_network_error', { requestId });
    return NextResponse.json(
      { code: 'BAD_GATEWAY', message: 'console-bff unreachable' },
      { status: 502 },
    );
  }

  if (res.status === 200) {
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      logger.warn('notification_inbox_proxy_bad_body', { requestId });
      return NextResponse.json(
        { code: 'BAD_GATEWAY', message: 'console-bff returned invalid body' },
        { status: 502 },
      );
    }
    return NextResponse.json(body, { status: 200 });
  }

  if (res.status === 401) {
    let envelope: { code?: unknown; message?: unknown } = {};
    try {
      envelope = (await res.json()) as { code?: unknown; message?: unknown };
    } catch {
      /* keep defaults */
    }
    const code =
      typeof envelope.code === 'string' ? envelope.code : 'TOKEN_INVALID';
    const message =
      typeof envelope.message === 'string' ? envelope.message : 'session expired';
    logger.warn('notification_inbox_proxy_401', { requestId });
    return NextResponse.json({ code, message }, { status: 401 });
  }

  // The aggregator never emits 503 (D5); any non-200/401 is transport/proxy.
  logger.warn('notification_inbox_proxy_unexpected_status', {
    requestId,
    status: res.status,
  });
  return NextResponse.json(
    { code: 'BAD_GATEWAY', message: 'console-bff unexpected response' },
    { status: 502 },
  );
}
