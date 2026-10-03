import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Notification aggregator in the console-web server (TASK-PC-FE-303 —
 * `notification-inbox-contract.md` § 4; replaces the former BFF's
 * `NotificationAggregationUseCase`). The domain set in production is `erp`
 * alone, so the isolation rule (AC-2) is measured here with TWO domains.
 */

const envValues: Record<string, unknown> = {};
vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({ ERP_BASE_URL: 'http://erp.local', ...envValues }),
}));

const logs: { level: string; msg: string; fields: Record<string, unknown> }[] = [];
vi.mock('@/shared/lib/logger', () => ({
  logger: {
    info: (msg: string, fields: Record<string, unknown>) => logs.push({ level: 'info', msg, fields }),
    warn: (msg: string, fields: Record<string, unknown>) => logs.push({ level: 'warn', msg, fields }),
    error: () => undefined,
  },
}));

import {
  aggregateInbox,
  configuredInboxDomains,
  domainInboxHeaders,
  findInboxDomain,
  markReadOnce,
  type InboxDomain,
  type InboxQuery,
} from '@/shared/composition/notification-inbox';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function fakeDomain(name: string): InboxDomain {
  return {
    name,
    inboxUrl: (q) => `http://${name}.local/inbox?page=${q.page}&size=${q.size}`,
    markReadUrl: (id) => `http://${name}.local/n/${id}/read`,
  };
}

const A = fakeDomain('alpha');
const B = fakeDomain('beta');
const Q: InboxQuery = { page: 0, size: 20, unread: null };
const H = domainInboxHeaders('DF-TOKEN', 'req-1');

function item(id: string, createdAt: string | null, extra: Record<string, unknown> = {}) {
  return { id, type: 'T', title: id, body: '', read: false, createdAt, ...extra };
}

beforeEach(() => {
  logs.length = 0;
  for (const k of Object.keys(envValues)) delete envValues[k];
});

describe('AC-2 — one domain failing never blanks the bell', () => {
  it.each([
    ['503', async () => json({ code: 'DOWN' }, 503)],
    ['network', async () => Promise.reject(new TypeError('fetch failed'))],
    ['unreadable body', async () => new Response('<html>', { status: 200 })],
    ['403', async () => json({ code: 'PERMISSION_DENIED' }, 403)],
  ])('beta %s → 200, alpha items still there, beta in degradedDomains', async (_, betaRes) => {
    const fetchLeg = vi.fn(async (url: string) =>
      url.startsWith('http://beta')
        ? betaRes()
        : json({ data: [item('a1', '2026-10-01T00:00:00Z')], meta: { totalElements: 1 } }),
    );
    const r = await aggregateInbox([A, B], { query: Q, headers: H, requestId: 'r', fetchLeg });
    expect(r.unauthorized).toBe(false);
    if (r.unauthorized) return;
    expect(r.body.items.map((i) => i.id)).toEqual(['a1']);
    expect(r.body.degradedDomains).toEqual(['beta']);
    expect(r.body.meta).toEqual({ page: 0, size: 20, totalElements: 1 });
  });

  it('beta hangs past the leg timeout → degraded, alpha served', async () => {
    const fetchLeg = vi.fn(
      (url: string, init: RequestInit) =>
        url.startsWith('http://beta')
          ? new Promise<Response>((_, reject) =>
              init.signal!.addEventListener('abort', () => reject(new Error('aborted'))),
            )
          : Promise.resolve(json({ data: [item('a1', '2026-10-01T00:00:00Z')] })),
    );
    const r = await aggregateInbox([A, B], {
      query: Q,
      headers: H,
      requestId: 'r',
      fetchLeg,
      timeoutMs: 20,
    });
    expect(r.unauthorized).toBe(false);
    if (r.unauthorized) return;
    expect(r.body.degradedDomains).toEqual(['beta']);
    expect(r.body.items).toHaveLength(1);
    const betaLog = logs.find((l) => l.msg === 'console_composition_leg' && l.fields.domain === 'beta');
    expect(betaLog?.fields.reason).toBe('TIMEOUT');
  });

  it('every domain down → still 200, all listed', async () => {
    const fetchLeg = vi.fn(async () => json({}, 500));
    const r = await aggregateInbox([A, B], { query: Q, headers: H, requestId: 'r', fetchLeg });
    expect(r.unauthorized).toBe(false);
    if (r.unauthorized) return;
    expect(r.body.items).toEqual([]);
    expect(r.body.degradedDomains).toEqual(['alpha', 'beta']);
  });
});

describe('AC-3 — a domain 401 is the session, not a degraded domain', () => {
  it('beta 401 while alpha is fine → unauthorized', async () => {
    const fetchLeg = vi.fn(async (url: string) =>
      url.startsWith('http://beta') ? json({ code: 'TOKEN_INVALID' }, 401) : json({ data: [] }),
    );
    const r = await aggregateInbox([A, B], { query: Q, headers: H, requestId: 'r', fetchLeg });
    expect(r).toEqual({ unauthorized: true, domain: 'beta' });
  });
});

describe('merge — contract § 1 / § 4 items 1–2', () => {
  it('sorts newest first across domains, missing createdAt last; sums totals', async () => {
    const fetchLeg = vi.fn(async (url: string) =>
      url.startsWith('http://alpha')
        ? json({
            data: [item('a-old', '2026-09-01T00:00:00Z'), item('a-none', null)],
            meta: { totalElements: 7 },
          })
        : json({ data: [item('b-new', '2026-10-01T00:00:00Z')], meta: { totalElements: 3 } }),
    );
    const r = await aggregateInbox([A, B], { query: Q, headers: H, requestId: 'r', fetchLeg });
    if (r.unauthorized) throw new Error('unexpected');
    expect(r.body.items.map((i) => i.id)).toEqual(['b-new', 'a-old', 'a-none']);
    expect(r.body.meta.totalElements).toBe(10);
    expect(r.body.degradedDomains).toEqual([]);
  });

  it('injects sourceDomain only when absent or blank — never overwrites', async () => {
    const fetchLeg = vi.fn(async () =>
      json({
        data: [
          item('x', '2026-10-01T00:00:03Z'),
          item('y', '2026-10-01T00:00:02Z', { sourceDomain: '' }),
          item('z', '2026-10-01T00:00:01Z', { sourceDomain: 'erp' }),
        ],
      }),
    );
    const r = await aggregateInbox([A], { query: Q, headers: H, requestId: 'r', fetchLeg });
    if (r.unauthorized) throw new Error('unexpected');
    expect(r.body.items.map((i) => i.sourceDomain)).toEqual(['alpha', 'alpha', 'erp']);
  });

  it('asOf is the request time, not a leg time', async () => {
    const r = await aggregateInbox([A], {
      query: Q,
      headers: H,
      requestId: 'r',
      fetchLeg: async () => json({ data: [] }),
      now: () => new Date('2026-10-02T12:00:00Z'),
    });
    if (r.unauthorized) throw new Error('unexpected');
    expect(r.body.asOf).toBe('2026-10-02T12:00:00.000Z');
  });
});

describe('AC-5 — per-domain credential (former BFF CredentialSelectionAdapter: erp ← IAM OIDC token)', () => {
  it('erp leg: domain-facing bearer, no X-Tenant-Id, the erp gateway path', async () => {
    const [erp] = configuredInboxDomains();
    const fetchLeg = vi.fn(async () => json({ data: [] }));
    await aggregateInbox([erp], {
      query: { page: 1, size: 5, unread: true },
      headers: H,
      requestId: 'r',
      fetchLeg,
    });
    const [url, init] = fetchLeg.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('http://erp.local/api/erp/notifications?page=1&size=5&unread=true');
    const headers = init.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer DF-TOKEN');
    expect(Object.keys(headers).map((k) => k.toLowerCase())).not.toContain('x-tenant-id');
  });
});

describe('domain set — `CONSOLE_NOTIFICATION_DOMAINS`', () => {
  it('defaults to erp', () => {
    expect(configuredInboxDomains().map((d) => d.name)).toEqual(['erp']);
  });

  it('normalises, dedupes, skips an unknown name with a warning', () => {
    envValues.CONSOLE_NOTIFICATION_DOMAINS = ' ERP , erp,wms ';
    expect(configuredInboxDomains().map((d) => d.name)).toEqual(['erp']);
    expect(logs.some((l) => l.msg === 'notification_inbox_unknown_domain')).toBe(true);
  });

  it('findInboxDomain matches case-insensitively and rejects the rest', () => {
    expect(findInboxDomain([A, B], 'BETA')?.name).toBe('beta');
    expect(findInboxDomain([A, B], 'wms')).toBeNull();
  });
});

describe('AC-4 — mark-read is sent exactly once', () => {
  it.each([
    ['200', async () => json({ data: { id: 'n1', read: true } })],
    ['503', async () => json({}, 503)],
  ])('domain %s → one POST, returned as-is', async (_, res) => {
    const fetchLeg = vi.fn(res);
    const out = await markReadOnce(B, 'n1', { headers: H, requestId: 'r', fetchLeg });
    expect(fetchLeg).toHaveBeenCalledTimes(1);
    const [url, init] = fetchLeg.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('http://beta.local/n/n1/read');
    expect(init.method).toBe('POST');
    expect(out.status).toBe((await res()).status);
  });

  it('timeout → throws after ONE attempt (no retry of a write)', async () => {
    const fetchLeg = vi.fn(
      (_: string, init: RequestInit) =>
        new Promise<Response>((__, reject) =>
          init.signal!.addEventListener('abort', () => reject(new Error('aborted'))),
        ),
    );
    await expect(
      markReadOnce(B, 'n1', { headers: H, requestId: 'r', fetchLeg, timeoutMs: 20 }),
    ).rejects.toThrow();
    expect(fetchLeg).toHaveBeenCalledTimes(1);
  });
});

describe('AC-6 — R1 one structured line per leg', () => {
  it('route / domain / status / reason / latencyMs / requestId, no token', async () => {
    const fetchLeg = vi.fn(async (url: string) =>
      url.startsWith('http://beta') ? json({}, 502) : json({ data: [] }),
    );
    await aggregateInbox([A, B], { query: Q, headers: H, requestId: 'req-9', fetchLeg });
    const legLines = logs.filter((l) => l.msg === 'console_composition_leg');
    expect(legLines).toHaveLength(2);
    const beta = legLines.find((l) => l.fields.domain === 'beta')!;
    expect(beta.fields).toMatchObject({
      route: 'notifications-inbox',
      status: 'degraded',
      reason: 'DOWNSTREAM_ERROR',
      requestId: 'req-9',
    });
    expect(typeof beta.fields.latencyMs).toBe('number');
    expect(JSON.stringify(logs)).not.toContain('DF-TOKEN');
  });
});
