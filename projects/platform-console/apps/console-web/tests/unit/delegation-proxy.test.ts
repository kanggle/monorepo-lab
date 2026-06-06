import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin erp delegation proxy route handlers (TASK-PC-FE-054):
 *   - delegations: GET (list ?role=) + POST (create) only
 *   - delegations/{id}/revoke: POST only
 *   server-only domain-facing IAM token; Idempotency-Key forwarded;
 *   reason required on revoke (pre-guard); error mapping via shared erp
 *   `_proxy` mapper.
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
  GET as delegationsGET,
  POST as delegationsPOST,
} from '@/app/api/erp/approval/delegations/route';
import { POST as revokePOST } from '@/app/api/erp/approval/delegations/[id]/revoke/route';
import * as delegationsRoute from '@/app/api/erp/approval/delegations/route';
import * as revokeRoute from '@/app/api/erp/approval/delegations/[id]/revoke/route';
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

const GRANT = {
  id: 'del-1',
  delegatorId: 'emp-delegator',
  delegateId: 'emp-delegate',
  validFrom: '2026-06-05T00:00:00Z',
  status: 'ACTIVE',
  createdAt: '2026-06-05T00:00:00Z',
  createdBy: 'emp-delegator',
};
const LIST = {
  data: [GRANT],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

// ===========================================================================
// method exposure — each route exposes ONLY its allowed methods.
// ===========================================================================

describe('delegation proxy — method exposure', () => {
  it('delegations route: GET + POST only (no PUT/PATCH/DELETE)', () => {
    expect(typeof delegationsRoute.GET).toBe('function');
    expect(typeof delegationsRoute.POST).toBe('function');
    expect((delegationsRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((delegationsRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((delegationsRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });

  it('revoke route: POST only (no GET/PUT/PATCH/DELETE)', () => {
    expect(typeof revokeRoute.POST).toBe('function');
    expect((revokeRoute as Record<string, unknown>).GET).toBeUndefined();
    expect((revokeRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((revokeRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((revokeRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
});

// ===========================================================================
// GET /api/erp/approval/delegations
// ===========================================================================

describe('GET /api/erp/approval/delegations', () => {
  it('domain-facing IAM token (NOT operator token); no X-Tenant-Id; forwards role param', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST));
    vi.stubGlobal('fetch', fetchMock);
    const res = await delegationsGET(
      new Request(
        'http://console.local/api/erp/approval/delegations?role=DELEGATOR',
      ),
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    expect(u.searchParams.get('role')).toBe('DELEGATOR');
  });

  it('no IAM session → 401, no upstream call', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await delegationsGET(
      new Request('http://console.local/api/erp/approval/delegations'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no ?role= param: upstream URL has no role param', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST));
    vi.stubGlobal('fetch', fetchMock);
    await delegationsGET(
      new Request('http://console.local/api/erp/approval/delegations'),
    );
    const [url] = fetchMock.mock.calls[0];
    const u = new URL(String(url));
    expect(u.searchParams.get('role')).toBeNull();
  });
});

// ===========================================================================
// POST /api/erp/approval/delegations — create.
// ===========================================================================

describe('POST /api/erp/approval/delegations', () => {
  it('forwards delegateId + validFrom + Idempotency-Key; strips idempotencyKey from body; 201', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(GRANT, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await delegationsPOST(
      new Request('http://console.local/api/erp/approval/delegations', {
        method: 'POST',
        body: JSON.stringify({
          delegateId: 'emp-delegate',
          validFrom: '2026-06-05T00:00:00Z',
          idempotencyKey: 'idem-1',
        }),
      }),
    );
    expect(res.status).toBe(201);
    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toBe('http://erp.local/api/erp/approval/delegations');
    expect(h['Idempotency-Key']).toBe('idem-1');
    expect(h['X-Operator-Reason']).toBeUndefined();
    const body = JSON.parse(String((init as RequestInit).body));
    expect(body.delegateId).toBe('emp-delegate');
    expect(body.validFrom).toBe('2026-06-05T00:00:00Z');
    // idempotencyKey stripped from the forwarded body.
    expect(body.idempotencyKey).toBeUndefined();
  });

  it('forwards optional validTo + reason when present', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(GRANT, 201));
    vi.stubGlobal('fetch', fetchMock);
    await delegationsPOST(
      new Request('http://console.local/api/erp/approval/delegations', {
        method: 'POST',
        body: JSON.stringify({
          delegateId: 'emp-delegate',
          validFrom: '2026-06-05T00:00:00Z',
          validTo: '2026-06-30T23:59:59Z',
          reason: '출장',
          idempotencyKey: 'idem-2',
        }),
      }),
    );
    const [, init] = fetchMock.mock.calls[0];
    const body = JSON.parse(String((init as RequestInit).body));
    expect(body.validTo).toBe('2026-06-30T23:59:59Z');
    expect(body.reason).toBe('출장');
  });

  it('invalid body (missing required fields) → 400, no upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await delegationsPOST(
      new Request('http://console.local/api/erp/approval/delegations', {
        method: 'POST',
        body: JSON.stringify({ reason: 'missing delegateId + validFrom + key' }),
      }),
    );
    expect(res.status).toBe(400);
    const b = await res.json();
    expect(b.code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('producer 422 DELEGATION_INVALID passes through (self-delegation / invalid period)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('DELEGATION_INVALID', 422)),
    );
    const res = await delegationsPOST(
      new Request('http://console.local/api/erp/approval/delegations', {
        method: 'POST',
        body: JSON.stringify({
          delegateId: 'emp-self',
          validFrom: '2026-06-05T00:00:00Z',
          idempotencyKey: 'k',
        }),
      }),
    );
    expect(res.status).toBe(422);
    const b = await res.json();
    expect(b.code).toBe('DELEGATION_INVALID');
  });
});

// ===========================================================================
// POST /api/erp/approval/delegations/{id}/revoke
// ===========================================================================

describe('POST /api/erp/approval/delegations/{id}/revoke', () => {
  function revokeReq(id: string, body: unknown) {
    return revokePOST(
      new Request(
        `http://console.local/api/erp/approval/delegations/${id}/revoke`,
        { method: 'POST', body: JSON.stringify(body) },
      ),
      { params: Promise.resolve({ id }) },
    );
  }

  it('forwards reason + Idempotency-Key to the upstream revoke endpoint; no X-Operator-Reason', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ ...GRANT, status: 'REVOKED' }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await revokeReq('del-1', {
      reason: '귀국',
      idempotencyKey: 'idem-revoke',
    });
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toBe(
      'http://erp.local/api/erp/approval/delegations/del-1/revoke',
    );
    expect(h['Idempotency-Key']).toBe('idem-revoke');
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Operator-Reason']).toBeUndefined();
    const body = JSON.parse(String((init as RequestInit).body));
    expect(body.reason).toBe('귀국');
  });

  it('missing reason → 400 VALIDATION_ERROR, no upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await revokeReq('del-1', { idempotencyKey: 'k' });
    expect(res.status).toBe(400);
    const b = await res.json();
    expect(b.code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('404 DELEGATION_NOT_FOUND passes through inline-actionably', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('DELEGATION_NOT_FOUND', 404)),
    );
    const res = await revokeReq('nope', { reason: 'r', idempotencyKey: 'k' });
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('DELEGATION_NOT_FOUND');
  });

  it('empty body → 400, no upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await revokeReq('del-1', {});
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
