import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Server-side tenant-management client core
 * (`features/tenants/api/tenants-client.ts`). Authoritative producer
 * contract: admin-api.md § "Tenant Lifecycle (TASK-BE-256)". `fetch` +
 * `getServerEnv()` + session + logger are mocked (mirrors
 * `subscriptions-client.test.ts` — the pattern-of-record for a small
 * `callAdminGateway`-profile client).
 */

const { ENV } = vi.hoisted(() => ({
  ENV: {
    IAM_ADMIN_API_BASE: 'http://iam.local',
    TENANTS_TIMEOUT_MS: 50,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

const session = vi.hoisted(() => ({
  operatorToken: 'op.jwt' as string | null,
  activeTenant: '*' as string | null,
}));
vi.mock('@/shared/lib/session', () => ({
  getOperatorToken: async () => session.operatorToken,
  getActiveTenant: async () => session.activeTenant,
}));

const logged: string[] = [];
vi.mock('@/shared/lib/logger', () => ({
  logger: {
    debug: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    info: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    warn: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    error: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
  },
  newRequestId: () => 'req-test',
}));

import { callGapTenants, TENANTS_PREFIX } from '@/features/tenants/api/tenants-client';
import { TenantsUnavailableError } from '@/shared/api/errors';

const OK_TENANT = {
  tenantId: 'acme-corp',
  displayName: 'ACME Corp',
  tenantType: 'B2B_ENTERPRISE',
  status: 'ACTIVE',
  createdAt: '2026-07-08T00:00:00Z',
  updatedAt: '2026-07-08T00:00:00Z',
};
function ok201() {
  return new Response(JSON.stringify(OK_TENANT), {
    status: 201,
    headers: { 'Content-Type': 'application/json' },
  });
}
const parse = (j: unknown) => j;

beforeEach(() => {
  logged.length = 0;
  session.operatorToken = 'op.jwt';
  session.activeTenant = '*';
  vi.unstubAllGlobals();
});

describe('callGapTenants — guards (no fetch)', () => {
  it('401 TOKEN_INVALID when no operator token', async () => {
    session.operatorToken = null;
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callGapTenants({ method: 'GET', path: TENANTS_PREFIX }, parse),
    ).rejects.toMatchObject({ name: 'ApiError', status: 401 });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 NO_ACTIVE_TENANT when no active tenant selected', async () => {
    session.activeTenant = null;
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callGapTenants({ method: 'GET', path: TENANTS_PREFIX }, parse),
    ).rejects.toMatchObject({ status: 400, code: 'NO_ACTIVE_TENANT' });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 REASON_REQUIRED on an empty reason for a mutation', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callGapTenants(
        { method: 'POST', path: TENANTS_PREFIX, reason: '   ', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({ status: 400, code: 'REASON_REQUIRED' });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('callGapTenants — success + headers', () => {
  it('sends operator token + X-Tenant-Id(*) + percent-encoded reason on create', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok201());
    vi.stubGlobal('fetch', fetchMock);
    const out = await callGapTenants(
      {
        method: 'POST',
        path: TENANTS_PREFIX,
        reason: '신규 파트너 온보딩',
        idempotencyKey: 'idem-1',
        body: { tenantId: 'acme-corp', displayName: 'ACME Corp', tenantType: 'B2B_ENTERPRISE' },
      },
      parse,
    );
    expect(out).toEqual(OK_TENANT);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://iam.local/api/admin/tenants');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer op.jwt');
    expect(headers['X-Tenant-Id']).toBe('*');
    expect(headers['X-Operator-Reason']).toBe(encodeURIComponent('신규 파트너 온보딩'));
    expect(headers['X-Operator-Reason']).not.toContain('신규');
    expect(headers['Idempotency-Key']).toBe('idem-1');
  });

  it('GET (list/detail) sends no mutation headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    await callGapTenants({ method: 'GET', path: TENANTS_PREFIX }, parse);
    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
  });
});

describe('callGapTenants — error taxonomy', () => {
  it('403 TENANT_SCOPE_DENIED (not SUPER_ADMIN) → ApiError passthrough', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TENANT_SCOPE_DENIED' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callGapTenants({ method: 'GET', path: TENANTS_PREFIX }, parse),
    ).rejects.toMatchObject({ name: 'ApiError', status: 403, code: 'TENANT_SCOPE_DENIED' });
  });

  it('409 TENANT_ALREADY_EXISTS → ApiError(409)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TENANT_ALREADY_EXISTS' }), {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callGapTenants(
        { method: 'POST', path: TENANTS_PREFIX, reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({ status: 409, code: 'TENANT_ALREADY_EXISTS' });
  });

  it('400 TENANT_ID_RESERVED → ApiError(400)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TENANT_ID_RESERVED' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callGapTenants(
        { method: 'POST', path: TENANTS_PREFIX, reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({ status: 400, code: 'TENANT_ID_RESERVED' });
  });

  it('404 TENANT_NOT_FOUND on get/update → ApiError(404)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TENANT_NOT_FOUND' }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callGapTenants({ method: 'GET', path: `${TENANTS_PREFIX}/missing` }, parse),
    ).rejects.toMatchObject({ status: 404, code: 'TENANT_NOT_FOUND' });
  });

  it('401 from producer → ApiError(401) (re-login)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TOKEN_INVALID' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callGapTenants({ method: 'GET', path: TENANTS_PREFIX }, parse),
    ).rejects.toMatchObject({ name: 'ApiError', status: 401 });
  });

  it('503 → TenantsUnavailableError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'DOWNSTREAM_ERROR' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callGapTenants({ method: 'GET', path: TENANTS_PREFIX }, parse),
    ).rejects.toMatchObject({ name: 'TenantsUnavailableError', reason: 'downstream' });
  });

  it('AbortController timeout → TenantsUnavailableError(TIMEOUT), token not logged', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init?: RequestInit) => {
        return new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            reject(e);
          });
        });
      }),
    );
    const err = (await callGapTenants(
      { method: 'GET', path: TENANTS_PREFIX },
      parse,
    ).catch((e) => e)) as TenantsUnavailableError;
    expect(err).toBeInstanceOf(TenantsUnavailableError);
    expect(err.code).toBe('TIMEOUT');
    expect(logged.join('\n')).not.toContain('op.jwt');
  });
});

