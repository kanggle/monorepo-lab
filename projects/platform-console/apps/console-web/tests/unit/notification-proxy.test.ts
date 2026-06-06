import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin erp notification proxy route handlers (TASK-PC-FE-052):
 *   - notifications/route: GET only (no POST/PUT/PATCH/DELETE)
 *   - notifications/[id]/route: GET only
 *   - notifications/[id]/read/route: POST only
 *   server-only domain-facing GAP token; no Idempotency-Key, no body on
 *   mark-read; error mapping via the shared erp `_proxy` mapper; 404
 *   NOTIFICATION_NOT_FOUND passthrough.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    ERP_BASE_URL: 'http://erp.local',
    ERP_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as inboxGET } from '@/app/api/erp/notifications/route';
import { GET as detailGET } from '@/app/api/erp/notifications/[id]/route';
import { POST as readPOST } from '@/app/api/erp/notifications/[id]/read/route';
import * as inboxRoute from '@/app/api/erp/notifications/route';
import * as detailRoute from '@/app/api/erp/notifications/[id]/route';
import * as readRoute from '@/app/api/erp/notifications/[id]/read/route';
import { ACCESS_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function erpError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const NOTIFICATION = {
  id: 'ntf-1',
  type: 'APPROVAL_SUBMITTED',
  title: '결재 상신 통지',
  body: '상신됨',
  sourceType: 'APPROVAL',
  sourceId: 'appr-1',
  read: false,
  createdAt: '2026-06-05T00:00:00Z',
};
const LIST = {
  data: [NOTIFICATION],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};
const DETAIL = {
  data: NOTIFICATION,
  meta: { timestamp: 'x' },
};
const READ_DETAIL = {
  data: { ...NOTIFICATION, read: true, readAt: '2026-06-05T01:00:00Z' },
  meta: { timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

// ===========================================================================
// method exposure — each route exports ONLY its allowed method handler.
// ===========================================================================

describe('notification proxy — method exposure', () => {
  it('inbox route: GET only (no POST/PUT/PATCH/DELETE)', () => {
    expect(typeof inboxRoute.GET).toBe('function');
    expect((inboxRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((inboxRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((inboxRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((inboxRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });

  it('detail route: GET only (no POST/PUT/PATCH/DELETE)', () => {
    expect(typeof detailRoute.GET).toBe('function');
    expect((detailRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((detailRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((detailRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((detailRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });

  it('read route: POST only (no GET/PUT/PATCH/DELETE)', () => {
    expect(typeof readRoute.POST).toBe('function');
    expect((readRoute as Record<string, unknown>).GET).toBeUndefined();
    expect((readRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((readRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((readRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
});

// ===========================================================================
// GET /api/erp/notifications — inbox.
// ===========================================================================

describe('GET /api/erp/notifications', () => {
  it('domain-facing GAP token; forwards unread/page/size; returns list', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST));
    vi.stubGlobal('fetch', fetchMock);
    const res = await inboxGET(
      new Request('http://console.local/api/erp/notifications?unread=true&page=0&size=5'),
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    expect(u.searchParams.get('unread')).toBe('true');
    expect(u.searchParams.get('page')).toBe('0');
    expect(u.searchParams.get('size')).toBe('5');
  });

  it('unread NOT present in query string → NOT forwarded upstream', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST));
    vi.stubGlobal('fetch', fetchMock);
    await inboxGET(
      new Request('http://console.local/api/erp/notifications?page=0&size=20'),
    );
    const u = new URL(String(fetchMock.mock.calls[0][0]));
    expect(u.searchParams.has('unread')).toBe(false);
  });

  it('no GAP session → 401, no upstream call', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await inboxGET(
      new Request('http://console.local/api/erp/notifications'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 from upstream → 503 response', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('SERVICE_UNAVAILABLE', 503)));
    const res = await inboxGET(
      new Request('http://console.local/api/erp/notifications'),
    );
    expect(res.status).toBe(503);
  });
});

// ===========================================================================
// GET /api/erp/notifications/{id} — detail.
// ===========================================================================

describe('GET /api/erp/notifications/{id}', () => {
  it('returns { data } wrapping the notification; GET only; no Idempotency-Key / reason', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const res = await detailGET(
      new Request('http://console.local/api/erp/notifications/ntf-1'),
      { params: Promise.resolve({ id: 'ntf-1' }) },
    );
    expect(res.status).toBe(200);
    const b = await res.json();
    expect(b.data.id).toBe('ntf-1');
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('GET');
    const h = init.headers as Record<string, string>;
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('404 NOTIFICATION_NOT_FOUND passthrough (foreign/unknown id)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('NOTIFICATION_NOT_FOUND', 404)));
    const res = await detailGET(
      new Request('http://console.local/api/erp/notifications/ntf-unknown'),
      { params: Promise.resolve({ id: 'ntf-unknown' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('NOTIFICATION_NOT_FOUND');
  });
});

// ===========================================================================
// POST /api/erp/notifications/{id}/read — idempotent mark-read.
// ===========================================================================

describe('POST /api/erp/notifications/{id}/read', () => {
  it('POST only; no body sent to upstream; no Idempotency-Key; returns { data }', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(READ_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const res = await readPOST(
      new Request('http://console.local/api/erp/notifications/ntf-1/read', {
        method: 'POST',
      }),
      { params: Promise.resolve({ id: 'ntf-1' }) },
    );
    expect(res.status).toBe(200);
    const b = await res.json();
    expect(b.data.id).toBe('ntf-1');
    expect(b.data.read).toBe(true);
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
    const h = init.headers as Record<string, string>;
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Content-Type']).toBeUndefined();
  });

  it('404 NOTIFICATION_NOT_FOUND passthrough on mark-read (foreign/unknown id)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('NOTIFICATION_NOT_FOUND', 404)));
    const res = await readPOST(
      new Request('http://console.local/api/erp/notifications/ntf-unknown/read', {
        method: 'POST',
      }),
      { params: Promise.resolve({ id: 'ntf-unknown' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('NOTIFICATION_NOT_FOUND');
  });

  it('ErpUnavailableError (503) → 503 response', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('SERVICE_UNAVAILABLE', 503)));
    const res = await readPOST(
      new Request('http://console.local/api/erp/notifications/ntf-1/read', {
        method: 'POST',
      }),
      { params: Promise.resolve({ id: 'ntf-1' }) },
    );
    expect(res.status).toBe(503);
  });
});
