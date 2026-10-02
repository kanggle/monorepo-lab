import { NextRequest, NextResponse } from 'next/server';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { sampleGate } from '@/shared/api/sample-gate';
import {
  aggregateInbox,
  configuredInboxDomains,
  domainInboxHeaders,
  type InboxQuery,
} from '@/shared/composition/notification-inbox';
import { resolveBackendUrl } from '@/shared/config/demo-backend';

export const runtime = 'nodejs';

/**
 * Notification-bell inbox — `platform/contracts/notification-inbox-contract.md` § 4.
 *
 * Aggregated in the console-web server since TASK-PC-FE-303 (ADR-MONO-081, A).
 * Before that this route proxied to `console-bff`, which has no public
 * hostname, so the Vercel console's bell could never load. The wire shape is
 * unchanged: `{ asOf, items, meta: { page, size, totalElements }, degradedDomains }`.
 *
 * Each configured domain (`CONSOLE_NOTIFICATION_DOMAINS`, default `erp`) is
 * read with the domain-facing IAM OIDC token and no `X-Tenant-Id` — erp
 * resolves tenant and recipient from the token. No active tenant is required,
 * as it never was: the bell renders across the whole console shell.
 *
 * Outcomes:
 *   - malformed `page` / `size` / `unread` → 400 VALIDATION_ERROR, no call;
 *   - no domain-facing token → 401 TOKEN_INVALID, no call;
 *   - any domain 401 → 401 TOKEN_INVALID (contract § 4 item 4 — an expired
 *     session is not a degraded domain);
 *   - otherwise 200 — a failed domain is listed in `degradedDomains` and the
 *     others' items still render (HARD INVARIANT, ADR-MONO-043 D5).
 *
 * READ-ONLY: GET only. No token is ever logged.
 */
export async function GET(req: NextRequest) {
  const requestId = newRequestId();
  const search = req.nextUrl.search ?? '';

  // ADR-MONO-074 A2 — asked BEFORE any token read. A sample visitor gets the
  // sample inbox; no domain is called. The sample core keeps the name
  // `console-bff` (ADR-MONO-081 rider check — renaming it trips the ledger guard).
  const sample = await sampleGate({
    core: 'console-bff',
    surface: 'notifications-inbox',
    method: 'GET',
    path: `/api/console/notifications/inbox${search}`,
  });
  if (sample) return passthroughSample(sample, requestId);

  const query = parseQuery(req.nextUrl.searchParams);
  if (!query) {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'page, size and unread must be well-formed' },
      { status: 400 },
    );
  }

  const domainFacingToken = await getDomainFacingToken();
  if (!domainFacingToken) {
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session not authenticated' },
      { status: 401 },
    );
  }

  const result = await aggregateInbox(configuredInboxDomains(), {
    query,
    headers: domainInboxHeaders(domainFacingToken, requestId),
    requestId,
    // The demo host is resolved here, at the call site (check-fetch-resolution).
    fetchLeg: async (url, init) => fetch(await resolveBackendUrl(url), init),
  });

  if (result.unauthorized) {
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'session expired' },
      { status: 401 },
    );
  }
  return NextResponse.json(result.body, { status: 200 });
}

/** `page` ≥ 0 (default 0), `size` 1–100 (default 20), `unread` true/false/absent. */
function parseQuery(params: URLSearchParams): InboxQuery | null {
  const int = (raw: string | null, fallback: number): number | null => {
    if (raw === null || raw === '') return fallback;
    return /^\d+$/.test(raw) ? Number(raw) : null;
  };
  const page = int(params.get('page'), 0);
  const size = int(params.get('size'), 20);
  if (page === null || size === null || size < 1 || size > 100) return null;
  const rawUnread = params.get('unread');
  let unread: boolean | null = null;
  if (rawUnread === 'true') unread = true;
  else if (rawUnread === 'false') unread = false;
  else if (rawUnread !== null && rawUnread !== '') return null;
  return { page, size, unread };
}

/** The sample surface answers with the same body the aggregation would. */
async function passthroughSample(res: Response, requestId: string): Promise<NextResponse> {
  if (res.status === 200) {
    try {
      return NextResponse.json(await res.json(), { status: 200 });
    } catch {
      /* fall through */
    }
  }
  logger.warn('notification_inbox_sample_unexpected', { requestId, status: res.status });
  return NextResponse.json(
    { code: 'BAD_GATEWAY', message: 'sample inbox unavailable' },
    { status: 502 },
  );
}
