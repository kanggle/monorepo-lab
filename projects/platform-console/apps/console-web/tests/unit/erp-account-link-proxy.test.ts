import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-318 — same-origin erp employee ↔ IAM account link proxy routes
 * (`TASK-MONO-774` S4; producer `masterdata-api.md` § Employee ↔ IAM account
 * link; console contract § 2.4.8 «Employee ↔ IAM account link binding»):
 *
 *   - employees/{id}/account-link-proposals        GET (history) + POST (propose)
 *   - employees/{id}/account-link/unlink           POST
 *   - account-link-proposals/mine                  GET
 *   - account-link-proposals/{proposalId}/{action} POST (accept|decline|revoke)
 *
 * Asserts: the domain-facing IAM token (never the operator token, no
 * X-Tenant-Id); Idempotency-Key forwarded on every write and stripped from the
 * body; reason-required pre-guards (revoke / unlink) and the action allow-list
 * refuse with no upstream call; the producer's refusals pass through with
 * their code AND `details` (cause-specific copy needs `details.cause`); 503
 * stays 503. AC-2 — propose → accept by ANOTHER account succeeds through the
 * BFF; the proposer's own accept ends in `EMPLOYEE_LINK_SELF_ACCEPT`.
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
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
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

import * as historyRoute from '@/app/api/erp/masterdata/employees/[id]/account-link-proposals/route';
import * as unlinkRoute from '@/app/api/erp/masterdata/employees/[id]/account-link/unlink/route';
import * as mineRoute from '@/app/api/erp/masterdata/account-link-proposals/mine/route';
import * as actionRoute from '@/app/api/erp/masterdata/account-link-proposals/[proposalId]/[action]/route';
import { POST as transitionPOST } from '@/app/api/erp/approval/requests/[id]/[transition]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function erpError(code: string, status: number, details?: unknown) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't', ...(details ? { details } : {}) }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const PROPOSAL = {
  id: 'prop-1',
  employeeId: 'emp-1',
  accountId: 'acc-B',
  status: 'PENDING',
  proposedBy: 'acc-A',
  proposedAt: '2026-10-07T00:00:00Z',
};
const EMPLOYEE_LINKED = {
  data: {
    id: 'emp-1',
    employeeNumber: 'E-1',
    name: '김연결',
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    accountId: 'acc-B',
  },
  meta: { timestamp: 'x' },
};
const PAGE = { data: [PROPOSAL], meta: { page: 0, size: 20, totalElements: 1 } };

function post(url: string, body: unknown) {
  return new Request(url, { method: 'POST', body: JSON.stringify(body) });
}
const idParams = (id: string) => ({ params: Promise.resolve({ id }) });
const actionParams = (proposalId: string, action: string) => ({
  params: Promise.resolve({ proposalId, action }),
});

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('account-link proxy — method exposure', () => {
  it('history route: GET + POST only', () => {
    expect(typeof historyRoute.GET).toBe('function');
    expect(typeof historyRoute.POST).toBe('function');
    expect((historyRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((historyRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
  it('unlink + action routes: POST only; mine: GET only', () => {
    expect(typeof unlinkRoute.POST).toBe('function');
    expect((unlinkRoute as Record<string, unknown>).GET).toBeUndefined();
    expect(typeof actionRoute.POST).toBe('function');
    expect((actionRoute as Record<string, unknown>).GET).toBeUndefined();
    expect(typeof mineRoute.GET).toBe('function');
    expect((mineRoute as Record<string, unknown>).POST).toBeUndefined();
  });
});

describe('reads', () => {
  it('GET mine → upstream /account-link-proposals/mine with the IAM access token (never the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAGE));
    vi.stubGlobal('fetch', fetchMock);
    const res = await mineRoute.GET(
      new Request('http://console.local/api/erp/masterdata/account-link-proposals/mine?page=0&size=20'),
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    const u = new URL(String(url));
    expect(u.pathname).toBe('/api/erp/masterdata/account-link-proposals/mine');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect((await res.json()).data[0].id).toBe('prop-1');
  });

  it('GET history → upstream /employees/{id}/account-link-proposals', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAGE));
    vi.stubGlobal('fetch', fetchMock);
    const res = await historyRoute.GET(
      new Request('http://console.local/api/erp/masterdata/employees/emp-1/account-link-proposals'),
      idParams('emp-1'),
    );
    expect(res.status).toBe(200);
    expect(new URL(String(fetchMock.mock.calls[0][0])).pathname).toBe(
      '/api/erp/masterdata/employees/emp-1/account-link-proposals',
    );
  });
});

describe('propose', () => {
  it('forwards Idempotency-Key + { accountId, reason } (key stripped from the body); 201', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ data: PROPOSAL }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await historyRoute.POST(
      post('http://console.local/x', {
        accountId: ' acc-B ',
        reason: '입사',
        idempotencyKey: 'idem-p',
      }),
      idParams('emp-1'),
    );
    expect(res.status).toBe(201);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://erp.local/api/erp/masterdata/employees/emp-1/account-link-proposals');
    expect((init as RequestInit).method).toBe('POST');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['Idempotency-Key']).toBe('idem-p');
    expect(h['X-Operator-Reason']).toBeUndefined();
    const body = JSON.parse(String((init as RequestInit).body));
    expect(body).toEqual({ accountId: 'acc-B', reason: '입사' });
    expect((await res.json()).data.status).toBe('PENDING');
  });

  it.each([
    ['blank accountId', { accountId: '   ', idempotencyKey: 'k' }],
    ['accountId over 64', { accountId: 'a'.repeat(65), idempotencyKey: 'k' }],
    ['no idempotency key', { accountId: 'acc-B' }],
  ])('%s → 400 VALIDATION_ERROR, no upstream call', async (_n, body) => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await historyRoute.POST(post('http://console.local/x', body), idParams('emp-1'));
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('409 EMPLOYEE_LINK_CONFLICT passes through WITH details.cause', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        erpError('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'proposal_pending' }),
      ),
    );
    const res = await historyRoute.POST(
      post('http://console.local/x', { accountId: 'acc-B', idempotencyKey: 'k' }),
      idParams('emp-1'),
    );
    expect(res.status).toBe(409);
    expect(await res.json()).toEqual({
      code: 'EMPLOYEE_LINK_CONFLICT',
      message: 'e',
      details: { cause: 'proposal_pending' },
    });
  });
});

describe('AC-2 — propose → accept by another account; the proposer cannot accept', () => {
  it('propose with token A, accept with token B → 200 employee with accountId', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ data: PROPOSAL }, 201))
      .mockResolvedValueOnce(jsonResponse(EMPLOYEE_LINKED));
    vi.stubGlobal('fetch', fetchMock);

    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    const proposed = await historyRoute.POST(
      post('http://console.local/x', { accountId: 'acc-B', idempotencyKey: 'k1' }),
      idParams('emp-1'),
    );
    expect(proposed.status).toBe(201);

    cookieJar.set(ACCESS_COOKIE, 'TOKEN-B');
    const accepted = await actionRoute.POST(
      post('http://console.local/x', { idempotencyKey: 'k2' }),
      actionParams('prop-1', 'accept'),
    );
    expect(accepted.status).toBe(200);
    expect((await accepted.json()).data.accountId).toBe('acc-B');

    const [acceptUrl, acceptInit] = fetchMock.mock.calls[1];
    expect(String(acceptUrl)).toBe(
      'http://erp.local/api/erp/masterdata/account-link-proposals/prop-1/accept',
    );
    const h = (acceptInit as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer TOKEN-B');
    expect(h['Idempotency-Key']).toBe('k2');
    expect(JSON.parse(String((acceptInit as RequestInit).body))).toEqual({});
  });

  it('the proposer accepting their own proposal → 403 EMPLOYEE_LINK_SELF_ACCEPT passes through (code kept)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('EMPLOYEE_LINK_SELF_ACCEPT', 403)));
    const res = await actionRoute.POST(
      post('http://console.local/x', { idempotencyKey: 'k3' }),
      actionParams('prop-1', 'accept'),
    );
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('EMPLOYEE_LINK_SELF_ACCEPT');
  });

  it('a non-addressee accepting → 403 EMPLOYEE_LINK_NOT_ADDRESSEE passes through', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-C');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('EMPLOYEE_LINK_NOT_ADDRESSEE', 403)));
    const res = await actionRoute.POST(
      post('http://console.local/x', { idempotencyKey: 'k4' }),
      actionParams('prop-1', 'accept'),
    );
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('EMPLOYEE_LINK_NOT_ADDRESSEE');
  });
});

describe('decline / revoke / unknown action', () => {
  it('decline: reason optional, forwarded in the body', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-B');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ data: { ...PROPOSAL, status: 'DECLINED' } }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await actionRoute.POST(
      post('http://console.local/x', { idempotencyKey: 'k' }),
      actionParams('prop-1', 'decline'),
    );
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toContain('/prop-1/decline');
    expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body))).toEqual({});
  });

  it('revoke without a reason → 400, no upstream call; with a reason → forwarded', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ data: { ...PROPOSAL, status: 'REVOKED' } }));
    vi.stubGlobal('fetch', fetchMock);
    const bad = await actionRoute.POST(
      post('http://console.local/x', { idempotencyKey: 'k' }),
      actionParams('prop-1', 'revoke'),
    );
    expect(bad.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
    const ok = await actionRoute.POST(
      post('http://console.local/x', { reason: '잘못 제안', idempotencyKey: 'k' }),
      actionParams('prop-1', 'revoke'),
    );
    expect(ok.status).toBe(200);
    expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body))).toEqual({
      reason: '잘못 제안',
    });
  });

  it('unknown action → 404, no upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await actionRoute.POST(
      post('http://console.local/x', { idempotencyKey: 'k' }),
      actionParams('prop-1', 'approve'),
    );
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('unlink', () => {
  it('reason required → 400 without; 409 not_linked passes through with details', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(erpError('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'not_linked' }));
    vi.stubGlobal('fetch', fetchMock);
    const bad = await unlinkRoute.POST(
      post('http://console.local/x', { reason: '  ', idempotencyKey: 'k' }),
      idParams('emp-1'),
    );
    expect(bad.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
    const res = await unlinkRoute.POST(
      post('http://console.local/x', { reason: '퇴사', idempotencyKey: 'k' }),
      idParams('emp-1'),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).details).toEqual({ cause: 'not_linked' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://erp.local/api/erp/masterdata/employees/emp-1/account-link/unlink');
    expect(((init as RequestInit).headers as Record<string, string>)['Idempotency-Key']).toBe('k');
  });

  it('503 stays 503 (outage, never reworded as a data problem)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('SERVICE_UNAVAILABLE', 503)));
    const res = await unlinkRoute.POST(
      post('http://console.local/x', { reason: '퇴사', idempotencyKey: 'k' }),
      idParams('emp-1'),
    );
    expect(res.status).toBe(503);
  });
});

describe('approval v2.4 refusal details reach the client (shared passthrough)', () => {
  it('submit 422 APPROVAL_APPROVER_UNLINKED keeps details.stageIndex through the proxy', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('APPROVAL_APPROVER_UNLINKED', 422, { stageIndex: 1 })),
    );
    const res = await transitionPOST(
      post('http://console.local/x', { idempotencyKey: 'k' }),
      { params: Promise.resolve({ id: 'appr-1', transition: 'submit' }) },
    );
    expect(res.status).toBe(422);
    expect(await res.json()).toEqual({
      code: 'APPROVAL_APPROVER_UNLINKED',
      message: 'e',
      details: { stageIndex: 1 },
    });
  });

  it('an error WITHOUT details keeps the old body shape (no `details` key)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'TOKEN-A');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('APPROVAL_ALREADY_FINALIZED', 409)));
    const res = await transitionPOST(
      post('http://console.local/x', { idempotencyKey: 'k' }),
      { params: Promise.resolve({ id: 'appr-1', transition: 'submit' }) },
    );
    expect(await res.json()).toEqual({ code: 'APPROVAL_ALREADY_FINALIZED', message: 'e' });
  });
});
