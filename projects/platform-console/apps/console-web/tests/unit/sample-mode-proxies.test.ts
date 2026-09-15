/**
 * The out-of-core backend call sites in sample mode (ADR-MONO-074 A2 —
 * TASK-PC-FE-282 AC-2): the four console-bff proxies and the tenant switch.
 *
 * ① sample visitor → `fetch` 0, and the sample Response runs through each
 *   route's EXISTING mapping (the dashboards/inbox pass a 200 through; the
 *   dashboard/inbox bodies parse with the production schemas).
 * ② authenticated → the route still reaches console-bff.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
    set: () => undefined,
    delete: () => undefined,
  }),
}));

vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
  }),
}));

import { NextRequest } from 'next/server';
import { GET as overviewGET } from '@/app/api/console/dashboards/operator-overview/route';
import { GET as healthGET } from '@/app/api/console/dashboards/domain-health/route';
import { GET as inboxGET } from '@/app/api/console/notifications/inbox/route';
import { POST as markReadPOST } from '@/app/api/console/notifications/[sourceDomain]/[id]/read/route';
import { POST as tenantPOST } from '@/app/api/tenant/route';
import { OperatorOverviewSchema } from '@/features/operator-overview/api/operator-overview-types';
import { DomainHealthSchema } from '@/features/domain-health/api/types';
import { NotificationInboxResponseSchema } from '@/features/notifications/api/notification-types';
import { SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { ACCESS_COOKIE, OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  cookieJar.clear();
  fetchSpy = vi.fn(async () => {
    throw new Error('network reached');
  });
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('① sample visitor — no network', () => {
  it('operator overview → 200 sample envelope (parses), fetch 0', async () => {
    const res = await overviewGET();
    expect(res.status).toBe(200);
    OperatorOverviewSchema.parse(await res.json());
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('domain health → 200 sample envelope (parses), fetch 0', async () => {
    const res = await healthGET();
    expect(res.status).toBe(200);
    DomainHealthSchema.parse(await res.json());
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('notification inbox → 200 sample inbox (parses), fetch 0', async () => {
    const res = await inboxGET(
      new NextRequest('http://localhost/api/console/notifications/inbox?page=0&size=20'),
    );
    expect(res.status).toBe(200);
    NotificationInboxResponseSchema.parse(await res.json());
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('mark-read (a write) → refused upstream, mapped by the UNCHANGED route mapping (502), fetch 0', async () => {
    const res = await markReadPOST(new Request('http://localhost/x', { method: 'POST' }), {
      params: Promise.resolve({ sourceDomain: 'erp', id: 'sample-notification-0001' }),
    });
    expect(res.status).toBe(502);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('tenant switch → 403 SAMPLE_READ_ONLY before the registry read and the token exchange, fetch 0', async () => {
    const res = await tenantPOST(
      new Request('http://localhost/api/tenant', {
        method: 'POST',
        body: JSON.stringify({ tenant: 'acme' }),
      }),
    );
    expect(res.status).toBe(403);
    expect(((await res.json()) as { code: string }).code).toBe(SAMPLE_READ_ONLY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('② authenticated operator — the real path is still taken', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'access');
    cookieJar.set(OPERATOR_COOKIE, 'operator');
    cookieJar.set(TENANT_COOKIE, 'acme');
  });

  it('operator overview reaches console-bff', async () => {
    const res = await overviewGET();
    // fetch throws in this test → the existing mapping turns it into 502.
    expect(res.status).toBe(502);
    expect(
      fetchSpy.mock.calls.some(([url]) =>
        String(url).endsWith('/api/console/dashboards/operator-overview'),
      ),
    ).toBe(true);
  });

  it('domain health reaches console-bff', async () => {
    await healthGET();
    expect(fetchSpy).toHaveBeenCalled();
  });

  it('notification inbox reaches console-bff', async () => {
    await inboxGET(new NextRequest('http://localhost/api/console/notifications/inbox'));
    expect(fetchSpy).toHaveBeenCalled();
  });
});
