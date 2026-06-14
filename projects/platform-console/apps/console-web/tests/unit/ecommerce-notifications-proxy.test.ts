import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops notifications proxy route handlers
 * (TASK-PC-FE-089 — § 2.4.10.4):
 *   - GET list: domain-facing IAM OIDC token attached server-side
 *     (NOT the operator token); NO X-Tenant-Id.
 *   - POST create: same auth model + Zod body parse (type+channel+subject+body).
 *   - GET detail: same auth model.
 *   - PUT update: same auth model + Zod body parse (subject+body only — NO type/channel).
 *   - bad body (Zod fail) → 422 (no upstream call).
 *   - 401 → 401 when the IAM session is absent (no upstream call).
 *   - 503 → 503 (section degrades only).
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
  POST as createPOST,
} from '@/app/api/ecommerce/notifications/templates/route';
import {
  GET as detailGET,
  PUT as updatePUT,
} from '@/app/api/ecommerce/notifications/templates/[id]/route';
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

const TEMPLATE_LIST = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
};

const TEMPLATE_DETAIL = {
  templateId: 'tmpl-1',
  type: 'ORDER_PLACED',
  channel: 'EMAIL',
  subject: '주문이 완료되었습니다',
  body: '안녕하세요 {{name}}님.',
  createdAt: '2026-06-14T00:00:00Z',
  updatedAt: '2026-06-14T00:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ecommerce/notifications/templates (list proxy)', () => {
  it('attaches the domain-facing token (NOT the operator token), NO X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TEMPLATE_LIST));
    vi.stubGlobal('fetch', fetchMock);

    const res = await listGET(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates?page=0&size=20',
      ),
    );
    expect(res.status).toBe(200);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await listGET(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates',
      ),
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
    const res = await listGET(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates',
      ),
    );
    expect(res.status).toBe(503);
  });
});

describe('POST /api/ecommerce/notifications/templates (create proxy)', () => {
  it('attaches the domain-facing token, NO X-Tenant-Id, NO Idempotency-Key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ templateId: 'tmpl-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    const res = await createPOST(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates',
        {
          method: 'POST',
          body: JSON.stringify({
            type: 'ORDER_PLACED',
            channel: 'EMAIL',
            subject: '주문이 완료되었습니다',
            body: '본문',
          }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );
    expect(res.status).toBe(201);
    const data = await res.json();
    expect(data.templateId).toBe('tmpl-9');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('invalid body (missing type) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await createPOST(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates',
        {
          method: 'POST',
          body: JSON.stringify({ channel: 'EMAIL', subject: '제목', body: '본문' }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await createPOST(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates',
        {
          method: 'POST',
          body: JSON.stringify({
            type: 'ORDER_PLACED',
            channel: 'EMAIL',
            subject: '제목',
            body: '본문',
          }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('409 TEMPLATE_ALREADY_EXISTS → 409 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('TEMPLATE_ALREADY_EXISTS', 409)),
    );
    const res = await createPOST(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates',
        {
          method: 'POST',
          body: JSON.stringify({
            type: 'ORDER_PLACED',
            channel: 'EMAIL',
            subject: '제목',
            body: '본문',
          }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.code).toBe('TEMPLATE_ALREADY_EXISTS');
  });
});

describe('GET /api/ecommerce/notifications/templates/{id} (detail proxy)', () => {
  it('attaches the domain-facing token and returns 200 with full detail (incl. body)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TEMPLATE_DETAIL));
    vi.stubGlobal('fetch', fetchMock);

    const res = await detailGET(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates/tmpl-1',
      ),
      { params: Promise.resolve({ id: 'tmpl-1' }) },
    );
    expect(res.status).toBe(200);
    const data = await res.json();
    expect(data.templateId).toBe('tmpl-1');
    expect(data.body).toBeTruthy();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/notifications/templates/tmpl-1',
    );
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('404 TEMPLATE_NOT_FOUND → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('TEMPLATE_NOT_FOUND', 404)),
    );
    const res = await detailGET(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates/nope',
      ),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const body = await res.json();
    expect(body.code).toBe('TEMPLATE_NOT_FOUND');
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await detailGET(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates/tmpl-1',
      ),
      { params: Promise.resolve({ id: 'tmpl-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('PUT /api/ecommerce/notifications/templates/{id} (update proxy)', () => {
  it('attaches the domain-facing token, NO X-Tenant-Id, NO Idempotency-Key; body has ONLY subject+body', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ templateId: 'tmpl-1' }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await updatePUT(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates/tmpl-1',
        {
          method: 'PUT',
          body: JSON.stringify({
            subject: '업데이트된 제목',
            body: '업데이트된 본문',
          }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
      { params: Promise.resolve({ id: 'tmpl-1' }) },
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/notifications/templates/tmpl-1',
    );
    expect((init as RequestInit).method).toBe('PUT');

    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();

    // CRITICAL: the forwarded body should contain ONLY subject+body
    const sentBody = JSON.parse((init as RequestInit).body as string);
    expect(sentBody.subject).toBe('업데이트된 제목');
    expect(sentBody.body).toBe('업데이트된 본문');
  });

  it('invalid update body (missing subject) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await updatePUT(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates/tmpl-1',
        {
          method: 'PUT',
          body: JSON.stringify({ body: '본문만 있음' }), // missing subject
          headers: { 'Content-Type': 'application/json' },
        },
      ),
      { params: Promise.resolve({ id: 'tmpl-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await updatePUT(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates/tmpl-1',
        {
          method: 'PUT',
          body: JSON.stringify({ subject: '제목', body: '본문' }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
      { params: Promise.resolve({ id: 'tmpl-1' }) },
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
    const res = await updatePUT(
      new Request(
        'http://console.local/api/ecommerce/notifications/templates/tmpl-1',
        {
          method: 'PUT',
          body: JSON.stringify({ subject: '제목', body: '본문' }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
      { params: Promise.resolve({ id: 'tmpl-1' }) },
    );
    expect(res.status).toBe(503);
  });
});
