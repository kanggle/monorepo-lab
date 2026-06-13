import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin scm-replenishment proxy route handlers (TASK-PC-FE-077 —
 * § 2.4.6.1):
 *   - reads (suggestions list / detail): domain-facing IAM OIDC token attached
 *     server-side (NOT the operator token); no X-Tenant-Id; no mutation
 *     artifacts.
 *   - approve / dismiss POST: optional note/reason in the BODY; NO
 *     Idempotency-Key + NO X-Operator-Reason (asserted absent).
 *   - 401 → 401 (no upstream call when the IAM session is absent).
 *   - 403 → 403; 422 SKU_SUPPLIER_UNMAPPED / INVALID_SUGGESTION_STATE → 422;
 *     409 SUGGESTION_ALREADY_MATERIALIZED → 409; 503 → 503; 429 → 429 +
 *     Retry-After.
 *   - NO /submit|/confirm|/cancel procurement route exists here.
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as listGET } from '@/app/api/scm/demand-planning/suggestions/route';
import { GET as detailGET } from '@/app/api/scm/demand-planning/suggestions/[id]/route';
import { POST as approvePOST } from '@/app/api/scm/demand-planning/suggestions/[id]/approve/route';
import { POST as dismissPOST } from '@/app/api/scm/demand-planning/suggestions/[id]/dismiss/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function scmError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const SUGGESTIONS_ENV = {
  data: [
    {
      id: 's-1',
      skuCode: 'SKU-1',
      status: 'SUGGESTED',
      source: 'ALERT',
      triggerAvailableQty: 5,
      suggestedQty: 100,
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
};
const APPROVE_ENV = {
  data: { id: 's-1', status: 'MATERIALIZED', poId: 'po-1', poStatus: 'DRAFT' },
  meta: {},
};
const DISMISS_ENV = { data: { id: 's-1', status: 'DISMISSED' }, meta: {} };

function actionReq(path: string, body?: unknown) {
  return new Request(`http://console.local${path}`, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/scm/demand-planning/suggestions (list) proxy', () => {
  it('attaches the domain-facing IAM OIDC token (NOT the operator token), forwards filters, no mutation artifacts', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUGGESTIONS_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await listGET(
      new Request(
        'http://console.local/api/scm/demand-planning/suggestions?status=SUGGESTED&skuCode=SKU-1&size=999',
      ),
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    const upstream = new URL(String(url));
    expect(upstream.searchParams.get('status')).toBe('SUGGESTED');
    expect(upstream.searchParams.get('skuCode')).toBe('SKU-1');
    expect(upstream.searchParams.get('size')).toBe('100'); // capped
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await listGET(
      new Request('http://console.local/api/scm/demand-planning/suggestions'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await listGET(
      new Request('http://console.local/api/scm/demand-planning/suggestions'),
    );
    expect(res.status).toBe(503);
  });

  it('persisting 429 → 429 + Retry-After (bounded; client must not re-storm)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
          {
            status: 429,
            headers: {
              'Content-Type': 'application/json',
              'Retry-After': '1',
            },
          },
        ),
      ),
    );
    const res = await listGET(
      new Request('http://console.local/api/scm/demand-planning/suggestions'),
    );
    expect(res.status).toBe(429);
    expect(res.headers.get('Retry-After')).toBe('1');
  });
});

describe('GET /api/scm/demand-planning/suggestions/{id} (detail) proxy', () => {
  it('forwards the detail read with the IAM OIDC token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ data: { id: 's-1', status: 'SUGGESTED' }, meta: {} }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await detailGET(
      new Request('http://console.local/api/scm/demand-planning/suggestions/s-1'),
      { params: Promise.resolve({ id: 's-1' }) },
    );
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toContain(
      '/api/v1/demand-planning/suggestions/s-1',
    );
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('GET');
  });

  it('404 SUGGESTION_NOT_FOUND → 404 inline', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SUGGESTION_NOT_FOUND', 404)),
    );
    const res = await detailGET(
      new Request('http://console.local/api/scm/demand-planning/suggestions/nope'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('SUGGESTION_NOT_FOUND');
  });
});

describe('POST /api/scm/demand-planning/suggestions/{id}/approve proxy', () => {
  it('forwards the OPTIONAL note in the BODY + NO Idempotency-Key + NO X-Operator-Reason; surfaces DRAFT poId/poStatus', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(APPROVE_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await approvePOST(
      actionReq('/api/scm/demand-planning/suggestions/s-1/approve', {
        note: '보충 승인',
      }),
      { params: Promise.resolve({ id: 's-1' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.poId).toBe('po-1');
    expect(body.poStatus).toBe('DRAFT');

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toContain('/suggestions/s-1/approve');
    expect(String(url)).not.toMatch(/\/submit|\/confirm|\/cancel/);
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      note: '보충 승인',
    });
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('approve with NO body forwards NO upstream body (note is OPTIONAL)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(APPROVE_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await approvePOST(
      actionReq('/api/scm/demand-planning/suggestions/s-1/approve'),
      { params: Promise.resolve({ id: 's-1' }) },
    );
    expect(res.status).toBe(200);
    expect((fetchMock.mock.calls[0][1] as RequestInit).body).toBeUndefined();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await approvePOST(
      actionReq('/api/scm/demand-planning/suggestions/s-1/approve', {}),
      { params: Promise.resolve({ id: 's-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('422 SKU_SUPPLIER_UNMAPPED → 422 passthrough (inline; no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SKU_SUPPLIER_UNMAPPED', 422)),
    );
    const res = await approvePOST(
      actionReq('/api/scm/demand-planning/suggestions/s-1/approve', {}),
      { params: Promise.resolve({ id: 's-1' }) },
    );
    expect(res.status).toBe(422);
    const b = await res.json();
    expect(b.code).toBe('SKU_SUPPLIER_UNMAPPED');
  });

  it('409 SUGGESTION_ALREADY_MATERIALIZED → 409 passthrough (benign)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SUGGESTION_ALREADY_MATERIALIZED', 409)),
    );
    const res = await approvePOST(
      actionReq('/api/scm/demand-planning/suggestions/s-1/approve', {}),
      { params: Promise.resolve({ id: 's-1' }) },
    );
    expect(res.status).toBe(409);
  });
});

describe('POST /api/scm/demand-planning/suggestions/{id}/dismiss proxy', () => {
  it('forwards the OPTIONAL reason in the BODY + NO Idempotency-Key + NO X-Operator-Reason', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DISMISS_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await dismissPOST(
      actionReq('/api/scm/demand-planning/suggestions/s-1/dismiss', {
        reason: '중복',
      }),
      { params: Promise.resolve({ id: 's-1' }) },
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toContain('/suggestions/s-1/dismiss');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      reason: '중복',
    });
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('422 INVALID_SUGGESTION_STATE (dismiss a MATERIALIZED one) → 422 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('INVALID_SUGGESTION_STATE', 422)),
    );
    const res = await dismissPOST(
      actionReq('/api/scm/demand-planning/suggestions/s-1/dismiss', {}),
      { params: Promise.resolve({ id: 's-1' }) },
    );
    expect(res.status).toBe(422);
    const b = await res.json();
    expect(b.code).toBe('INVALID_SUGGESTION_STATE');
  });
});
