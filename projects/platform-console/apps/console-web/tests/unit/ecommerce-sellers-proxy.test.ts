import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops sellers proxy route handlers
 * (TASK-PC-FE-090 — ADR-MONO-031 § 2.4.10 7th area):
 *   - GET list / GET detail: domain-facing IAM OIDC token (NOT the operator
 *     token); NO X-Tenant-Id; targets ECOMMERCE_ADMIN_BASE_URL.
 *   - POST register: Zod-validated body; bad body → 422 (no upstream call);
 *     no IAM session → 401; 503 → 503; 409 CONFLICT → passthrough.
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
    ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.local/api/admin',
    ECOMMERCE_PUBLIC_BASE_URL: 'http://ecommerce.local/api',
    ECOMMERCE_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import {
  GET as listGET,
  POST as registerPOST,
} from '@/app/api/ecommerce/sellers/route';
import { GET as detailGET } from '@/app/api/ecommerce/sellers/[id]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ecomError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const VALID_BODY = {
  sellerId: 'acme-corp',
  displayName: 'Acme Corporation',
};

const SELLER_LIST = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
};

const SELLER_DETAIL = {
  sellerId: 'acme-corp',
  displayName: 'Acme Corporation',
  status: 'ACTIVE',
  createdAt: '2026-06-14T00:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ecommerce/sellers (list)', () => {
  it('passes through the seller list', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(SELLER_LIST)));
    const res = await listGET(
      new Request('http://console.local/api/ecommerce/sellers?page=0&size=20'),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.content).toEqual([]);
  });

  it('targets ECOMMERCE_ADMIN_BASE_URL/sellers (not public base)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SELLER_LIST));
    vi.stubGlobal('fetch', fetchMock);
    await listGET(
      new Request('http://console.local/api/ecommerce/sellers?page=0&size=20'),
    );
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/sellers');
    expect(String(url)).not.toMatch(/ecommerce\.local\/api\/sellers/);
  });

  it('no IAM session → 401', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await listGET(
      new Request('http://console.local/api/ecommerce/sellers'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/ecommerce/sellers (register)', () => {
  it('attaches the domain-facing token (NOT the operator token), NO X-Tenant-Id / Idempotency-Key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ sellerId: 'acme-corp' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    const res = await registerPOST(
      new Request('http://console.local/api/ecommerce/sellers', {
        method: 'POST',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(201);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('invalid body (missing displayName) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerPOST(
      new Request('http://console.local/api/ecommerce/sellers', {
        method: 'POST',
        body: JSON.stringify({ sellerId: 'acme-corp' }), // missing displayName
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('invalid body (sellerId empty string) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerPOST(
      new Request('http://console.local/api/ecommerce/sellers', {
        method: 'POST',
        body: JSON.stringify({ sellerId: '', displayName: 'Acme' }),
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerPOST(
      new Request('http://console.local/api/ecommerce/sellers', {
        method: 'POST',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await registerPOST(
      new Request('http://console.local/api/ecommerce/sellers', {
        method: 'POST',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(503);
  });

  it('409 CONFLICT (duplicate sellerId) → passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('CONFLICT', 409)),
    );
    const res = await registerPOST(
      new Request('http://console.local/api/ecommerce/sellers', {
        method: 'POST',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.code).toBe('CONFLICT');
  });
});

describe('GET /api/ecommerce/sellers/{id} (detail)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS'));

  it('passes through the seller detail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(SELLER_DETAIL)),
    );
    const res = await detailGET(
      new Request('http://console.local/api/ecommerce/sellers/acme-corp'),
      { params: Promise.resolve({ id: 'acme-corp' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.sellerId).toBe('acme-corp');
  });

  it('targets ECOMMERCE_ADMIN_BASE_URL/sellers/{id}', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(SELLER_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    await detailGET(
      new Request('http://console.local/api/ecommerce/sellers/acme-corp'),
      { params: Promise.resolve({ id: 'acme-corp' }) },
    );
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/sellers/acme-corp',
    );
  });

  it('404 SELLER_NOT_FOUND → 404 passthrough', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SELLER_NOT_FOUND', 404)),
    );
    const res = await detailGET(
      new Request('http://console.local/api/ecommerce/sellers/nope'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const body = await res.json();
    expect(body.code).toBe('SELLER_NOT_FOUND');
  });
});
