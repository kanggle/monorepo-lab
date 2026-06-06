import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin operators proxy route handlers (TASK-PC-FE-004):
 *   - list 401 → 401 (client api-client refresh→re-login; no partial state)
 *   - list 503 → 503 (operators section degrades only)
 *   - no active tenant → 400 NO_ACTIVE_TENANT (tenant gate; never empty)
 *   - create proxy → BOTH X-Operator-Reason + Idempotency-Key to GAP
 *   - roles proxy → X-Operator-Reason ONLY, asserts Idempotency-Key ABSENT
 *   - status proxy → X-Operator-Reason ONLY, asserts Idempotency-Key ABSENT
 *   - me/password proxy → self path, NO reason / NO key, 204 passthrough
 *   - 403 PERMISSION_DENIED → 403 (inline-permission UX)
 *   - a malformed body → 422 without calling GAP (reason never fabricated)
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
} from '@/app/api/operators/route';
import { POST as rolesPOST } from '@/app/api/operators/[operatorId]/roles/route';
import { POST as statusPOST } from '@/app/api/operators/[operatorId]/status/route';
import { POST as pwPOST } from '@/app/api/operators/me/password/route';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/operators proxy (list)', () => {
  it('401 from GAP → 401 (forced re-login, no partial authed state)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const res = await listGET(
      new Request('http://console.local/api/operators?page=0&size=20'),
    );
    expect(res.status).toBe(401);
  });

  it('503 from GAP → 503 (operators section degrades only)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );
    const res = await listGET(
      new Request('http://console.local/api/operators'),
    );
    expect(res.status).toBe(503);
  });

  it('no active tenant → 400 NO_ACTIVE_TENANT (tenant gate, never empty)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await listGET(
      new Request('http://console.local/api/operators'),
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/operators proxy (create) — reason + idempotency', () => {
  it('forwards BOTH X-Operator-Reason + Idempotency-Key to GAP', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          operatorId: 'op-9',
          email: 'new@x.com',
          displayName: 'New',
          status: 'ACTIVE',
          roles: [],
          createdAt: 'x',
          auditId: 'a',
          tenantId: 'wms',
        },
        201,
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await createPOST(
      new Request('http://console.local/api/operators', {
        method: 'POST',
        body: JSON.stringify({
          email: 'new@x.com',
          displayName: 'New',
          password: 'Sup3r!secret',
          roles: ['SUPPORT_LOCK'],
          tenantId: 'wms',
          reason: 'operator entered reason',
          idempotencyKey: 'idem-1',
        }),
      }),
    );
    expect(res.status).toBe(201);
    const h = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    // TASK-MONO-176: percent-encoded on the wire to GAP; round-trips.
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe(
      'operator entered reason',
    );
    expect(h['Idempotency-Key']).toBe('idem-1');
  });

  it('a malformed body → 422 without calling GAP (reason never fabricated)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await createPOST(
      new Request('http://console.local/api/operators', {
        method: 'POST',
        body: JSON.stringify({ email: 'x@x.com' }), // missing fields
      }),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('409 OPERATOR_EMAIL_CONFLICT from GAP → 409 (inline email-field)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ code: 'OPERATOR_EMAIL_CONFLICT' }, 409),
        ),
    );
    const res = await createPOST(
      new Request('http://console.local/api/operators', {
        method: 'POST',
        body: JSON.stringify({
          email: 'dup@x.com',
          displayName: 'Dup',
          password: 'Sup3r!secret',
          roles: [],
          tenantId: 'wms',
          reason: 'r',
          idempotencyKey: 'k',
        }),
      }),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('OPERATOR_EMAIL_CONFLICT');
  });
});

describe('POST /api/operators/[id]/roles proxy — reason ONLY (no key)', () => {
  it('forwards X-Operator-Reason and asserts Idempotency-Key ABSENT', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ operatorId: 'op-1', roles: [], auditId: 'a' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await rolesPOST(
      new Request('http://console.local/api/operators/op-1/roles', {
        method: 'POST',
        body: JSON.stringify({ roles: [], reason: 'remove all roles' }),
      }),
      { params: Promise.resolve({ operatorId: 'op-1' }) },
    );
    expect(res.status).toBe(200);
    const h = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('remove all roles');
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('a body with no reason → 422 without calling GAP', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await rolesPOST(
      new Request('http://console.local/api/operators/op-1/roles', {
        method: 'POST',
        body: JSON.stringify({ roles: ['SUPPORT_LOCK'] }), // no reason
      }),
      { params: Promise.resolve({ operatorId: 'op-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/operators/[id]/status proxy — reason ONLY (no key)', () => {
  it('forwards X-Operator-Reason and asserts Idempotency-Key ABSENT', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        operatorId: 'op-1',
        previousStatus: 'ACTIVE',
        currentStatus: 'SUSPENDED',
        auditId: 'a',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await statusPOST(
      new Request('http://console.local/api/operators/op-1/status', {
        method: 'POST',
        body: JSON.stringify({ status: 'SUSPENDED', reason: 'policy' }),
      }),
      { params: Promise.resolve({ operatorId: 'op-1' }) },
    );
    expect(res.status).toBe(200);
    const h = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBe('policy');
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('403 PERMISSION_DENIED from GAP → 403 (inline permission)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'PERMISSION_DENIED' }, 403)),
    );
    const res = await statusPOST(
      new Request('http://console.local/api/operators/op-1/status', {
        method: 'POST',
        body: JSON.stringify({ status: 'SUSPENDED', reason: 'r' }),
      }),
      { params: Promise.resolve({ operatorId: 'op-1' }) },
    );
    expect(res.status).toBe(403);
  });
});

describe('POST /api/operators/me/password proxy — self, no reason/key, 204', () => {
  it('forwards to the self path with NO reason / NO key and returns 204', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await pwPOST(
      new Request('http://console.local/api/operators/me/password', {
        method: 'POST',
        body: JSON.stringify({
          currentPassword: 'Old1!pass',
          newPassword: 'New2@word!',
        }),
      }),
    );
    expect(res.status).toBe(204);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/operators/me/password');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('400 CURRENT_PASSWORD_MISMATCH from GAP → 400 (inline field error)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ code: 'CURRENT_PASSWORD_MISMATCH' }, 400),
        ),
    );
    const res = await pwPOST(
      new Request('http://console.local/api/operators/me/password', {
        method: 'POST',
        body: JSON.stringify({
          currentPassword: 'wrong',
          newPassword: 'New2@word!',
        }),
      }),
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('CURRENT_PASSWORD_MISMATCH');
  });
});
