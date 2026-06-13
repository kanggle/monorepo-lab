import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin scm-config (seed) proxy route handlers (TASK-PC-FE-080 —
 * § 2.4.6.2):
 *   - GET/PUT policies + sku-supplier-map: domain-facing IAM OIDC token attached
 *     server-side (NOT the operator token); no X-Tenant-Id;
 *   - PUT upsert: the FULL-row body; NO Idempotency-Key + NO X-Operator-Reason
 *     (asserted absent);
 *   - GET 404 (POLICY_NOT_FOUND / MAPPING_NOT_FOUND) → 200 `{ found: false }`
 *     (not configured yet → create), NOT a 404 error toast;
 *   - 401 → 401 (no upstream call when the IAM session is absent);
 *   - 403 → 403; 422 VALIDATION_ERROR → 422; 503 → 503; 429 → 429 + Retry-After;
 *   - NO suggestion/approve/dismiss/submit route exists here.
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import {
  GET as policyGET,
  PUT as policyPUT,
} from '@/app/api/scm/demand-planning/policies/[skuCode]/route';
import {
  GET as mapGET,
  PUT as mapPUT,
} from '@/app/api/scm/demand-planning/sku-supplier-map/[skuCode]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function scmError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const POLICY_ENV = {
  data: { reorderPoint: 10, safetyStock: 5, reorderQty: 100 },
  meta: {},
};
const MAP_ENV = {
  data: {
    supplierId: 'sup-1',
    defaultOrderQty: 100,
    leadTimeDays: 7,
    currency: 'KRW',
  },
  meta: {},
};
const POLICY_INPUT = { reorderPoint: 10, safetyStock: 5, reorderQty: 100 };
const MAP_INPUT = {
  supplierId: 'sup-1',
  defaultOrderQty: 100,
  leadTimeDays: 7,
  currency: 'KRW',
};

function putReq(path: string, body: unknown) {
  return new Request(`http://console.local${path}`, {
    method: 'PUT',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
  });
}
function getReq(path: string) {
  return new Request(`http://console.local${path}`);
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/scm/demand-planning/policies/{skuCode} proxy', () => {
  it('attaches the domain-facing IAM OIDC token (NOT the operator token); no X-Tenant-Id; no mutation artifacts', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(POLICY_ENV)),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await policyGET(
      getReq('/api/scm/demand-planning/policies/SKU-1'),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.found).toBe(true);
    expect(body.value.reorderPoint).toBe(10);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    expect(String(url)).toContain('/api/v1/demand-planning/policies/SKU-1');
  });

  it('GET 404 POLICY_NOT_FOUND → 200 { found: false } (not configured yet → create), NOT a 404', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('POLICY_NOT_FOUND', 404)),
      ),
    );
    const res = await policyGET(
      getReq('/api/scm/demand-planning/policies/NOPE'),
      { params: Promise.resolve({ skuCode: 'NOPE' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.found).toBe(false);
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await policyGET(
      getReq('/api/scm/demand-planning/policies/SKU-1'),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('SERVICE_UNAVAILABLE', 503)),
      ),
    );
    const res = await policyGET(
      getReq('/api/scm/demand-planning/policies/SKU-1'),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(503);
  });

  it('persisting 429 → 429 + Retry-After (bounded; client must not re-storm)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(
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
      ),
    );
    const res = await policyGET(
      getReq('/api/scm/demand-planning/policies/SKU-1'),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(429);
    expect(res.headers.get('Retry-After')).toBe('1');
  });
});

describe('PUT /api/scm/demand-planning/policies/{skuCode} proxy', () => {
  it('forwards the FULL-row body + NO Idempotency-Key + NO X-Operator-Reason', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(POLICY_ENV)),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await policyPUT(
      putReq('/api/scm/demand-planning/policies/SKU-1', POLICY_INPUT),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('PUT');
    expect(String(url)).toContain('/policies/SKU-1');
    expect(String(url)).not.toMatch(/\/suggestions|\/approve|\/submit/);
    expect(JSON.parse((init as RequestInit).body as string)).toEqual(
      POLICY_INPUT,
    );
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('PUT with a malformed body (negative qty) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await policyPUT(
      putReq('/api/scm/demand-planning/policies/SKU-1', {
        reorderPoint: -1,
        safetyStock: 5,
        reorderQty: 100,
      }),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('producer 422 VALIDATION_ERROR → 422 passthrough (inline; no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('VALIDATION_ERROR', 422)),
      ),
    );
    const res = await policyPUT(
      putReq('/api/scm/demand-planning/policies/SKU-1', POLICY_INPUT),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(422);
    const b = await res.json();
    expect(b.code).toBe('VALIDATION_ERROR');
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await policyPUT(
      putReq('/api/scm/demand-planning/policies/SKU-1', POLICY_INPUT),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('GET+PUT /api/scm/demand-planning/sku-supplier-map/{skuCode} proxy', () => {
  it('GET 404 MAPPING_NOT_FOUND → 200 { found: false }', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('MAPPING_NOT_FOUND', 404)),
      ),
    );
    const res = await mapGET(
      getReq('/api/scm/demand-planning/sku-supplier-map/NOPE'),
      { params: Promise.resolve({ skuCode: 'NOPE' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.found).toBe(false);
  });

  it('PUT forwards the FULL-row body (supplierId free-text) + NO invented headers', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(MAP_ENV)),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await mapPUT(
      putReq('/api/scm/demand-planning/sku-supplier-map/SKU-1', MAP_INPUT),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('PUT');
    expect(String(url)).toContain('/sku-supplier-map/SKU-1');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual(MAP_INPUT);
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('PUT with an empty supplierId → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await mapPUT(
      putReq('/api/scm/demand-planning/sku-supplier-map/SKU-1', {
        ...MAP_INPUT,
        supplierId: '',
      }),
      { params: Promise.resolve({ skuCode: 'SKU-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
