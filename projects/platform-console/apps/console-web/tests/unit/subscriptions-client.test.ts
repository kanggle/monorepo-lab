import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Server-side subscription client core
 * (`features/subscriptions/api/subscriptions-client.ts`).
 *
 * Authoritative producer contract: admin-api.md § Subscription Management
 * (BE-343). `fetch` + `getServerEnv()` + session + logger are mocked.
 */

const { ENV } = vi.hoisted(() => ({
  ENV: {
    IAM_ADMIN_API_BASE: 'http://iam.local',
    SUBSCRIPTIONS_TIMEOUT_MS: 50,
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

import { callSubscriptions } from '@/features/subscriptions/api/subscriptions-client';
import { ApiError, SubscriptionsUnavailableError } from '@/shared/api/errors';

const OK_BODY = {
  tenantId: 'acme-corp',
  domainKey: 'wms',
  previousStatus: null,
  currentStatus: 'ACTIVE',
  occurredAt: '2026-07-04T10:00:00Z',
};
function ok201() {
  return new Response(JSON.stringify(OK_BODY), {
    status: 201,
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

describe('callSubscriptions — guards (no fetch)', () => {
  it('401 TOKEN_INVALID when no operator token', async () => {
    session.operatorToken = null;
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callSubscriptions({ method: 'POST', path: '/x', reason: 'r', body: {} }, parse),
    ).rejects.toMatchObject({ name: 'ApiError', status: 401 });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 NO_ACTIVE_TENANT when no active tenant', async () => {
    session.activeTenant = null;
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callSubscriptions({ method: 'POST', path: '/x', reason: 'r', body: {} }, parse),
    ).rejects.toMatchObject({ name: 'ApiError', status: 400, code: 'NO_ACTIVE_TENANT' });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 REASON_REQUIRED on an empty reason', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      callSubscriptions({ method: 'POST', path: '/x', reason: '   ', body: {} }, parse),
    ).rejects.toMatchObject({ status: 400, code: 'REASON_REQUIRED' });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('callSubscriptions — success + headers', () => {
  it('sends operator token + X-Tenant-Id + percent-encoded reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok201());
    vi.stubGlobal('fetch', fetchMock);
    const out = await callSubscriptions(
      {
        method: 'POST',
        path: '/api/admin/subscriptions',
        reason: '이커머스 활성화', // non-Latin-1 → must be encoded
        body: { tenantId: 'acme-corp', domainKey: 'wms' },
      },
      parse,
    );
    expect(out).toEqual(OK_BODY);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://iam.local/api/admin/subscriptions');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer op.jwt');
    expect(headers['X-Tenant-Id']).toBe('acme-corp');
    expect(headers['X-Operator-Reason']).toBe(encodeURIComponent('이커머스 활성화'));
    // the raw Korean reason is never sent on the wire header
    expect(headers['X-Operator-Reason']).not.toContain('이커머스');
  });
});

describe('callSubscriptions — error taxonomy', () => {
  it('403 PERMISSION_DENIED → ApiError passthrough', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'PERMISSION_DENIED' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callSubscriptions({ method: 'POST', path: '/x', reason: 'r', body: {} }, parse),
    ).rejects.toMatchObject({ name: 'ApiError', status: 403, code: 'PERMISSION_DENIED' });
  });

  it('409 SUBSCRIPTION_ALREADY_EXISTS → ApiError(409) (drives resume)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'SUBSCRIPTION_ALREADY_EXISTS' }), {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(
      callSubscriptions({ method: 'POST', path: '/x', reason: 'r', body: {} }, parse),
    ).rejects.toMatchObject({ status: 409, code: 'SUBSCRIPTION_ALREADY_EXISTS' });
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
      callSubscriptions({ method: 'POST', path: '/x', reason: 'r', body: {} }, parse),
    ).rejects.toMatchObject({ name: 'ApiError', status: 401 });
  });

  it('503 → SubscriptionsUnavailableError', async () => {
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
      callSubscriptions({ method: 'POST', path: '/x', reason: 'r', body: {} }, parse),
    ).rejects.toMatchObject({ name: 'SubscriptionsUnavailableError', reason: 'downstream' });
  });

  it('AbortController timeout → SubscriptionsUnavailableError(TIMEOUT), token not logged', async () => {
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
    const err = (await callSubscriptions(
      { method: 'POST', path: '/x', reason: 'r', body: {} },
      parse,
    ).catch((e) => e)) as SubscriptionsUnavailableError;
    expect(err).toBeInstanceOf(SubscriptionsUnavailableError);
    expect(err.code).toBe('TIMEOUT');
    expect(logged.join('\n')).not.toContain('op.jwt');
  });
});
