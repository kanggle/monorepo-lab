import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-338 slice c-1 fix — `GET /api/tenant/reassume` (console contract § 2.7 «Assumed-token TTL
 * becomes 300 s»): re-mint the SELECTED tenant's assumed token from the current base token, no IAM
 * refresh. Found by the federation e2e on 2026-10-14: the first c-1 cut sent this state through the
 * refresh hop, whose rotation-race retry and section-less pages ended in «세션 만료».
 *
 * Every outcome returns to the page and leaves {@link needsReassume} false — the hop cannot loop.
 */

const cookieJar = new Map<string, { value: string; opts?: Record<string, unknown> }>();
const cookiesMock = {
  get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)!.value } : undefined),
  set: (n: string, value: string, opts: Record<string, unknown>) => {
    cookieJar.set(n, { value, opts });
  },
  delete: (n: string) => {
    cookieJar.delete(n);
  },
};
vi.mock('next/headers', () => ({ cookies: async () => cookiesMock }));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: 'http://console.local' },
  getServerEnv: () => ({ NEXT_PUBLIC_APP_URL: 'http://console.local', LOG_LEVEL: 'info' }),
  publicOrigin: () => 'http://console.local',
}));

const exchangeMock = vi.fn();
vi.mock('@/shared/lib/assume-tenant-exchange', () => ({
  exchangeForAssumedToken: (...args: unknown[]) => exchangeMock(...args),
}));

import { GET } from '@/app/api/tenant/reassume/route';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
} from '@/shared/lib/session';

function call(redirect = '/wms/inventory') {
  return GET(new Request(`http://console.local/api/tenant/reassume?redirect=${encodeURIComponent(redirect)}`));
}

function locationPath(res: Response): string {
  const loc = res.headers.get('location') ?? '';
  const u = new URL(loc);
  return u.pathname + u.search;
}

beforeEach(() => {
  cookieJar.clear();
  exchangeMock.mockReset();
  cookieJar.set(ACCESS_COOKIE, { value: 'base.token' });
  cookieJar.set(OPERATOR_COOKIE, { value: 'op.token' });
});

describe('GET /api/tenant/reassume', () => {
  it('🔴 tenant selected, assumed lapsed, base alive → re-assumed FROM THE BASE TOKEN, cookie lapses 60 s early, back to the page', async () => {
    cookieJar.set(TENANT_COOKIE, { value: 'demo-corp' });
    exchangeMock.mockResolvedValue({ accessToken: 'assumed.new', expiresIn: 300 });

    const res = await call();

    expect(exchangeMock).toHaveBeenCalledWith('base.token', 'demo-corp');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('assumed.new');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.opts?.maxAge).toBe(240);
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('demo-corp');
    expect(locationPath(res)).toBe('/wms/inventory');
  });

  it('🔴 re-assume refused → the tenant selection is dropped (the page shows «select a tenant»), never a loop', async () => {
    cookieJar.set(TENANT_COOKIE, { value: '*' });
    exchangeMock.mockRejectedValue(new Error('denied'));

    const res = await call('/dashboards/overview');

    expect(cookieJar.has(TENANT_COOKIE)).toBe(false);
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
    expect(locationPath(res)).toBe('/dashboards/overview');
  });

  it('no base token → the idle-refresh hop (it re-assumes too)', async () => {
    cookieJar.delete(ACCESS_COOKIE);
    cookieJar.set(TENANT_COOKIE, { value: 'demo-corp' });

    const res = await call();

    expect(exchangeMock).not.toHaveBeenCalled();
    expect(locationPath(res)).toBe('/api/auth/refresh?redirect=%2Fwms%2Finventory');
  });

  it('nothing to do (no tenant, or assumed still present) → straight back, no exchange', async () => {
    let res = await call();
    expect(locationPath(res)).toBe('/wms/inventory');

    cookieJar.set(TENANT_COOKIE, { value: 'demo-corp' });
    cookieJar.set(ASSUMED_TOKEN_COOKIE, { value: 'assumed.live' });
    res = await call();
    expect(locationPath(res)).toBe('/wms/inventory');
    expect(exchangeMock).not.toHaveBeenCalled();
  });

  it('a hostile redirect is sanitised (same consumer predicate as the refresh hop)', async () => {
    const res = await call('//evil.example/x');
    expect(locationPath(res)).toBe('/');
  });
});
