import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * TASK-PC-FE-350 (AC-2, owner decision (나)) — the two dashboard SSR entry points
 * compose IN-PROCESS. They do not fetch this app's own composition routes.
 *
 * Why this is a test and not a comment: the self-fetch was an inherited hop
 * (TASK-PC-FE-011 proxied to the former BFF; TASK-PC-FE-302 moved the
 * composition into the route but left the page calling the route over HTTP).
 * That hop added a `localhost` / public-hostname lookup and undici's 10 s connect
 * timeout. TASK-PC-FE-350 § AC-0 measured that lookup queueing behind
 * un-cancellable `getaddrinfo` calls in a saturated libuv threadpool and timing
 * out as `UND_ERR_CONNECT_TIMEOUT` — the whole overview degraded. Nothing in the
 * route needs HTTP; a reintroduced self-fetch would bring the stall back and
 * stay green everywhere else. This file pins the absence.
 *
 * 🔴 The fetch spy separates two populations: LEG fetches (to the domain bases —
 *    allowed, that is what composing means) and SELF fetches (this app's origin,
 *    or a relative `/api/...` path — forbidden on the SSR path). Without the split
 *    «zero fetch» would be unsatisfiable and «some fetch» would prove nothing.
 *
 * The behaviour half asserts every SSR outcome the page branches on — `noTenant`,
 * `unauthorized`, `bffUnavailable` + `unavailableCause`, per-card degrade inside
 * the payload — through the REAL session reads (cookie jar via `next/headers`),
 * the real `sampleGate`, the real composer. Only the network (and the finance
 * default-account read, which the overview no longer calls since
 * TASK-PC-FE-364) are stubbed (same seams as `dashboard-composition-routes.test.ts`).
 */

const SELF = 'http://console.self.test';

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
    // The pre-350 SSR path serialised the jar into a Cookie header; kept so that
    // path fails on its assertions, not on a missing method.
    toString: () => [...cookieJar].map(([k, v]) => `${k}=${v}`).join('; '),
  }),
}));

const { envState } = vi.hoisted(() => ({ envState: { throwWith: null as Error | null } }));
vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => {
    if (envState.throwWith) throw envState.throwWith;
    return {
      IAM_ADMIN_API_BASE: 'http://iam.local',
      WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
      SCM_GATEWAY_BASE_URL: 'http://scm.local',
      FINANCE_BASE_URL: 'http://finance.local',
      LEDGER_BASE_URL: 'http://finance.local',
      ERP_BASE_URL: 'http://erp.local',
      ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.local/api/admin',
    };
  },
}));

vi.mock('@/shared/config/demo-backend', () => ({
  resolveBackendUrl: async (u: string) => u,
}));

vi.mock('@/shared/lib/finance-default-account-id', () => ({
  getFinanceDefaultAccountId: vi.fn(),
}));

const { sampleOverride } = vi.hoisted(() => ({
  sampleOverride: { res: null as (() => Response) | null },
}));
vi.mock('@/shared/sample/router', async (importOriginal) => {
  const real = await importOriginal<typeof import('@/shared/sample/router')>();
  return {
    ...real,
    sampleResponse: (req: Parameters<typeof real.sampleResponse>[0]) =>
      sampleOverride.res ? sampleOverride.res() : real.sampleResponse(req),
  };
});

import { getOperatorOverviewState } from '@/features/operator-overview';
import { getDomainHealthState } from '@/features/domain-health';
import { GET as overviewGET } from '@/app/api/console/dashboards/operator-overview/route';
import { GET as healthGET } from '@/app/api/console/dashboards/domain-health/route';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
} from '@/shared/lib/session';
import { getFinanceDefaultAccountId } from '@/shared/lib/finance-default-account-id';

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

const LEG_BASES = [
  'http://iam.local',
  'http://wms.local',
  'http://scm.local',
  'http://finance.local',
  'http://erp.local',
  'http://ecommerce.local',
];

function isSelf(url: string): boolean {
  return url.startsWith(SELF) || url.startsWith('/');
}

// TASK-PC-FE-368 — the six headline-metric legs are `<domain>:metrics`, matched FIRST.
function legOf(url: string): string {
  if (url.includes('/actuator/health')) return 'health';
  if (url.includes('/api/admin/metrics')) return 'iam:metrics';
  if (url.includes('/dashboard/metrics')) return 'wms:metrics';
  if (url.includes('/po/metrics')) return 'scm:metrics';
  if (url.includes('/api/finance/accounts/metrics')) return 'finance:metrics';
  if (url.includes('/approval/metrics')) return 'erp:metrics';
  if (url.includes('/api/admin/orders/metrics')) return 'ecommerce:metrics';
  if (url.includes('/api/admin/accounts')) return 'iam';
  if (url.includes('/dashboard/orders')) return 'wms';
  if (url.includes('/procurement/po')) return 'scm';
  if (url.includes('/reconciliation/discrepancies')) return 'finance';
  if (url.includes('/approval/inbox')) return 'erp';
  if (url.includes('/api/admin/orders')) return 'ecommerce';
  return 'unknown';
}

function bodyFor(leg: string): unknown {
  return leg.endsWith(':metrics')
    ? fixture.headlineMetrics[leg.split(':')[0]].body
    : fixture.legs[leg].body;
}

let fetchMock: ReturnType<typeof vi.fn>;
let legImpl: (url: string) => Promise<Response>;

const fetchedUrls = (): string[] => fetchMock.mock.calls.map(([u]) => String(u));
const selfFetches = (): string[] => fetchedUrls().filter(isSelf);
const legFetches = (): string[] =>
  fetchedUrls().filter((u) => LEG_BASES.some((b) => u.startsWith(b)));

function fullSession(tenant = 'acme') {
  cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
  cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
  cookieJar.set(TENANT_COOKIE, tenant);
  cookieJar.set(ASSUMED_TOKEN_COOKIE, 'GAP-ASSUMED');
}

beforeEach(() => {
  cookieJar.clear();
  envState.throwWith = null;
  sampleOverride.res = null;
  vi.stubEnv('CONSOLE_PUBLIC_ORIGIN', SELF);
  vi.mocked(getFinanceDefaultAccountId).mockReset();
  vi.mocked(getFinanceDefaultAccountId).mockResolvedValue('acc-7');
  legImpl = async (url) => {
    const leg = legOf(url);
    if (leg === 'health') return json({ status: 'UP' });
    if (leg === 'unknown') throw new Error(`unexpected url ${url}`);
    return json(bodyFor(leg));
  };
  fetchMock = vi.fn(async (input: unknown) => {
    const url = String(input);
    // A self-fetch answers like an unreachable origin would — it must not be a
    // silent success, and it must not crash the test: the pre-350 path then
    // fails on its ASSERTIONS (state shape, call counts), not on a throw here.
    if (isSelf(url)) return json({ code: 'SELF_FETCH', message: 'self' }, 599);
    return legImpl(url);
  });
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe('🔴 TASK-PC-FE-350 — SSR makes ZERO fetch calls to this app’s own origin', () => {
  it('getOperatorOverviewState — legs fetched directly, no self-fetch', async () => {
    fullSession();
    const state = await getOperatorOverviewState();
    expect(selfFetches()).toEqual([]);
    // 🔵 non-vacuity — the composition really ran here, in-process (six card legs incl. finance + six headline-metric legs).
    expect(legFetches()).toHaveLength(12);
    expect(state.bffUnavailable).toBe(false);
    expect(state.overview?.cards.map((c) => c.domain)).toEqual([
      'iam',
      'wms',
      'scm',
      'finance',
      'erp',
      'ecommerce',
    ]);
  });

  it('getDomainHealthState — six health legs fetched directly, no self-fetch', async () => {
    fullSession();
    const state = await getDomainHealthState();
    expect(selfFetches()).toEqual([]);
    expect(legFetches()).toHaveLength(6);
    expect(state.bffUnavailable).toBe(false);
    expect(state.health?.cards).toHaveLength(6);
  });

  it('🔵 the SSR state carries the SAME cards the browser route answers (one composition, two callers)', async () => {
    fullSession();
    const ssr = await getOperatorOverviewState();
    const route = (await (await overviewGET()).json()) as { cards: unknown[] };
    expect(ssr.overview?.cards).toEqual(route.cards);

    const ssrHealth = await getDomainHealthState();
    const routeHealth = (await (await healthGET()).json()) as { cards: unknown[] };
    expect(ssrHealth.health?.cards).toEqual(routeHealth.cards);
  });

  it('sample visitor (no session cookies) — sample envelope, ZERO fetch of any kind', async () => {
    const overview = await getOperatorOverviewState();
    const health = await getDomainHealthState();
    expect(fetchedUrls()).toEqual([]);
    expect(overview.bffUnavailable).toBe(false);
    expect(overview.overview?.cards).toHaveLength(6);
    expect(health.bffUnavailable).toBe(false);
    expect(health.health?.cards).toHaveLength(6);
  });
});

describe('getOperatorOverviewState — every SSR outcome via the direct path', () => {
  it('no active tenant → { noTenant: true }, zero fetch', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    const state = await getOperatorOverviewState();
    expect(state).toEqual({
      overview: null,
      noTenant: true,
      unauthorized: false,
      bffUnavailable: false,
    });
    expect(fetchedUrls()).toEqual([]);
  });

  it('half session (no operator token) → { unauthorized: true }, zero fetch', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'acme');
    const state = await getOperatorOverviewState();
    expect(state).toEqual({
      overview: null,
      noTenant: false,
      unauthorized: true,
      bffUnavailable: false,
    });
    expect(fetchedUrls()).toEqual([]);
  });

  it('one data leg 401 → { unauthorized: true } (cross-leg rule), no self-fetch', async () => {
    fullSession();
    legImpl = async (url) => {
      const leg = legOf(url);
      return leg === 'wms' ? json({ code: 'TOKEN_INVALID' }, 401) : json(bodyFor(leg));
    };
    const state = await getOperatorOverviewState();
    expect(state.unauthorized).toBe(true);
    expect(state.overview).toBeNull();
    expect(selfFetches()).toEqual([]);
  });

  it('one leg down → overview present, that card degraded (per-card degrade stays in the payload)', async () => {
    fullSession();
    legImpl = async (url) => {
      const leg = legOf(url);
      if (leg === 'erp') throw new TypeError('fetch failed');
      return json(bodyFor(leg));
    };
    const state = await getOperatorOverviewState();
    expect(state.bffUnavailable).toBe(false);
    expect(state.unavailableCause).toBeUndefined();
    expect(state.overview?.cards.find((c) => c.domain === 'erp')).toMatchObject({
      status: 'degraded',
      reason: 'DOWNSTREAM_ERROR',
    });
  });

  it('the composition throws in-process → { bffUnavailable } with cause kind `unknown` — 🔴 NOT `transport`', async () => {
    fullSession();
    // A TypeError is what `fetch` throws at the network layer, so the old
    // classifier called it `transport`. In-process there is no transport: a
    // TypeError here is a code fault, and reporting it as «could not reach» would
    // send a reader to the network.
    // TASK-PC-FE-364: the overview no longer reads the finance default account,
    // so the in-process throw is injected at the leg builder's env read instead.
    envState.throwWith = new TypeError('boom');
    const state = await getOperatorOverviewState();
    expect(state.bffUnavailable).toBe(true);
    expect(state.overview).toBeNull();
    expect(state.unavailableCause).toEqual({ kind: 'unknown', name: 'TypeError' });
    expect(selfFetches()).toEqual([]);
  });

  it('the sample surface answers non-200 → { bffUnavailable } with cause status:502/BAD_GATEWAY (the route’s 502)', async () => {
    sampleOverride.res = () => json({ code: 'SAMPLE_NOT_READY' }, 503);
    const state = await getOperatorOverviewState();
    expect(state.bffUnavailable).toBe(true);
    expect(state.unavailableCause).toEqual({ kind: 'status', status: 502, code: 'BAD_GATEWAY' });
    expect(fetchedUrls()).toEqual([]);
  });
});

describe('getDomainHealthState — every SSR outcome via the direct path', () => {
  it('no active tenant → { noTenant: true }, zero fetch', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    const state = await getDomainHealthState();
    expect(state).toEqual({
      health: null,
      noTenant: true,
      unauthorized: false,
      bffUnavailable: false,
    });
    expect(fetchedUrls()).toEqual([]);
  });

  it('no IAM access token → { unauthorized: true }, zero fetch', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'acme');
    const state = await getDomainHealthState();
    expect(state).toEqual({
      health: null,
      noTenant: false,
      unauthorized: true,
      bffUnavailable: false,
    });
    expect(fetchedUrls()).toEqual([]);
  });

  it('a health leg 401 degrades that card — never `unauthorized`', async () => {
    fullSession();
    legImpl = async (url) => (url.startsWith('http://iam.local') ? json({}, 401) : json({ status: 'UP' }));
    const state = await getDomainHealthState();
    expect(state.unauthorized).toBe(false);
    expect(state.bffUnavailable).toBe(false);
    expect(state.health?.cards.find((c) => c.domain === 'iam')).toMatchObject({ status: 'degraded' });
  });

  it('the composition throws in-process → { bffUnavailable } with cause kind `unknown` — 🔴 NOT `transport`', async () => {
    fullSession();
    envState.throwWith = new TypeError('env broke');
    const state = await getDomainHealthState();
    expect(state.bffUnavailable).toBe(true);
    expect(state.unavailableCause).toEqual({ kind: 'unknown', name: 'TypeError' });
    expect(selfFetches()).toEqual([]);
  });

  it('the sample surface answers non-200 → { bffUnavailable } with cause status:502/BAD_GATEWAY', async () => {
    sampleOverride.res = () => json({ code: 'SAMPLE_NOT_READY' }, 503);
    const state = await getDomainHealthState();
    expect(state.bffUnavailable).toBe(true);
    expect(state.unavailableCause).toEqual({ kind: 'status', status: 502, code: 'BAD_GATEWAY' });
    expect(fetchedUrls()).toEqual([]);
  });
});
