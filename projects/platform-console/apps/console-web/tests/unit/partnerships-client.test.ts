import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Server-side partnership client core
 * (`features/partnerships/api/partnerships-client.ts`).
 *
 * Authoritative producer contract: admin-api.md § Partnership Management
 * (BE-476/477/478). `fetch` + `getServerEnv()` + session + logger are mocked.
 * Asserts the per-endpoint header matrix (token / X-Tenant-Id / percent-encoded
 * reason / Idempotency-Key on invite ONLY), that the raw Korean reason never
 * hits the wire, that the token is never logged, and the full error taxonomy.
 */

const { ENV } = vi.hoisted(() => ({
  ENV: {
    IAM_ADMIN_API_BASE: 'http://iam.local',
    PARTNERSHIPS_TIMEOUT_MS: 50,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

const session = vi.hoisted(() => ({
  operatorToken: 'op.jwt' as string | null,
  activeTenant: 'acme-corp' as string | null,
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

import { callPartnerships } from '@/features/partnerships/api/partnerships-client';
import { ApiError, PartnershipsUnavailableError } from '@/shared/api/errors';

const PARTNERSHIP_BODY = {
  partnershipId: 'p-1',
  hostTenantId: 'acme-corp',
  partnerTenantId: 'globex-corp',
  status: 'PENDING',
  delegatedScope: { domains: ['wms'], roles: ['WMS_OUTBOUND_OPERATOR'] },
  invitedAt: '2026-07-04T10:00:00Z',
};
function ok201() {
  return new Response(JSON.stringify(PARTNERSHIP_BODY), {
    status: 201,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ok200() {
  return new Response(JSON.stringify({ ...PARTNERSHIP_BODY, status: 'ACTIVE' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
const parse = (j: unknown) => j;

beforeEach(() => {
  logged.length = 0;
  session.operatorToken = 'op.jwt';
  session.activeTenant = 'acme-corp';
  vi.unstubAllGlobals();
});

describe('callPartnerships — guards (no fetch)', () => {
  it('401 TOKEN_INVALID when no operator token', async () => {
    session.operatorToken = null;
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callPartnerships(
        { method: 'POST', path: '/x', reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({ name: 'ApiError', status: 401 });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 NO_ACTIVE_TENANT when no active tenant', async () => {
    session.activeTenant = null;
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callPartnerships(
        { method: 'POST', path: '/x', reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      code: 'NO_ACTIVE_TENANT',
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 REASON_REQUIRED on an empty reason (reason-bearing mutation)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callPartnerships(
        { method: 'POST', path: '/x', reason: '   ', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({ status: 400, code: 'REASON_REQUIRED' });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('callPartnerships — header matrix', () => {
  it('invite: sends token + X-Tenant-Id + percent-encoded reason + Idempotency-Key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok201());
    vi.stubGlobal('fetch', fetchMock);
    const out = await callPartnerships(
      {
        method: 'POST',
        path: '/api/admin/partnerships',
        reason: '파트너 초대', // non-Latin-1 → must be encoded
        idempotencyKey: 'idem-1',
        body: { partnerTenantId: 'globex-corp', delegatedScope: {} },
      },
      parse,
    );
    expect(out).toEqual(PARTNERSHIP_BODY);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://iam.local/api/admin/partnerships');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer op.jwt');
    expect(headers['X-Tenant-Id']).toBe('acme-corp');
    expect(headers['X-Operator-Reason']).toBe(encodeURIComponent('파트너 초대'));
    // the raw Korean reason is never sent on the wire header
    expect(headers['X-Operator-Reason']).not.toContain('파트너');
    expect(headers['Idempotency-Key']).toBe('idem-1');
  });

  it('accept: reason ONLY — asserts Idempotency-Key ABSENT', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok200());
    vi.stubGlobal('fetch', fetchMock);
    await callPartnerships(
      {
        method: 'POST',
        path: '/api/admin/partnerships/p-1:accept',
        reason: 'accept it',
      },
      parse,
    );
    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(decodeURIComponent(headers['X-Operator-Reason'])).toBe('accept it');
    expect(headers['Idempotency-Key']).toBeUndefined();
  });

  it('list (GET): no reason header, no idempotency key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    await callPartnerships(
      { method: 'GET', path: '/api/admin/partnerships?page=0&size=20' },
      parse,
    );
    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    // the read still authenticates + scopes
    expect(headers.Authorization).toBe('Bearer op.jwt');
    expect(headers['X-Tenant-Id']).toBe('acme-corp');
  });

  it('participant remove (204): returns undefined via expectNoContent', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    const out = await callPartnerships(
      {
        method: 'DELETE',
        path: '/api/admin/partnerships/p-1/participants/op-1',
        reason: 'offboard',
        expectNoContent: true,
      },
      parse,
    );
    expect(out).toBeUndefined();
  });
});

describe('callPartnerships — error taxonomy', () => {
  it('403 PARTNERSHIP_SCOPE_DENIED → ApiError passthrough', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'PARTNERSHIP_SCOPE_DENIED' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callPartnerships(
        { method: 'POST', path: '/x', reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({
      name: 'ApiError',
      status: 403,
      code: 'PARTNERSHIP_SCOPE_DENIED',
    });
  });

  it('409 PARTNERSHIP_ALREADY_EXISTS → ApiError(409)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'PARTNERSHIP_ALREADY_EXISTS' }), {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callPartnerships(
        { method: 'POST', path: '/x', reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({ status: 409, code: 'PARTNERSHIP_ALREADY_EXISTS' });
  });

  it('422 PARTICIPANT_SCOPE_EXCEEDS_DELEGATION → ApiError(422)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: 'PARTICIPANT_SCOPE_EXCEEDS_DELEGATION' }),
          {
            status: 422,
            headers: { 'Content-Type': 'application/json' },
          },
        ),
      ),
    );
    await expect(
      callPartnerships(
        { method: 'POST', path: '/x', reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({
      status: 422,
      code: 'PARTICIPANT_SCOPE_EXCEEDS_DELEGATION',
    });
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
      callPartnerships(
        { method: 'POST', path: '/x', reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({ name: 'ApiError', status: 401 });
  });

  it('503 → PartnershipsUnavailableError', async () => {
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
      callPartnerships(
        { method: 'POST', path: '/x', reason: 'r', body: {} },
        parse,
      ),
    ).rejects.toMatchObject({
      name: 'PartnershipsUnavailableError',
      reason: 'downstream',
    });
  });

  it('AbortController timeout → PartnershipsUnavailableError(TIMEOUT), token not logged', async () => {
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
    const err = (await callPartnerships(
      { method: 'POST', path: '/x', reason: 'r', body: {} },
      parse,
    ).catch((e) => e)) as PartnershipsUnavailableError;
    expect(err).toBeInstanceOf(PartnershipsUnavailableError);
    expect(err.code).toBe('TIMEOUT');
    expect(logged.join('\n')).not.toContain('op.jwt');
  });
});
