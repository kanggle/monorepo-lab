import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * The two dashboard routes compose in the console-web server
 * (TASK-PC-FE-302 / ADR-MONO-081 — contract § 2.4.9). Replaces the former
 * BFF's proxy tests (`operator-overview-proxy-header.test.ts`,
 * `domain-health-proxy.test.ts`): there is no BFF hop left to assert headers
 * on. The leg rules themselves live in `shared/console-composition.test.ts`;
 * this file pins what only the ROUTE decides — the inbound checks that run
 * before any leg, and the HTTP mapping of a composition result.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({
    IAM_ADMIN_API_BASE: 'http://iam.local',
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    FINANCE_BASE_URL: 'http://finance.local',
    LEDGER_BASE_URL: 'http://finance.local',
    ERP_BASE_URL: 'http://erp.local',
    ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.local/api/admin',
  }),
}));

vi.mock('@/shared/config/demo-backend', () => ({
  resolveBackendUrl: async (u: string) => u,
}));

// TASK-PC-FE-364 — the overview no longer reads the finance default account.
// Still mocked so that a reintroduced read is observable (asserted below).
vi.mock('@/shared/lib/finance-default-account-id', () => ({
  getFinanceDefaultAccountId: vi.fn(),
}));

import { GET as overviewGET } from '@/app/api/console/dashboards/operator-overview/route';
import { GET as healthGET } from '@/app/api/console/dashboards/domain-health/route';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
} from '@/shared/lib/session';
import { getFinanceDefaultAccountId } from '@/shared/lib/finance-default-account-id';
import { OperatorOverviewSchema } from '@/features/operator-overview/api/operator-overview-types';
import { DomainHealthSchema } from '@/features/domain-health/api/types';

const fixture = JSON.parse(
  readFileSync(
    path.resolve(__dirname, '../../../../specs/contracts/fixtures/operator-overview-leg-bodies.json'),
    'utf8',
  ),
) as {
  legs: Record<string, { body: unknown }>;
  headlineMetrics: Record<string, { body: unknown }>;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

// TASK-PC-FE-368 — the six headline-metric legs are `<domain>:metrics`; they are matched FIRST
// (`/po/metrics` also contains `/procurement/po`).
function legOf(url: string): string {
  if (url.includes('/actuator/health')) return 'health';
  if (url.includes('/metrics')) {
    if (url.includes('/api/admin/metrics')) return 'iam:metrics';
    if (url.includes('/dashboard/metrics')) return 'wms:metrics';
    if (url.includes('/po/metrics')) return 'scm:metrics';
    if (url.includes('/api/finance/accounts/metrics')) return 'finance:metrics';
    if (url.includes('/approval/metrics')) return 'erp:metrics';
    if (url.includes('/api/admin/orders/metrics')) return 'ecommerce:metrics';
  }
  if (url.includes('/api/admin/accounts')) return 'iam';
  if (url.includes('/dashboard/orders')) return 'wms';
  if (url.includes('/procurement/po')) return 'scm';
  if (url.includes('/reconciliation/discrepancies')) return 'finance';
  if (url.includes('/approval/inbox')) return 'erp';
  if (url.includes('/api/admin/orders')) return 'ecommerce';
  throw new Error(`unexpected url ${url}`);
}

let fetchMock: ReturnType<typeof vi.fn>;

function bodyFor(leg: string): unknown {
  return leg.endsWith(':metrics')
    ? fixture.headlineMetrics[leg.split(':')[0]].body
    : fixture.legs[leg].body;
}

/** The card legs only (the headline-metric legs are asserted in operator-overview-headline-composition.test.ts). */
const cardCalls = () => fetchMock.mock.calls.map(([u]) => legOf(String(u))).filter((l) => !l.endsWith(':metrics'));

function fullSession(tenant = 'acme') {
  cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
  cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
  cookieJar.set(TENANT_COOKIE, tenant);
  // TASK-PC-FE-338 (OD-2): a selected tenant always carries its assumed token (the switch sets both);
  // without it the domain-facing resolver is fail-closed (null → 401), no base-token fallback.
  cookieJar.set(ASSUMED_TOKEN_COOKIE, 'GAP-ASSUMED');
}

beforeEach(() => {
  cookieJar.clear();
  vi.mocked(getFinanceDefaultAccountId).mockReset();
  vi.mocked(getFinanceDefaultAccountId).mockResolvedValue('acc-7');
  fetchMock = vi.fn(async (url: string) => {
    const leg = legOf(String(url));
    return leg === 'health' ? json({ status: 'UP' }) : json(bodyFor(leg));
  });
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('operator overview route', () => {
  it('200 — the composed envelope parses with the production card schema; six card legs + six headline-metric legs called', async () => {
    fullSession();
    const res = await overviewGET();
    expect(res.status).toBe(200);
    const body = OperatorOverviewSchema.parse(await res.json());
    expect(body.cards.map((c) => c.domain)).toEqual(['iam', 'wms', 'scm', 'finance', 'erp', 'ecommerce']);
    expect(cardCalls()).toHaveLength(6);
    expect(fetchMock).toHaveBeenCalledTimes(12);
  });

  it('🔴 no active tenant → 400 NO_ACTIVE_TENANT and ZERO leg calls (AC-4)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    const res = await overviewGET();
    expect(res.status).toBe(400);
    expect(((await res.json()) as { code: string }).code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).toHaveBeenCalledTimes(0);
  });

  it('half session (no operator token) → 401 TOKEN_INVALID and ZERO leg calls', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'acme');
    const res = await overviewGET();
    expect(res.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(0);
  });

  it('🔴 one data leg 401 → route 401 TOKEN_INVALID (not a 200 with a degraded card)', async () => {
    fullSession();
    fetchMock.mockImplementation(async (url: string) => {
      const leg = legOf(String(url));
      return leg === 'wms' ? json({ code: 'TOKEN_INVALID' }, 401) : json(bodyFor(leg));
    });
    const res = await overviewGET();
    expect(res.status).toBe(401);
    expect(((await res.json()) as { code: string }).code).toBe('TOKEN_INVALID');
  });

  it('one leg down → still 200, that card degraded', async () => {
    fullSession();
    fetchMock.mockImplementation(async (url: string) => {
      const leg = legOf(String(url));
      if (leg === 'erp') throw new TypeError('fetch failed');
      return json(bodyFor(leg));
    });
    const res = await overviewGET();
    expect(res.status).toBe(200);
    const body = OperatorOverviewSchema.parse(await res.json());
    expect(body.cards.find((c) => c.domain === 'erp')).toMatchObject({
      status: 'degraded',
      reason: 'DOWNSTREAM_ERROR',
    });
  });

  it('TASK-PC-FE-364 — no finance default account → the finance card is still called and ok; six card calls', async () => {
    fullSession();
    vi.mocked(getFinanceDefaultAccountId).mockResolvedValue(null);
    const res = await overviewGET();
    const body = OperatorOverviewSchema.parse(await res.json());
    expect(body.cards.find((c) => c.domain === 'finance')).toMatchObject({ status: 'ok' });
    expect(body.cards.some((c) => c.status === 'forbidden')).toBe(false);
    expect(cardCalls()).toHaveLength(6);
    // The overview does not read the default account at all any more.
    expect(getFinanceDefaultAccountId).not.toHaveBeenCalled();
  });

  it('TASK-PC-FE-364 — one call per card through the route (each leg exactly once)', async () => {
    fullSession();
    await overviewGET();
    const legs = cardCalls();
    expect(legs.sort()).toEqual(['ecommerce', 'erp', 'finance', 'iam', 'scm', 'wms']);
  });

  it('a producer 403 on one swapped leg → that card forbidden, route 200', async () => {
    fullSession();
    fetchMock.mockImplementation(async (url: string) => {
      const leg = legOf(String(url));
      return leg === 'erp'
        ? json({ code: 'PERMISSION_DENIED', message: 'required role not present' }, 403)
        : json(bodyFor(leg));
    });
    const res = await overviewGET();
    expect(res.status).toBe(200);
    const body = OperatorOverviewSchema.parse(await res.json());
    expect(body.cards.find((c) => c.domain === 'erp')).toMatchObject({
      status: 'forbidden',
      reason: 'PERMISSION_DENIED',
    });
  });
});

describe('domain health route', () => {
  it('200 — six public health legs, no credential sent, envelope parses', async () => {
    fullSession();
    const res = await healthGET();
    expect(res.status).toBe(200);
    DomainHealthSchema.parse(await res.json());
    expect(fetchMock).toHaveBeenCalledTimes(6);
    for (const [, init] of fetchMock.mock.calls) {
      const h = (init as RequestInit).headers as Record<string, string>;
      expect('Authorization' in h).toBe(false);
      expect('X-Tenant-Id' in h).toBe(false);
      expect('X-Operator-Token' in h).toBe(false);
    }
  });

  it('no active tenant → 400 and ZERO leg calls', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    const res = await healthGET();
    expect(res.status).toBe(400);
    expect(fetchMock).toHaveBeenCalledTimes(0);
  });

  it('no IAM session (half session) → 401 and ZERO leg calls', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'acme');
    const res = await healthGET();
    expect(res.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(0);
  });

  it('a health leg 401 degrades that card — the route stays 200', async () => {
    fullSession();
    fetchMock.mockImplementation(async (url: string) =>
      String(url).startsWith('http://iam.local') ? json({}, 401) : json({ status: 'UP' }),
    );
    const res = await healthGET();
    expect(res.status).toBe(200);
    const body = DomainHealthSchema.parse(await res.json());
    expect(body.cards.find((c) => c.domain === 'iam')).toMatchObject({ status: 'degraded' });
  });
});
