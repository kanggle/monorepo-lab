import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * The notification inbox + mark-read routes in the console-web server
 * (TASK-PC-FE-303 / ADR-MONO-081 — `notification-inbox-contract.md` § 4).
 * The aggregation rules live in `shared/notification-inbox.test.ts`; this file
 * pins what only the ROUTE decides — the checks before any call and the HTTP
 * mapping — and that each call goes straight to the owning domain.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({ ERP_BASE_URL: 'http://erp.local' }),
}));

vi.mock('@/shared/config/demo-backend', () => ({
  resolveBackendUrl: async (u: string) => u,
}));

// TASK-PC-FE-343 — row 12 (`/me/domain-access`). Default `null` = «don't know» → the ERP leg is
// called exactly as before, so every older test here keeps its meaning.
const myDomainRoles = vi.hoisted(() => ({
  value: null as { domainKey: string; role: string }[] | null,
  calls: 0,
}));
vi.mock('@/shared/api/iam-operators-read', () => ({
  getMyDomainRolesOrNull: async () => {
    myDomainRoles.calls += 1;
    return myDomainRoles.value;
  },
}));

import { NextRequest } from 'next/server';
import { GET as inboxGET } from '@/app/api/console/notifications/inbox/route';
import { POST as markReadPOST } from '@/app/api/console/notifications/[sourceDomain]/[id]/read/route';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
} from '@/shared/lib/session';
import { NotificationInboxResponseSchema } from '@/features/notifications/api/notification-types';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const erpItem = {
  id: 'n1',
  sourceDomain: 'erp',
  type: 'APPROVAL_SUBMITTED',
  title: '결재 요청 도착',
  body: 'b',
  read: false,
  createdAt: '2026-10-01T00:00:00Z',
};

let fetchMock: ReturnType<typeof vi.fn>;

function session() {
  cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
  cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
  cookieJar.set(TENANT_COOKIE, 'acme');
  // TASK-PC-FE-338 (OD-2): a selected tenant always carries its assumed token (the switch sets both);
  // without it the domain-facing resolver is fail-closed (null → 401), no base-token fallback.
  cookieJar.set(ASSUMED_TOKEN_COOKIE, 'GAP-ASSUMED');
}

function inbox(search = '') {
  return inboxGET(new NextRequest(`http://localhost/api/console/notifications/inbox${search}`));
}

function markRead(sourceDomain: string, id = 'n1') {
  return markReadPOST(new Request('http://localhost/x', { method: 'POST' }), {
    params: Promise.resolve({ sourceDomain, id }),
  });
}

beforeEach(() => {
  cookieJar.clear();
  myDomainRoles.value = null;
  myDomainRoles.calls = 0;
  fetchMock = vi.fn(async () => json({ data: [erpItem], meta: { totalElements: 1 } }));
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function calledUrls(): string[] {
  return fetchMock.mock.calls.map(([u]) => String(u));
}

describe('inbox route', () => {
  it('200 — the contract shape, read from erp directly', async () => {
    session();
    const res = await inbox('?page=0&size=20&unread=true');
    expect(res.status).toBe(200);
    const body = NotificationInboxResponseSchema.parse(await res.json());
    expect(body.items.map((i) => i.id)).toEqual(['n1']);
    expect(body.degradedDomains).toEqual([]);
    expect(calledUrls()).toEqual([
      'http://erp.local/api/erp/notifications?page=0&size=20&unread=true',
    ]);
  });

  it('AC-5 — domain-facing bearer, no X-Tenant-Id even with an active tenant', async () => {
    session();
    await inbox();
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer GAP-ASSUMED');
    expect(Object.keys(headers).map((k) => k.toLowerCase())).not.toContain('x-tenant-id');
    expect(Object.keys(headers).map((k) => k.toLowerCase())).not.toContain('x-operator-token');
  });

  it('no active tenant is NOT a 400 — the bell never required one', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const res = await inbox();
    expect(res.status).toBe(200);
    // The real path, not the sample one.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('half session (no IAM token) → 401, no call', async () => {
    // No cookie at all is a sample visitor (sample-mode-proxies.test.ts ①).
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    const res = await inbox();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(['?page=-1', '?page=x', '?size=0', '?size=101', '?unread=maybe'])(
    'malformed %s → 400, no call',
    async (q) => {
      session();
      const res = await inbox(q);
      expect(res.status).toBe(400);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it('AC-3 — erp 401 → 401 TOKEN_INVALID, not a degraded 200', async () => {
    session();
    fetchMock.mockImplementation(async () => json({ code: 'TOKEN_INVALID' }, 401));
    const res = await inbox();
    expect(res.status).toBe(401);
    expect(((await res.json()) as { code: string }).code).toBe('TOKEN_INVALID');
  });

  it('erp down → 200 with degradedDomains [erp]', async () => {
    session();
    fetchMock.mockImplementation(async () => json({}, 503));
    const res = await inbox();
    expect(res.status).toBe(200);
    const body = NotificationInboxResponseSchema.parse(await res.json());
    expect(body.degradedDomains).toEqual(['erp']);
    expect(body.items).toEqual([]);
  });
});

describe('mark-read route', () => {
  it('AC-4 — unknown sourceDomain → 404, no downstream call', async () => {
    session();
    const res = await markRead('wms');
    expect(res.status).toBe(404);
    expect(((await res.json()) as { code: string }).code).toBe('NOTIFICATION_NOT_FOUND');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('AC-4 — erp → exactly one POST to the erp mark-read path', async () => {
    session();
    fetchMock.mockImplementation(async () => json({ data: { ...erpItem, read: true } }));
    const res = await markRead('erp', 'n 1');
    expect(res.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('http://erp.local/api/erp/notifications/n%201/read');
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer GAP-ASSUMED');
  });

  it.each([
    ['503', async () => json({}, 503), 502],
    ['network', async () => Promise.reject(new TypeError('fetch failed')), 502],
    ['404', async () => json({ code: 'NOTIFICATION_NOT_FOUND' }, 404), 404],
    ['401', async () => json({ code: 'TOKEN_INVALID' }, 401), 401],
  ])('erp %s → %s, still one call', async (_, impl, status) => {
    session();
    fetchMock.mockImplementation(impl);
    const res = await markRead('erp');
    expect(res.status).toBe(status);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('half session (no IAM token) → 401, no call', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    const res = await markRead('erp');
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

/**
 * TASK-PC-FE-343 (contract § 2.4.8 «ERP leg skipped», server = TASK-MONO-793 Q2/Q6): the erp
 * notification endpoints need ANY ERP business role (or `erp.read` / `tenant_id='*'`). An
 * operator row 12 KNOWS holds none would get 403 → `degradedDomains ∋ erp`; the console does not
 * ask. Every «don't know» still asks (fail-open).
 */
describe('inbox route — TASK-PC-FE-343 ERP leg follows the ERP business roles', () => {
  it('active tenant + row 12 with no ERP role → ERP is NOT called, and erp is not «degraded»', async () => {
    session();
    myDomainRoles.value = [{ domainKey: 'wms', role: 'INVENTORY_READ' }];
    const res = await inbox();
    expect(res.status).toBe(200);
    const body = NotificationInboxResponseSchema.parse(await res.json());
    expect(calledUrls().filter((u) => u.startsWith('http://erp.local'))).toEqual([]);
    expect(body.degradedDomains).toEqual([]);
    expect(body.items).toEqual([]);
  });

  it('row 12 empty (no business role at all) → ERP is NOT called', async () => {
    session();
    myDomainRoles.value = [];
    await inbox();
    expect(calledUrls().filter((u) => u.startsWith('http://erp.local'))).toEqual([]);
  });

  it('control: row 12 with one ERP role → ERP is called (personal endpoint opens on any ERP role)', async () => {
    session();
    myDomainRoles.value = [{ domainKey: 'erp', role: 'ERP_APPROVAL_READ' }];
    const res = await inbox();
    const body = NotificationInboxResponseSchema.parse(await res.json());
    expect(calledUrls().filter((u) => u.startsWith('http://erp.local'))).toHaveLength(1);
    expect(body.items.map((i) => i.id)).toEqual(['n1']);
  });

  it('fail-open: row 12 failed (null) → ERP is called', async () => {
    session();
    myDomainRoles.value = null;
    await inbox();
    expect(calledUrls().filter((u) => u.startsWith('http://erp.local'))).toHaveLength(1);
  });

  it('fail-open: no active tenant → ERP is called (row 12 needs a tenant)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    myDomainRoles.value = [];
    await inbox();
    expect(calledUrls().filter((u) => u.startsWith('http://erp.local'))).toHaveLength(1);
  });

  it("the '*' tenant → ERP is called even with no ERP role (the '*' token opens the endpoint)", async () => {
    session();
    cookieJar.set(TENANT_COOKIE, '*');
    myDomainRoles.value = [];
    await inbox();
    expect(calledUrls().filter((u) => u.startsWith('http://erp.local'))).toHaveLength(1);
  });
});
