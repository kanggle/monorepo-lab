import { NextResponse } from 'next/server';
import {
  getDomainFacingToken,
  getOperatorToken,
  getActiveTenant,
} from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Same-origin server proxy for the console-bff notification aggregator
 * MARK-READ (ADR-MONO-043 P3b — contract § 4.5). Forwards
 * `POST /api/console/notifications/{sourceDomain}/{id}/read` to console-bff,
 * which dispatches it to the OWNING domain with that domain's credential (D6).
 *
 * Naturally idempotent (state-converging) — no body, no `Idempotency-Key`.
 * The credential is attached server-side from the HttpOnly session; the
 * browser never reaches console-bff or a domain directly.
 *
 * Outcome map:
 *   - no domain-facing token → 401 TOKEN_INVALID.
 *   - BFF 200 → passthrough (the updated notification `{ data }`).
 *   - BFF 401 → 401; BFF 404 (unknown sourceDomain / notification) → 404.
 *   - other / network / parse → 502 BAD_GATEWAY.
 */

/** Docker-network address, not the Traefik edge (TASK-MONO-362: console-bff holds
 *  no edge router — every call is server-side, from this route handler). */
function bffUrl(sourceDomain: string, id: string): string {
  const base = (
    process.env.CONSOLE_BFF_URL || 'http://console-bff:8080'
  ).replace(/\/$/, '');
  return `${base}/api/console/notifications/${encodeURIComponent(sourceDomain)}/${encodeURIComponent(id)}/read`;
}

export async function POST(
  _req: Request,
  { params }: { params: Promise<{ sourceDomain: string; id: string }> },
) {
  const requestId = newRequestId();
  const { sourceDomain, id } = await params;

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

  let res: Response;
  try {
    // DEMO-URL-EXEMPT: console-bff-internal — console-bff 는 **공개 호스트명이 없다**
    //   (`TASK-MONO-362` 가 그 Traefik 라우터를 일부러 없앴다: 백엔드 서비스는 엣지에
    //   노출되지 않는다 — `api-gateway-policy.md` L14). 주소는 도커 네트워크 DNS
    //   (`http://console-bff:8080`)이고 데모 도메인으로 파생될 수 있는 값이 아니다.
    //   🔴 그래서 **Vercel 에서는 이 레그가 닿지 않는다** — TASK-MONO-585 § 알려진 한계.
    res = await fetch(bffUrl(sourceDomain, id), {
      method: 'POST',
      headers: outboundHeaders,
      cache: 'no-store',
    });
  } catch {
    logger.warn('notification_markread_proxy_network_error', { requestId });
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
      return NextResponse.json(
        { code: 'BAD_GATEWAY', message: 'console-bff returned invalid body' },
        { status: 502 },
      );
    }
    return NextResponse.json(body, { status: 200 });
  }

  if (res.status === 401 || res.status === 404) {
    let envelope: { code?: unknown; message?: unknown } = {};
    try {
      envelope = (await res.json()) as { code?: unknown; message?: unknown };
    } catch {
      /* keep defaults */
    }
    const fallbackCode = res.status === 401 ? 'TOKEN_INVALID' : 'NOTIFICATION_NOT_FOUND';
    const code = typeof envelope.code === 'string' ? envelope.code : fallbackCode;
    const message =
      typeof envelope.message === 'string' ? envelope.message : 'request failed';
    logger.warn('notification_markread_proxy_4xx', { requestId, status: res.status });
    return NextResponse.json({ code, message }, { status: res.status });
  }

  logger.warn('notification_markread_proxy_unexpected_status', {
    requestId,
    status: res.status,
  });
  return NextResponse.json(
    { code: 'BAD_GATEWAY', message: 'console-bff unexpected response' },
    { status: 502 },
  );
}
