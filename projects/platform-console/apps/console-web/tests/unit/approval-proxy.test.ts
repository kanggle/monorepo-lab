import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin erp approval proxy route handlers (TASK-PC-FE-051):
 *   - requests: GET (list) + POST (create) only
 *   - requests/{id}: GET (detail) only
 *   - requests/{id}/{transition}: POST only (transition allow-list)
 *   - inbox: GET only
 *   server-only domain-facing GAP token; Idempotency-Key + X-Operator-Reason
 *   forwarded; reject/withdraw reason-required pre-guard; error mapping via
 *   the shared erp `_proxy` mapper.
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

import {
  GET as requestsGET,
  POST as requestsPOST,
} from '@/app/api/erp/approval/requests/route';
import { GET as detailGET } from '@/app/api/erp/approval/requests/[id]/route';
import { POST as transitionPOST } from '@/app/api/erp/approval/requests/[id]/[transition]/route';
import { GET as inboxGET } from '@/app/api/erp/approval/inbox/route';
import * as requestsRoute from '@/app/api/erp/approval/requests/route';
import * as detailRoute from '@/app/api/erp/approval/requests/[id]/route';
import * as transitionRoute from '@/app/api/erp/approval/requests/[id]/[transition]/route';
import * as inboxRoute from '@/app/api/erp/approval/inbox/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

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

const DETAIL = {
  data: {
    id: 'appr-1',
    status: 'DRAFT',
    subjectType: 'DEPARTMENT',
    subjectId: 'dept-1',
    title: 'T',
    approverId: 'emp-a',
    submitterId: 'emp-s',
    history: [],
    createdAt: '2026-06-05T00:00:00Z',
  },
  meta: { timestamp: 'x' },
};
const LIST = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

// ===========================================================================
// method exposure — each route exposes ONLY its allowed methods.
// ===========================================================================

describe('approval proxy — method exposure (GET/POST per route; no PUT/PATCH/DELETE)', () => {
  it('requests route: GET + POST only', () => {
    expect(typeof requestsRoute.GET).toBe('function');
    expect(typeof requestsRoute.POST).toBe('function');
    expect((requestsRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((requestsRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((requestsRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
  it('detail route: GET only (no POST/PUT/PATCH/DELETE)', () => {
    expect(typeof detailRoute.GET).toBe('function');
    expect((detailRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((detailRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((detailRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((detailRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
  it('transition route: POST only (no GET/PUT/PATCH/DELETE)', () => {
    expect(typeof transitionRoute.POST).toBe('function');
    expect((transitionRoute as Record<string, unknown>).GET).toBeUndefined();
    expect((transitionRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((transitionRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((transitionRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
  it('inbox route: GET only (no POST/PUT/PATCH/DELETE)', () => {
    expect(typeof inboxRoute.GET).toBe('function');
    expect((inboxRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((inboxRoute as Record<string, unknown>).PUT).toBeUndefined();
  });
});

// ===========================================================================
// requests list / create.
// ===========================================================================

describe('GET/POST /api/erp/approval/requests', () => {
  it('GET list: domain-facing GAP token (NOT operator token); forwards status/role', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST));
    vi.stubGlobal('fetch', fetchMock);
    const res = await requestsGET(
      new Request('http://console.local/api/erp/approval/requests?status=SUBMITTED&role=APPROVER'),
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    expect(u.searchParams.get('status')).toBe('SUBMITTED');
    expect(u.searchParams.get('role')).toBe('APPROVER');
  });

  it('GET list: no GAP session → 401, no upstream call', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await requestsGET(
      new Request('http://console.local/api/erp/approval/requests'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('POST create (legacy approverId): forwards Idempotency-Key + body (idempotencyKey stripped); 201', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await requestsPOST(
      new Request('http://console.local/api/erp/approval/requests', {
        method: 'POST',
        body: JSON.stringify({
          subjectType: 'DEPARTMENT',
          subjectId: 'dept-1',
          title: 'T',
          approverId: 'emp-a',
          idempotencyKey: 'idem-1',
        }),
      }),
    );
    expect(res.status).toBe(201);
    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toBe('http://erp.local/api/erp/approval/requests');
    expect(h['Idempotency-Key']).toBe('idem-1');
    const body = JSON.parse(String((init as RequestInit).body));
    expect(body).toMatchObject({ subjectType: 'DEPARTMENT', subjectId: 'dept-1', approverId: 'emp-a' });
    expect(body.idempotencyKey).toBeUndefined();
    expect(body.approverIds).toBeUndefined();
  });

  it('POST create (multi-stage approverIds): accepts approverIds and forwards verbatim; 201', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await requestsPOST(
      new Request('http://console.local/api/erp/approval/requests', {
        method: 'POST',
        body: JSON.stringify({
          subjectType: 'DEPARTMENT',
          subjectId: 'dept-1',
          title: 'Multi',
          approverIds: ['emp-a', 'emp-b'],
          idempotencyKey: 'idem-multi',
        }),
      }),
    );
    expect(res.status).toBe(201);
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(String((init as RequestInit).body));
    // approverIds forwarded; approverId absent; idempotencyKey stripped.
    expect(body.approverIds).toEqual(['emp-a', 'emp-b']);
    expect(body.approverId).toBeUndefined();
    expect(body.idempotencyKey).toBeUndefined();
  });

  it('POST create: neither approverId nor approverIds → 400 VALIDATION_ERROR', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await requestsPOST(
      new Request('http://console.local/api/erp/approval/requests', {
        method: 'POST',
        body: JSON.stringify({
          subjectType: 'DEPARTMENT',
          subjectId: 'dept-1',
          title: 'T',
          idempotencyKey: 'idem-x',
          // Neither approverId nor approverIds provided.
        }),
      }),
    );
    expect(res.status).toBe(400);
    const b = await res.json();
    expect(b.code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('POST create: BOTH approverId AND approverIds → 400 VALIDATION_ERROR', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await requestsPOST(
      new Request('http://console.local/api/erp/approval/requests', {
        method: 'POST',
        body: JSON.stringify({
          subjectType: 'DEPARTMENT',
          subjectId: 'dept-1',
          title: 'T',
          approverId: 'emp-a',
          approverIds: ['emp-a', 'emp-b'],
          idempotencyKey: 'idem-x',
        }),
      }),
    );
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('POST create: invalid body (missing required fields) → 400, no upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await requestsPOST(
      new Request('http://console.local/api/erp/approval/requests', {
        method: 'POST',
        body: JSON.stringify({ title: 'missing fields' }),
      }),
    );
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// detail.
// ===========================================================================

describe('GET /api/erp/approval/requests/{id}', () => {
  it('returns { data } and forwards no idempotency / reason', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const res = await detailGET(
      new Request('http://console.local/api/erp/approval/requests/appr-1'),
      { params: Promise.resolve({ id: 'appr-1' }) },
    );
    expect(res.status).toBe(200);
    const b = await res.json();
    expect(b.data.id).toBe('appr-1');
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('GET');
    const h = init.headers as Record<string, string>;
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('404 APPROVAL_REQUEST_NOT_FOUND → 404 inline actionable', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_REQUEST_NOT_FOUND', 404)));
    const res = await detailGET(
      new Request('http://console.local/api/erp/approval/requests/nope'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('APPROVAL_REQUEST_NOT_FOUND');
  });
});

// ===========================================================================
// transitions — allow-list, reason-required, idempotency, error mapping.
// ===========================================================================

function transitionReq(transition: string, body: unknown) {
  return transitionPOST(
    new Request(
      `http://console.local/api/erp/approval/requests/appr-1/${transition}`,
      { method: 'POST', body: JSON.stringify(body) },
    ),
    { params: Promise.resolve({ id: 'appr-1', transition }) },
  );
}

describe('POST /api/erp/approval/requests/{id}/{transition}', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
  });

  it('submit → upstream POST .../submit + Idempotency-Key (no reason)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const res = await transitionReq('submit', { idempotencyKey: 'idem-s' });
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toBe('http://erp.local/api/erp/approval/requests/appr-1/submit');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['Idempotency-Key']).toBe('idem-s');
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
  });

  it('approve → upstream POST .../approve', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    await transitionReq('approve', { idempotencyKey: 'idem-a' });
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://erp.local/api/erp/approval/requests/appr-1/approve',
    );
  });

  it('reject WITHOUT reason → 400, no upstream call (reason-required pre-guard)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await transitionReq('reject', { idempotencyKey: 'k' });
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('reject WITH reason → upstream POST .../reject + X-Operator-Reason + body reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    await transitionReq('reject', { reason: '근거 부족', idempotencyKey: 'idem-r' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://erp.local/api/erp/approval/requests/appr-1/reject');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBe('근거 부족');
    expect(JSON.parse(String((init as RequestInit).body)).reason).toBe('근거 부족');
  });

  it('withdraw WITHOUT reason → 400; WITH reason → forwarded', async () => {
    let fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    let res = await transitionReq('withdraw', { idempotencyKey: 'k' });
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();

    fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    res = await transitionReq('withdraw', { reason: '수정', idempotencyKey: 'idem-w' });
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://erp.local/api/erp/approval/requests/appr-1/withdraw',
    );
  });

  it('unknown transition segment → 404, no upstream call', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await transitionReq('frobnicate', { idempotencyKey: 'k' });
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 APPROVAL_NOT_AUTHORIZED_APPROVER maps through (console never pre-judges approver authority)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_NOT_AUTHORIZED_APPROVER', 403)));
    const res = await transitionReq('approve', { idempotencyKey: 'k' });
    expect(res.status).toBe(403);
    const b = await res.json();
    expect(b.code).toBe('APPROVAL_NOT_AUTHORIZED_APPROVER');
  });

  it('409 APPROVAL_ALREADY_FINALIZED maps through', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_ALREADY_FINALIZED', 409)));
    const res = await transitionReq('approve', { idempotencyKey: 'k' });
    expect(res.status).toBe(409);
  });

  it('422 APPROVAL_ROUTE_INVALID maps through (self-approval / unresolved subject at submit)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_ROUTE_INVALID', 422)));
    const res = await transitionReq('submit', { idempotencyKey: 'k' });
    expect(res.status).toBe(422);
  });
});

// ===========================================================================
// inbox.
// ===========================================================================

describe('GET /api/erp/approval/inbox', () => {
  it('GET only; forwards page/size; domain-facing token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST));
    vi.stubGlobal('fetch', fetchMock);
    const res = await inboxGET(
      new Request('http://console.local/api/erp/approval/inbox?page=1&size=10'),
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    expect(u.pathname).toBe('/api/erp/approval/inbox');
    expect(u.searchParams.get('page')).toBe('1');
    expect(u.searchParams.get('size')).toBe('10');
  });
});
