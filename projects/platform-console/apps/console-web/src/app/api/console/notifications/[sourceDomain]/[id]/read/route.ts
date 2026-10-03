import { NextResponse } from 'next/server';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { sampleGate } from '@/shared/api/sample-gate';
import {
  configuredInboxDomains,
  domainInboxHeaders,
  findInboxDomain,
  markReadOnce,
} from '@/shared/composition/notification-inbox';
import { resolveBackendUrl } from '@/shared/config/demo-backend';

export const runtime = 'nodejs';

/**
 * Notification mark-read — `platform/contracts/notification-inbox-contract.md`
 * § 4 items 5–6. Dispatched from the console-web server to the OWNING domain
 * since TASK-PC-FE-303 (ADR-MONO-081, A); before that it went via the former
 * BFF (retired — ADR-MONO-081).
 *
 * Naturally idempotent (state-converging) — no body, no `Idempotency-Key`.
 * It is still a write, so it is sent **exactly once**: no retry wrapper, and
 * a timeout is answered 502 rather than re-sent.
 *
 * Outcome map (unchanged for the browser):
 *   - `{sourceDomain}` not configured → 404 NOTIFICATION_NOT_FOUND, no call;
 *   - no domain-facing token → 401 TOKEN_INVALID, no call;
 *   - domain 200 → passthrough (the updated notification);
 *   - domain 401 / 404 → same status, the domain's `code` when it gave one;
 *   - other status / network / timeout / unreadable body → 502 BAD_GATEWAY.
 */
export async function POST(
  _req: Request,
  { params }: { params: Promise<{ sourceDomain: string; id: string }> },
) {
  const requestId = newRequestId();
  const { sourceDomain, id } = await params;

  // ADR-MONO-074 A2 / R1ⓐ — asked BEFORE any token read. A sample visitor's
  // mark-read is refused by the sample router (`403 SAMPLE_READ_ONLY`), and that
  // Response is fed into the mapping below UNCHANGED — which maps a non-200/401/
  // 404 to `502 BAD_GATEWAY`, as it always has. 🔵 The bell ignores mark-read
  // failures by design (navigation must not block), so no refusal copy is
  // rendered for this write; nothing claims it succeeded either. Sample core
  // `console-composition` (renamed by TASK-MONO-757).
  const sample = await sampleGate({
    core: 'console-composition',
    surface: 'notifications-read',
    method: 'POST',
    path: `/api/console/notifications/${encodeURIComponent(sourceDomain)}/${encodeURIComponent(id)}/read`,
  });

  let res: Response;
  if (sample) {
    res = sample;
  } else {
    const domain = findInboxDomain(configuredInboxDomains(), sourceDomain);
    if (!domain) {
      // Contract § 4 item 6 — answered here, before any downstream call.
      return NextResponse.json(
        { code: 'NOTIFICATION_NOT_FOUND', message: 'unknown notification domain' },
        { status: 404 },
      );
    }

    const domainFacingToken = await getDomainFacingToken();
    if (!domainFacingToken) {
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'session not authenticated' },
        { status: 401 },
      );
    }

    try {
      res = await markReadOnce(domain, id, {
        headers: domainInboxHeaders(domainFacingToken, requestId),
        requestId,
        // The demo host is resolved here, at the call site (check-fetch-resolution).
        fetchLeg: async (url, init) => fetch(await resolveBackendUrl(url), init),
      });
    } catch {
      logger.warn('notification_markread_network_error', { requestId });
      return NextResponse.json(
        { code: 'BAD_GATEWAY', message: 'notification domain unreachable' },
        { status: 502 },
      );
    }
  }

  if (res.status === 200) {
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      return NextResponse.json(
        { code: 'BAD_GATEWAY', message: 'notification domain returned invalid body' },
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
    logger.warn('notification_markread_4xx', { requestId, status: res.status });
    return NextResponse.json({ code, message }, { status: res.status });
  }

  logger.warn('notification_markread_unexpected_status', {
    requestId,
    status: res.status,
  });
  return NextResponse.json(
    { code: 'BAD_GATEWAY', message: 'notification domain unexpected response' },
    { status: 502 },
  );
}
